package com.lioisme.modev.data.repo

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.flatMap
import androidx.paging.map
import com.lioisme.modev.core.codec.CryptoRecordCodec
import com.lioisme.modev.core.codec.RecordCodecRegistry
import com.lioisme.modev.core.codec.SqlRecordCodec
import com.lioisme.modev.core.codec.WireRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.data.db.CryptoRecordDao
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.data.db.RecordTypeCount
import com.lioisme.modev.data.mapping.RecordDetailModel
import com.lioisme.modev.data.mapping.RowModel
import com.lioisme.modev.data.mapping.toEntity
import com.lioisme.modev.data.mapping.toRecordDetailOrNull
import com.lioisme.modev.data.mapping.toRowModelOrNull
import com.lioisme.modev.data.server.ChannelStats
import com.lioisme.modev.data.server.ConfigServer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 日志仓库(ADR-1):类型化记录摄入 → 批量入库(200ms/32 条窗口)→ 双层容量治理 → 供 UI 订阅。
 *
 * - 摄入泛化:server 回调 [WireRecord] 通用信封,按 [RecordCodecRegistry] 类型化解码,
 *   新增监听类型零改动本类;
 * - 解码失败/未注册类型:计数丢弃,但仍按会话聚合 ACK(防注入侧 WAL 永久重放);
 * - 落库最终失败:不 ACK,注入侧 WAL 保留重放(唯一索引幂等吸收);
 * - 管道统计(丢弃/写失败/裁剪/通道拒绝)汇入 [statsFlow]。
 *
 * 接线:显式 [start]()(宿主 AppContainer 启动时调用),不依赖构造副作用。
 */
