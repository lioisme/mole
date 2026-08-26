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
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.codec.BehaviorRecordCodec
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 日志仓库(ADR-1;V27 更名注释修复):类型化记录摄入、批量入库(200ms/32 条窗口)、
 * 双层容量治理(每包/全局)、UI 订阅出口。
 *
 * - 摄入泛化:server 回调 [WireRecord] 通用信封,经 [RecordCodecRegistry] 类型化解码,
 *   新增监听类型零改动本类;
 * - 解码失败/未注册类型:计数丢弃,但仍按会话聚合 ACK(防注入侧 WAL 永久重放);
 * - 落库最终失败:不 ACK,注入侧 WAL 保留重放(唯一索引幂等吸收);
 * - 管道统计(丢弃/写失败/裁剪/通道拒绝)汇入 [statsFlow]。
 *
 * 读写门面:实现 [RecordService](V22),所有查询/增删改查经其暴露,
 * DAO 不对外暴露,UI 与未来 MCP 共用同一契约。
 *
 * 接线:显式 [start]()(宿主 AppContainer 启动时调用),不依赖构造副作用。
 */
class LogRepository(
    private val dao: CryptoRecordDao,
    private val server: ConfigServer,
    private val registry: RecordCodecRegistry = cryptoRegistry(),
    private val maxRecords: Int = MAX_RECORDS,
    /** 主库文件提供者(V23 存储占用统计;未装配时 [storageUsage] 返回 0)。 */
    private val databaseFile: (() -> java.io.File)? = null,
) : RecordService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    /** 待入库记录通道:信封 + 其所属会话的 ACK 回调。 */
    private val pending = Channel<Pair<WireRecord, (Long) -> Unit>>(PENDING_CAPACITY)

    /**
     * 背压信号量:与通道容量相等,满时在 [ACQUIRE_TIMEOUT_MS] 窗口内
     * 阻塞服务端客户端线程(以 socket 写压力反向传导至注入侧 WAL,
     * 注入侧先落盘再发送、天然安全);窗口超时仍无槽位则丢弃并计数,
     * 绝不无限挂起客户端线程。
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
                is BehaviorRecord -> {
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
            // 全部落库成功(或无待持久项):按会话聚合回执全部游标
            AckAggregator.maxSeqPerSession(decodableAcks + undecodableAcks)
                .forEach { (seq, ack) -> runCatching { ack(seq) } }
        } else {
            // 写库最终失败:可解码记录不 ACK(注入侧 WAL 保留重放,唯一索引幂等吸收);
            // 不可解码记录仍回执(其重放无意义且永不可能成功)。
            Log.w("Modev", "insert batch failed after ${MAX_INSERT_ATTEMPTS} attempts (${toPersist.size} records)")
            bumpStats { it.copy(writeFailures = it.writeFailures + toPersist.size) }
            AckAggregator.maxSeqPerSession(undecodableAcks)
                .forEach { (seq, ack) -> runCatching { ack(seq) } }
        }
    }

    /** 批量落库,失败重试 [MAX_INSERT_ATTEMPTS] 次;全部失败返回 false。 */
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

    /** 双层容量治理(每 [TRIM_EVERY] 条执行一次):全局 + 每包。 */
    private var insertedSinceTrim = 0L

    private suspend fun maybeTrim(batchSize: Int) {
        insertedSinceTrim += batchSize
        if (insertedSinceTrim < TRIM_EVERY) return
        insertedSinceTrim = 0
        try {
            // V17:跨类型计数(recordType=null = 全部),每包上限与全局上限均按全类型治理。
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

    // ----------------------------------------------------------------
    // RecordService 实现(读 / 写 / 删 / 清理 / 统计)
    // ----------------------------------------------------------------

    /**
     * 流式分页记录(Paging3):时间倒序,支持记录类型/类别/操作/关键字/时间范围过滤。
     * 关键字在 SQL 侧匹配(算法/操作/hex/文本/线程/SQL/路径),不在内存过滤;
     * 列表走投影查询 [CryptoRecordDao.observePagedRowsByFilter],仅物化展示列 + SQL 侧预览,
     * 零大字段加载;枚举未知的行返回 null 丢弃(计数在 DAO 层不感知,防 Schema 漂移兜底)。
     * V17 R2:recordType 参数为 null = 全部类型,跨监听类别「整体」语义。
     * V22:kind/operation 经 [RecordFilter.normalize] 作用域收敛,空交集短路为零结果。
     */
    override fun observe(filter: RecordFilter): Flow<PagingData<RowModel>> {
        val r = resolve(filter)
        if (r.noMatch) return flowOf(PagingData.empty())
        val like = r.query
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                dao.observePagedRowsByFilter(
                    recordTypes = r.recordTypes,
                    packages = r.packages,
                    kinds = r.kinds,
                    operations = r.operations,
                    typeCount = r.typeCount,
                    packageCount = r.packageCount,
                    kindCount = r.kindCount,
                    opCount = r.opCount,
                    timeStart = r.timeStart,
                    timeEnd = r.timeEnd,
                    query = like,
                    previewChars = ROW_PREVIEW_CHARS,
                    limit = PAGING_WINDOW_CAP,
                    offset = 0,
                )
            },
        ).flow.map { pagingData ->
            pagingData.flatMap { row ->
                row.toRowModelOrNull()?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    /** 同步切片查询(供 MCP / 导出):返回当页 + 总量,不入 Paging 冷流。 */
    override suspend fun queryPage(filter: RecordFilter, page: Int, pageSize: Int): RecordPage {
        val r = resolve(filter)
        if (r.noMatch) return RecordPage(emptyList(), 0, page, pageSize)
        val total = count(filter).first()
        if (total == 0L) return RecordPage(emptyList(), 0, page, pageSize)
        val safePage = page.coerceAtLeast(0)
        val safeSize = pageSize.coerceAtLeast(1)
        val offset = safePage * safeSize
        val rows: List<CryptoRecordRow> = withContext(Dispatchers.IO) {
            if (filter.sort == SortOrder.ASC) {
                dao.sliceByFilterAsc(
                    recordTypes = r.recordTypes, packages = r.packages, kinds = r.kinds,
                    operations = r.operations, typeCount = r.typeCount, packageCount = r.packageCount,
                    kindCount = r.kindCount, opCount = r.opCount, timeStart = r.timeStart, timeEnd = r.timeEnd,
                    query = r.query, previewChars = ROW_PREVIEW_CHARS, limit = safeSize, offset = offset,
                )
            } else {
                dao.sliceByFilterDesc(
                    recordTypes = r.recordTypes, packages = r.packages, kinds = r.kinds,
                    operations = r.operations, typeCount = r.typeCount, packageCount = r.packageCount,
                    kindCount = r.kindCount, opCount = r.opCount, timeStart = r.timeStart, timeEnd = r.timeEnd,
                    query = r.query, previewChars = ROW_PREVIEW_CHARS, limit = safeSize, offset = offset,
                )
            }
        }
        val items = rows.mapNotNull { it.toRowModelOrNull() }
        return RecordPage(items = items, total = total, page = safePage, pageSize = safeSize)
    }

    /** 统一查询契约计数入口(V17 R2;REST/MCP 唯一依赖口)。 */
    override fun count(filter: RecordFilter): Flow<Long> {
        val r = resolve(filter)
        if (r.noMatch) return flowOf(0L)
        return dao.observeCountByFilter(
            recordTypes = r.recordTypes,
            packages = r.packages,
            kinds = r.kinds,
            operations = r.operations,
            typeCount = r.typeCount,
            packageCount = r.packageCount,
            kindCount = r.kindCount,
            opCount = r.opCount,
            timeStart = r.timeStart,
            timeEnd = r.timeEnd,
            query = r.query,
        )
    }

    /** 单条记录(记录详情源),记录被清理或不可解码后返回 null。 */
    override fun get(id: Long): Flow<RecordDetailModel?> =
        dao.observeById(id).map { it?.toRecordDetailOrNull() }

    /** 相邻记录 id(详情「上一条/下一条」),仅投影 id,不物化邻居大字段。 */
    override suspend fun neighbor(id: Long, newer: Boolean): Long? {
        val rec = dao.observeById(id).first() ?: return null
        return withContext(Dispatchers.IO) {
            if (newer) {
                dao.neighborIdNewerThan(rec.recordType, rec.packageName, rec.timestamp, id)
            } else {
                dao.neighborIdOlderThan(rec.recordType, rec.packageName, rec.timestamp, id)
            }
        }
    }

    /** 新增单条(供 MCP 导入 / 手动补录),返回自增 id。 */
    override suspend fun create(record: CryptoRecordEntity): Long = dao.insertReturningId(record)

    /** 更新单条(按主键覆盖,MCP 编辑),成功返回 true。 */
    override suspend fun update(record: CryptoRecordEntity): Boolean {
        dao.upsert(record)
        return true
    }

    /** 按 id 删除单条。 */
    override suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    /** 按 [RecordFilter] 批量删除(共享 [CryptoRecordDao.WHERE_FILTER],保证口径单一)。 */
    override suspend fun deleteByFilter(filter: RecordFilter): Long {
        val r = resolve(filter)
        if (r.noMatch) return 0L
        // 全字段空 = 无约束,拒绝整表误删(显式整清请用 [clearAll])。
        if (!filter.hasConstraint) return 0L
        return dao.deleteByFilter(
            recordTypes = r.recordTypes,
            packages = r.packages,
            kinds = r.kinds,
            operations = r.operations,
            typeCount = r.typeCount,
            packageCount = r.packageCount,
            kindCount = r.kindCount,
            opCount = r.opCount,
            timeStart = r.timeStart,
            timeEnd = r.timeEnd,
            query = r.query,
        ).toLong()
    }

    override suspend fun clearPackage(packageName: String) = dao.clearPackage(packageName)

    /** 按记录类型清理(V19 T7 数据管理按类型清空)。 */
    override suspend fun clearByType(recordType: String) = dao.clearByType(recordType)

    override suspend fun clearAll() = dao.clearAll()

    /**
     * 存储占用(V23):主库 + `-wal` + `-shm` 三文件字节和,单一出口供
     * 数据管理页/诊断消费(UI 禁止直触 [com.lioisme.modev.data.db.ModevDatabase])。
     * 未装配 databaseFile 时返回 0。
     */
    suspend fun storageUsage(): Long {
        val provider = databaseFile ?: return 0L
        return withContext(Dispatchers.IO) {
            val main = provider()
            listOf("", "-wal", "-shm").sumOf { suffix ->
                val file = java.io.File(main.parentFile, main.name + suffix)
                if (file.exists()) file.length() else 0L
            }
        }
    }

    // V27 修复:原硬编码 RecordTypes.CRYPTO 只统计算法包;null = 全类型(应用列表徽标跨类型对账)。
    override fun observePackages(): Flow<List<String>> = dao.observePackages(null)

    /** 按记录类型分组计数(V17 R2,全类型总览基础)。 */
    override fun observeTypeCounts(): Flow<List<RecordTypeCount>> = dao.observeRecordTypeCounts()

    /** 兼容别名([DataSettingsViewModel] 既有调用)。 */
    fun observeRecordTypeCounts(): Flow<List<RecordTypeCount>> = observeTypeCounts()

    /** 相邻记录 id(详情"上一条/下一条")兼容入口([RecordDetailScreen] 既有调用)。 */
    suspend fun neighborIdOf(
        recordType: String,
        packageName: String,
        timestamp: Long,
        id: Long,
        newer: Boolean,
    ): Long? = withContext(Dispatchers.IO) {
        if (newer) {
            dao.neighborIdNewerThan(recordType, packageName, timestamp, id)
        } else {
            dao.neighborIdOlderThan(recordType, packageName, timestamp, id)
        }
    }

    /** 兼容别名([RecordDetailScreen] 既有调用),返回详情模型流。 */
    fun observeById(id: Long): Flow<RecordDetailModel?> = get(id)

    /** 兼容别名([AppMonitorScreen] 既有调用)。 */
    suspend fun clear(packageName: String) = clearPackage(packageName)

    /** 指定类型每类别计数(筛选菜单徽标)。 */
    override suspend fun categoryCounts(type: RecordType): Map<String, Long> =
        dao.observeCategoryCounts(type.wire).first().associate { it.kind to it.count }

    // ----------------------------------------------------------------
    // 兼容便捷方法(UI 既有调用)
    // ----------------------------------------------------------------

    /**
     * 按包/类别/关键字/记录类型分页(便捷封装,委托 [observe])。
     * kindName 为单类别 id(CryptoKind.name / SqlKind.name),经 [RecordFilter] 作用域收敛。
     */
    fun pagedRecords(
        packageName: String? = null,
        kindName: String? = null,
        query: String = "",
        recordType: String? = null,
    ): Flow<PagingData<RowModel>> {
        val filter = RecordFilter(
            recordTypes = recordType?.let { setOf(it) },
            packages = packageName?.let { setOf(it) },
            kinds = kindName?.let { setOf(it) },
            query = query,
        )
        return observe(filter)
    }

    /** 统一查询契约分页入口(V17 R2;REST/MCP 唯一依赖口)。 */
    fun paged(filter: RecordFilter): Flow<PagingData<RowModel>> = observe(filter)

    /** 兼容别名:统计(委托 [count])。 */
    fun counts(filter: RecordFilter): Flow<Long> = count(filter)

    fun observeRecordCounts(recordType: String? = null): Flow<Map<String, Long>> =
        dao.observeRecordCounts(recordType).map { list -> list.associate { it.packageName to it.count } }

    /** SQL LIKE 模式转义(% / _ / \)。 */
    private fun likePattern(raw: String): String =
        "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    /**
     * RecordFilter 集合 → Room IN 绑定列表:null/空集绑定哨兵单例(内容无意义,
     * 由 DAO 的 `:xCount = 0 OR` 短路为不过滤);非空集合原样绑定。
     */
    private fun Set<String>?.toBindList(): List<String> =
        this?.toList()?.takeIf { it.isNotEmpty() } ?: listOf(EMPTY_IN_SENTINEL)

    /**
     * 将 [RecordFilter] 解析为共享 WHERE 绑定参数 + 空交集短路标记。
     * - [noMatch]:kinds/operations 经 [RecordFilter.normalize] 收敛后为空集(显式无匹配),调用方短路为零结果;
     * - 其余参数恒可安全绑定(哨兵语义由 DAO WHERE 处理)。
     */
    private data class Resolved(
        val noMatch: Boolean,
        val recordTypes: List<String>,
        val packages: List<String>,
        val kinds: List<String>,
        val operations: List<String>,
        val typeCount: Int,
        val packageCount: Int,
        val kindCount: Int,
        val opCount: Int,
        val timeStart: Long?,
        val timeEnd: Long?,
        val query: String?,
    )

    private fun resolve(filter: RecordFilter): Resolved {
        val norm = filter.normalize()
        val kindsEmpty = filter.kinds != null && norm.kinds?.isEmpty() == true
        val opsEmpty = filter.operations != null && norm.operations?.isEmpty() == true
        val query = norm.query?.takeIf { it.isNotBlank() }?.let(::likePattern)
        return Resolved(
            noMatch = kindsEmpty || opsEmpty,
            recordTypes = norm.recordTypes.toBindList(),
            packages = norm.packages.toBindList(),
            kinds = norm.kinds.toBindList(),
            operations = norm.operations.toBindList(),
            typeCount = norm.recordTypes?.size ?: 0,
            packageCount = norm.packages?.size ?: 0,
            kindCount = norm.kinds?.size ?: 0,
            opCount = norm.operations?.size ?: 0,
            timeStart = norm.timeRange?.start,
            timeEnd = norm.timeRange?.endInclusive,
            query = query,
        )
    }

    companion object {
        private fun cryptoRegistry(): RecordCodecRegistry =
            RecordCodecRegistry().apply {
                register(CryptoRecordCodec.RECORD_TYPE, CryptoRecordCodec::decode)
                register(SqlRecordCodec.RECORD_TYPE, SqlRecordCodec::decode)
                register(BehaviorRecordCodec.RECORD_TYPE, BehaviorRecordCodec::decode)
            }

        private const val MAX_RECORDS = 20_000
        private const val PAGE_SIZE = 60

        /**
         * DAO paging 查询的 LIMIT 窗口上限(Room 2.7.2 LimitOffsetPagingSource
         * 契约):生成式计数 = COUNT(*) FROM (<原 SQL>),而原 SQL 自带 LIMIT 绑定
         * 会钳制 itemCount 至固定小 limit,Refresh 后 nextKey=null,分页死锁
         * 于第一页(V20 R3 设备测试实锤:120 条种子只显示 60 条)。外层实际
         * loadSize 由 Room 动态注入,此值仅作窗口上限(= MAX_RECORDS 即可)。
         */
        private const val PAGING_WINDOW_CAP = MAX_RECORDS

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
