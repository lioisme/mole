package com.lioisme.modev.data.repo

import android.util.Log
import com.google.gson.Gson
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.db.CryptoRecordDao
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.data.server.ChannelStats
import com.lioisme.modev.data.server.ConfigServer
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 日志仓库:接收注入侧记录 → 批量入库(200ms/32 条窗口)→ 双层容量治理 → 供 UI 订阅。
 * 管道统计(丢弃/拒绝/写失败/裁剪)汇入 [statsFlow] 供设置页展示。
 */
class LogRepository(
    private val dao: CryptoRecordDao,
    server: ConfigServer,
    private val maxRecords: Int = MAX_RECORDS,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val writeMutex = Mutex()

    /** 待入库记录通道:记录 + 其所属会话的 ACK 回调。 */
    private val pending = Channel<Pair<CryptoRecord, (Long) -> Unit>>(PENDING_CAPACITY)

    /**
     * 背压信号量:与通道容量相等,满时阻塞服务端客户端线程(而非丢弃),
     * 经 socket 写压力反向传导至注入侧 WAL(注入侧先落盘再发送,天然安全)。
     */
    private val pendingSlots = java.util.concurrent.Semaphore(PENDING_CAPACITY)

    private val _stats = MutableStateFlow(PipelineStats())
    val statsFlow: StateFlow<PipelineStats> = _stats

    private fun bumpStats(transform: (PipelineStats) -> PipelineStats) {
        _stats.value = transform(_stats.value)
    }

    init {
        server.onRecord = { record, ack ->
            // 背压:通道满时阻塞客户端线程,绝不丢弃;槽位由 writerLoop 消费后释放
            runCatching { pendingSlots.acquire() }
            pending.trySend(record to ack)
        }
        // 通道侧拒绝/丢弃计数汇入管道统计
        scope.launch {
            server.statsFlow.collect { channel ->
                bumpStats { it.copy(channel = channel) }
            }
        }
        scope.launch { writerLoop() }
    }

    /** 批量写入循环:收到首条后攒批(32 条或 200ms 窗口),Mutex 内批量落库 + ACK。 */
    private suspend fun writerLoop() {
        val batch = ArrayList<Pair<CryptoRecord, (Long) -> Unit>>(BATCH_SIZE)
        while (true) {
            batch.clear()
            batch.add(pending.receive())
            pendingSlots.release()
            val deadline = System.currentTimeMillis() + BATCH_WINDOW_MS
            while (batch.size < BATCH_SIZE && System.currentTimeMillis() < deadline) {
                val remaining = deadline - System.currentTimeMillis()
                val item = withTimeoutOrNull(remaining) { pending.receive() } ?: break
                pendingSlots.release()
                batch.add(item)
            }
            bumpStats { it.copy(received = it.received + batch.size) }
            writeMutex.withLock {
                if (persistBatch(batch)) {
                    ackBatch(batch)
                } else {
                    // 写库最终失败:不 ACK,注入侧 WAL 保留并重放(唯一索引幂等吸收重复)
                    Log.w("Modev", "insert batch failed after ${MAX_INSERT_ATTEMPTS} attempts (${batch.size} records)")
                    bumpStats { it.copy(writeFailures = it.writeFailures + batch.size) }
                }
            }
            maybeTrim(batch.size)
        }
    }

    /** 批量落库,失败重试 [MAX_INSERT_ATTEMPTS] 次,全部失败返回 false。 */
    private suspend fun persistBatch(batch: List<Pair<CryptoRecord, (Long) -> Unit>>): Boolean {
        var lastError: Throwable? = null
        repeat(MAX_INSERT_ATTEMPTS) {
            try {
                dao.insertAll(batch.map { it.first.toEntity(gson) })
                return true
            } catch (t: Throwable) {
                lastError = t
            }
        }
        Log.w("Modev", "persist batch failed: $lastError")
        return false
    }

    /**
     * 批量确认:按会话(ACK 回调)聚合取最大 seq 回执——
     * 每批只回一个游标,注入侧裁剪该 seq 之前全部行,协议开销 O(批次)。
     */
    private fun ackBatch(batch: List<Pair<CryptoRecord, (Long) -> Unit>>) {
        val byAck = HashMap<(Long) -> Unit, Long>()
        for ((record, ack) in batch) {
            val max = byAck[ack] ?: -1L
            if (record.seq > max) byAck[ack] = record.seq
        }
        for ((ack, seq) in byAck) {
            runCatching { ack(seq) }
        }
    }

    /** ③ 双层容量治理:每 [TRIM_EVERY] 条执行一次(全局 + 每包)。 */
    private var insertedSinceTrim = 0L

    private suspend fun maybeTrim(batchSize: Int) {
        insertedSinceTrim += batchSize
        if (insertedSinceTrim < TRIM_EVERY) return
        insertedSinceTrim = 0
        try {
            val counts = dao.counts()
            var trimmedNow = 0L
            var total = 0L
            for (c in counts) {
                total += c.count
                if (c.count > PER_PACKAGE_MAX) {
                    trimmedNow += dao.trimPackage(c.packageName, PER_PACKAGE_MAX)
                }
            }
            if (total > maxRecords) {
                trimmedNow += dao.trim(maxRecords)
            }
            if (trimmedNow > 0) {
                bumpStats { it.copy(trimmed = it.trimmed + trimmedNow) }
            }
        } catch (t: Throwable) {
            Log.w("Modev", "trim failed: $t")
        }
    }

    fun observe(
        packageName: String? = null,
        kind: CryptoKind? = null,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<CryptoRecord>> =
        dao.observe(packageName, kind?.name, limit).map { list -> list.map { it.toModel(gson) } }

    /**
     * 分页记录流(Paging3):时间倒序,支持包/类别/关键字过滤。
     * 关键字在 SQL 侧匹配(algorithm/operation/hex/文本/线程),不在内存过滤。
     * 2026-08-17 大数据修复:列表走投影查询 [CryptoRecordDao.observePagedRows],
     * 仅物化展示列与 SQL 侧预览,零大字段加载。
     */
    fun pagedRecords(
        packageName: String? = null,
        kind: CryptoKind? = null,
        query: String = "",
    ): Flow<PagingData<CryptoRecord>> {
        val like = if (query.isBlank()) null else likePattern(query.trim())
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                dao.observePagedRows(packageName, kind?.name, like, previewChars = ROW_PREVIEW_CHARS, limit = PAGE_SIZE, offset = 0)
            },
        ).flow.map { pagingData -> pagingData.map { it.toRowModel() } }
    }

    /** 每包记录数(应用列表徽标)。 */
    fun observeRecordCounts(): Flow<Map<String, Long>> =
        dao.observeRecordCounts().map { list -> list.associate { it.packageName to it.count } }

    /** 单条记录(记录详情页),记录被清理后返回 null。 */
    fun observeById(id: Long): Flow<CryptoRecord?> =
        dao.observeById(id).map { it?.toModel(gson) }

    /**
     * 相邻记录 id([newer]=true 取更新的、false 取更旧的),用于记录详情"上一条/下一条"。
     * 2026-08-17 大数据修复:仅投影 id,不再物化邻居全量大字段。
     */
    suspend fun neighborIdOf(record: CryptoRecord, newer: Boolean): Long? =
        if (newer) {
            dao.neighborIdNewerThan(record.packageName, record.timestamp, record.id)
        } else {
            dao.neighborIdOlderThan(record.packageName, record.timestamp, record.id)
        }

    fun observePackages(): Flow<List<String>> = dao.observePackages()

    suspend fun clear(packageName: String) = dao.clearPackage(packageName)

    suspend fun clearAll() = dao.clearAll()

    /** SQL LIKE 模式转义(% / _ / \)。 */
    private fun likePattern(raw: String): String =
        "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private fun CryptoRecord.toEntity(gson: Gson) = CryptoRecordEntity(
        recordType = recordType,
        packageName = packageName,
        kind = kind.name,
        operation = operation.name,
        algorithm = algorithm,
        provider = provider,
        direction = direction,
        keyHex = key?.hex,
        keyText = key?.text,
        keyLength = key?.totalLength,
        keyTruncated = key?.truncated ?: false,
        ivHex = iv?.hex,
        ivText = iv?.text,
        ivLength = iv?.totalLength,
        ivTruncated = iv?.truncated ?: false,
        keyAlias = keyAlias,
        keyExportable = keyExportable,
        inputHex = input?.hex,
        inputText = input?.text,
        inputLength = input?.totalLength,
        inputTruncated = input?.truncated ?: false,
        outputHex = output?.hex,
        outputText = output?.text,
        outputLength = output?.totalLength,
        outputTruncated = output?.truncated ?: false,
        saltHex = salt?.hex,
        saltText = salt?.text,
        saltLength = salt?.totalLength,
        saltTruncated = salt?.truncated ?: false,
        aadHex = aad?.hex,
        aadText = aad?.text,
        aadLength = aad?.totalLength,
        aadTruncated = aad?.truncated ?: false,
        publicKeyHex = publicKey?.hex,
        publicKeyText = publicKey?.text,
        publicKeyLength = publicKey?.totalLength,
        publicKeyTruncated = publicKey?.truncated ?: false,
        paramsJson = params?.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
        verifyResult = verifyResult,
        processName = processName,
        contextId = contextId,
        threadName = threadName,
        stackJson = if (stack.isEmpty()) null else gson.toJson(stack),
        timestamp = timestamp,
        seq = seq,
    )

    private fun CryptoRecordEntity.toModel(gson: Gson) = CryptoRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = enumOf(CryptoKind::class.java, kind),
        operation = enumOf(CryptoOperation::class.java, operation),
        algorithm = algorithm,
        provider = provider,
        direction = direction,
        key = view(keyHex, keyText, keyLength, keyTruncated),
        iv = view(ivHex, ivText, ivLength, ivTruncated),
        keyAlias = keyAlias,
        keyExportable = keyExportable,
        input = view(inputHex, inputText, inputLength, inputTruncated),
        output = view(outputHex, outputText, outputLength, outputTruncated),
        salt = view(saltHex, saltText, saltLength, saltTruncated),
        aad = view(aadHex, aadText, aadLength, aadTruncated),
        publicKey = view(publicKeyHex, publicKeyText, publicKeyLength, publicKeyTruncated),
        params = paramsJson?.let { runCatching { gson.fromJson(it, Map::class.java) as Map<String, String> }.getOrNull() },
        verifyResult = verifyResult,
        processName = processName,
        contextId = contextId,
        threadName = threadName,
        stack = stackJson?.let { runCatching { gson.fromJson(it, Array<String>::class.java).toList() }.getOrNull() } ?: emptyList(),
        timestamp = timestamp,
        seq = seq ?: 0L,
    )

    private fun view(hex: String?, text: String?, length: Int?, truncated: Boolean): BytesView? =
        if (hex == null) null else BytesView(hex = hex, text = text, totalLength = length ?: 0, truncated = truncated)

    /**
     * 列表投影行 → 轻量展示模型:仅填充行展示所需字段,
     * 大字段以 SQL 侧预览呈现(hex 为预览字符串,非全量)。
     */
    private fun CryptoRecordRow.toRowModel() = CryptoRecord(
        id = id,
        packageName = packageName,
        kind = enumOf(CryptoKind::class.java, kind),
        operation = enumOf(CryptoOperation::class.java, operation),
        algorithm = algorithm,
        direction = direction,
        keyAlias = keyAlias,
        key = previewView(keyPreview, keyTruncated),
        input = previewView(inputPreview, inputTruncated),
        output = previewView(outputPreview, outputTruncated),
        threadName = threadName,
        timestamp = timestamp,
    )

    /** 投影预览 → 展示 BytesView(仅存在于列表行;totalLength 由 hex 长度推断,仅供摘要展示)。 */
    private fun previewView(hex: String?, truncated: Boolean): BytesView? =
        if (hex == null) null else BytesView(hex = hex, text = null, totalLength = hex.length / 2, truncated = truncated)

    private inline fun <reified T : Enum<T>> enumOf(clazz: Class<T>, name: String): T =
        runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrDefault(clazz.enumConstants.first())

    companion object {
        private const val MAX_RECORDS = 20_000
        private const val DEFAULT_LIMIT = 2_000
        private const val PAGE_SIZE = 60

        /** 单包容量上限(每包治理)。 */
        private const val PER_PACKAGE_MAX = 5_000

        /** 列表行 SQL 侧预览字符数(24 字节 hex;行摘要仅需首段)。 */
        private const val ROW_PREVIEW_CHARS = 48

        /** 批量写入窗口:32 条或 200ms。 */
        private const val BATCH_SIZE = 32
        private const val BATCH_WINDOW_MS = 200L

        /** 待入库通道容量,溢出丢弃并计数。 */
        private const val PENDING_CAPACITY = 8192

        /** 批量落库失败重试次数(仍失败不 ACK,注入侧 WAL 保留重放)。 */
        private const val MAX_INSERT_ATTEMPTS = 3

        /** 每插入这么多条执行一次双层容量治理。 */
        private const val TRIM_EVERY = 500L
    }
}

/**
 * 宿主管道统计:收到/丢弃/写失败/裁剪 + 通道拒绝计数,供设置页展示。
 */
data class PipelineStats(
    val received: Long = 0L,
    val dropped: Long = 0L,
    val writeFailures: Long = 0L,
    val trimmed: Long = 0L,
    val channel: ChannelStats = ChannelStats(),
)