class LogRepository(
    private val dao: CryptoRecordDao,
    private val server: ConfigServer,
    private val registry: RecordCodecRegistry = cryptoRegistry(),
    private val maxRecords: Int = MAX_RECORDS,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    /** 待入库记录通道:信封 + 其所属会话的 ACK 回调。 */
    private val pending = Channel<Pair<WireRecord, (Long) -> Unit>>(PENDING_CAPACITY)

    /**
     * 背压信号量:与通道容量相等,满时在 [ACQUIRE_TIMEOUT_MS] 窗口内
     * 阻塞服务端客户端线程(经 socket 写压力反向传导至注入侧 WAL,注入侧先落盘再发送,
     * 天然安全);窗口超时仍无槽位则丢弃并计数,绝不无限挂起客户端线程。
     */
    private val pendingSlots = Semaphore(PENDING_CAPACITY)

    @Volatile
    private var started = false

    private val _stats = MutableStateFlow(PipelineStats())
    val statsFlow: StateFlow<PipelineStats> = _stats

    private fun bumpStats(transform: (PipelineStats) -> PipelineStats) {
        _stats.update { transform(it) }
    }

    init {
        // 通道侧拒绝/丢弃计数汇入管道统计
        scope.launch {
            server.statsFlow.collect { channel ->
                bumpStats { it.copy(channel = channel) }
            }
        }
        scope.launch { writerLoop() }
    }

    /** 显式接线记录回调(进程启动时由 AppContainer 调用)。 */
    fun start() {
        if (started) return
        started = true
        server.onRecord = ::onInjectedRecord
    }

    private fun onInjectedRecord(record: WireRecord, ack: (Long) -> Unit) {
        val acquired = try {
            pendingSlots.tryAcquire(ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            false
        }
        if (!acquired) {
            Log.w("Modev", "pending queue overflow, dropped record seq=${record.seq} type=${record.recordType}")
            bumpStats { it.copy(dropped = it.dropped + 1) }
            return
        }
        val sent = pending.trySend(record to ack)
        if (sent.isFailure) {
            pendingSlots.release()
            bumpStats { it.copy(dropped = it.dropped + 1) }
        }
    }

    /** 批量写入循环:收到首条后攒批(32 条或 200ms 窗口),Mutex 内批量落库 + ACK。 */
    private suspend fun writerLoop() {
        val batch = ArrayList<Pair<WireRecord, (Long) -> Unit>>(BATCH_SIZE)
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
                writeBatch(batch)
            }
            maybeTrim(batch.size)
        }
    }

    private suspend fun writeBatch(batch: List<Pair<WireRecord, (Long) -> Unit>>) {
        val toPersist = ArrayList<CryptoRecordEntity>(batch.size)
        val decodableAcks = ArrayList<Pair<Long, (Long) -> Unit>>(batch.size)
        val undecodableAcks = ArrayList<Pair<Long, (Long) -> Unit>>(batch.size)
        for ((wire, ack) in batch) {
            when (val decoded = registry.decode(wire)) {
                is CryptoRecord -> {
                    decodableAcks.add(wire.seq to ack)
                    toPersist.add(decoded.toEntity())
                }
                is SqlRecord -> {
                    decodableAcks.add(wire.seq to ack)
                    toPersist.add(decoded.toEntity())
                }
                else -> {
                    // 未知记录类型/畸形载荷:计数丢弃;ACK 单独回执(防注入侧 WAL 永久重放)
                    undecodableAcks.add(wire.seq to ack)
                    Log.w("Modev", "dropped undecodable record type=${wire.recordType} seq=${wire.seq}")
                    bumpStats { it.copy(dropped = it.dropped + 1) }
                }
            }
        }
        val persisted = toPersist.isEmpty() || persistBatch(toPersist)
        if (persisted) {
            // 全部落库成功(或无可落库):按会话聚合回执全部游标
            AckAggregator.maxSeqPerSession(decodableAcks + undecodableAcks)
                .forEach { (seq, ack) -> runCatching { ack(seq) } }
        } else {
            // 写库最终失败:可解码记录不 ACK(注入侧 WAL 保留重放,唯一索引幂等吸收);
            // 不可解码记录仍回执(其重放无意义且永不成功)
            Log.w("Modev", "insert batch failed after ${MAX_INSERT_ATTEMPTS} attempts (${toPersist.size} records)")
            bumpStats { it.copy(writeFailures = it.writeFailures + toPersist.size) }
            AckAggregator.maxSeqPerSession(undecodableAcks)
                .forEach { (seq, ack) -> runCatching { ack(seq) } }
        }
    }

    /** 批量落库,失败重试 [MAX_INSERT_ATTEMPTS] 次,全部失败返回 false。 */
    private suspend fun persistBatch(records: List<CryptoRecordEntity>): Boolean {
        var lastError: Throwable? = null
        repeat(MAX_INSERT_ATTEMPTS) {
            try {
                dao.insertAll(records)
                return true
            } catch (t: Throwable) {
                lastError = t
            }
        }
        Log.w("Modev", "persist batch failed: $lastError")
        return false
    }

    /** ③ 双层容量治理:每 [TRIM_EVERY] 条执行一次(全局 + 每包)。 */
    private var insertedSinceTrim = 0L

    private suspend fun maybeTrim(batchSize: Int) {
        insertedSinceTrim += batchSize
        if (insertedSinceTrim < TRIM_EVERY) return
        insertedSinceTrim = 0
        try {
            // V17:跨类型计数(recordType=null = 全部),每包上限与全局上限均按全类型治理
            val counts = dao.counts(null)
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

    /**
     * 分页记录流(Paging3):时间倒序,支持包/类别/关键字/记录类型过滤。
     * 关键字在 SQL 侧匹配(algorithm/operation/hex/文本/线程),不在内存过滤。
     * 列表走投影查询 [CryptoRecordDao.observePagedRows],仅物化展示列与 SQL 侧预览,
     * 零大字段加载;枚举未知的行按 null 丢弃(计数由 DAO 层不感知,属 Schema 漂移兜底)。
     * V17 R2:recordType 参数化(null = 全部类型,跨监听类型"整体"语义)。
     * V17 R3:kindName 字符串化(CryptoKind.name / SqlKind.name 共用 kind 列,跨枚举过滤)。
     */
    fun pagedRecords(
        packageName: String? = null,
        kindName: String? = null,
        query: String = "",
        recordType: String? = null,
    ): Flow<PagingData<RowModel>> {
        val like = if (query.isBlank()) null else likePattern(query.trim())
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                dao.observePagedRows(
                    recordType = recordType,
                    packageName = packageName,
                    kind = kindName,
                    query = like,
                    previewChars = ROW_PREVIEW_CHARS,
                    limit = PAGE_SIZE,
                    offset = 0,
                )
            },
        ).flow.map { pagingData ->
            pagingData.flatMap { row ->
                row.toRowModelOrNull()?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    /** 每包记录数(应用列表徽标;V17 R2 recordType 可空 = 全类型合计)。 */
    fun observeRecordCounts(recordType: String? = null): Flow<Map<String, Long>> =
        dao.observeRecordCounts(recordType).map { list -> list.associate { it.packageName to it.count } }

    /**
     * 统一查询契约分页入口(V17 R2;REST/MCP 唯一依赖面):
     * [RecordFilter] 集合多值语义,空集/空 query = 不过滤;单值退化为与
     * [pagedRecords] 等价的 DAO 路径(IN 单元素),行为一致。
     */
    fun paged(filter: RecordFilter): Flow<PagingData<RowModel>> {
        val query = filter.query?.takeIf { it.isNotBlank() }?.let(::likePattern)
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                dao.observePagedRowsByFilter(
                    recordTypes = filter.recordTypes.toBindList(),
                    packages = filter.packages.toBindList(),
                    kinds = filter.kinds.toBindList(),
                    typeCount = filter.recordTypes?.size ?: 0,
                    packageCount = filter.packages?.size ?: 0,
                    kindCount = filter.kinds?.size ?: 0,
                    query = query,
                    previewChars = ROW_PREVIEW_CHARS,
                    limit = PAGE_SIZE,
                    offset = 0,
                )
            },
        ).flow.map { pagingData ->
            pagingData.flatMap { row ->
                row.toRowModelOrNull()?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    /** 统一查询契约计数入口(V17 R2):匹配 [RecordFilter] 的记录总数(分页元信息)。 */
    fun counts(filter: RecordFilter): Flow<Long> =
        dao.observeCountByFilter(
            recordTypes = filter.recordTypes.toBindList(),
            packages = filter.packages.toBindList(),
            kinds = filter.kinds.toBindList(),
            typeCount = filter.recordTypes?.size ?: 0,
            packageCount = filter.packages?.size ?: 0,
            kindCount = filter.kinds?.size ?: 0,
            query = filter.query?.takeIf { it.isNotBlank() }?.let(::likePattern),
        )

    /** 按记录类型分组计数(V17 R2,全类型总览基础)。 */
    fun observeRecordTypeCounts(): Flow<List<RecordTypeCount>> = dao.observeRecordTypeCounts()

    /** 单条记录(记录详情页),记录被清理或不可解码后返回 null。 */
    fun observeById(id: Long): Flow<RecordDetailModel?> =
        dao.observeById(id).map { it?.toRecordDetailOrNull() }

    /**
     * 相邻记录 id([newer]=true 取更新的、false 取更旧的),用于记录详情"上一条/下一条"。
     * 仅投影 id,不再物化邻居全量大字段;按 recordType 限定(算法/数据库各自邻接,
     * 混排时跨类型的相邻在时间线语义上无意义)。
     */
    suspend fun neighborIdOf(
        recordType: String,
        packageName: String,
        timestamp: Long,
        id: Long,
        newer: Boolean,
    ): Long? =
        if (newer) {
            dao.neighborIdNewerThan(recordType, packageName, timestamp, id)
        } else {
            dao.neighborIdOlderThan(recordType, packageName, timestamp, id)
        }

    fun observePackages(): Flow<List<String>> = dao.observePackages(RecordTypes.CRYPTO)

    suspend fun clear(packageName: String) = dao.clearPackage(packageName)

    suspend fun clearAll() = dao.clearAll()

    /** SQL LIKE 模式转义(% / _ / \)。 */
    private fun likePattern(raw: String): String =
        "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    /**
     * RecordFilter 集合 → Room IN 绑定列表:null/空集绑定哨兵单例(内容无意义,
     * 由 DAO 侧 `:xCount = 0 OR` 短路为不过滤);非空集合原样绑定。
     */
    private fun Set<String>?.toBindList(): List<String> =
        this?.toList()?.takeIf { it.isNotEmpty() } ?: listOf(EMPTY_IN_SENTINEL)

    companion object {
        private fun cryptoRegistry(): RecordCodecRegistry =
            RecordCodecRegistry().apply {
                register(CryptoRecordCodec.RECORD_TYPE, CryptoRecordCodec::decode)
                register(SqlRecordCodec.RECORD_TYPE, SqlRecordCodec::decode)
            }

        private const val MAX_RECORDS = 20_000
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

        /** 背压阻塞窗口:超时丢弃并计数,绝不无限挂起客户端线程。 */
        private const val ACQUIRE_TIMEOUT_MS = 10_000L

        /** 批量落库失败重试次数(仍失败不 ACK,注入侧 WAL 保留重放)。 */
        private const val MAX_INSERT_ATTEMPTS = 3

        /** 每插入这么多条执行一次双层容量治理。 */
        private const val TRIM_EVERY = 500L

        /** Room IN 绑定哨兵(Room 不支持空集合绑定;哨兵内容不被求值)。 */
        private const val EMPTY_IN_SENTINEL = ""
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