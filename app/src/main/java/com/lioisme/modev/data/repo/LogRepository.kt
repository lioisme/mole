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
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.codec.BehaviorRecordCodec
import com.lioisme.modev.core.codec.PrefsRecordCodec
import com.lioisme.modev.core.codec.WebViewRecordCodec
import com.lioisme.modev.core.codec.NetworkRecordCodec
import com.lioisme.modev.core.codec.DeviceEnvRecordCodec
import com.lioisme.modev.core.codec.UiInteractionRecordCodec
import com.lioisme.modev.core.codec.SystemRecordCodec
import com.lioisme.modev.core.codec.ProcessRecordCodec
import com.lioisme.modev.data.db.CryptoRecordDao
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.data.db.RecordQueryFactory
import com.lioisme.modev.data.mapping.rawJson
import com.lioisme.modev.data.mapping.toRecordDetailOrNull
import com.lioisme.modev.data.mapping.toRowModelOrNull
import com.lioisme.modev.data.mapping.writeEntity
import com.lioisme.modev.domain.record.RecordDetailModel
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.hasConstraint
import com.lioisme.modev.domain.record.RecordPage
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.record.RecordTypeCount
import com.lioisme.modev.domain.record.RowModel

import com.lioisme.modev.domain.server.ChannelStats
import com.lioisme.modev.domain.diagnostics.PipelineStats
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
 * 读写门面:实现 [RecordService],所有查询/增删改查经其暴露,
 * DAO 不对外暴露,UI 与未来 MCP 共用同一契约。
 *
 * 接线:显式 [start](宿主 AppContainer 启动时调用),不依赖构造副作用。
 */
class LogRepository(
    private val dao: CryptoRecordDao,
    private val server: ConfigServer,
    private val registry: RecordCodecRegistry = cryptoRegistry(),
    private val maxRecords: Int = MAX_RECORDS,
    /**
     * 在途载荷预算(V45 T1.6;可注入以便单测以小预算验证闸门语义,
     * 生产装配恒取 [PENDING_MAX_PAYLOAD_CHARS])。
     */
    private val maxPendingPayloadChars: Int = PENDING_MAX_PAYLOAD_CHARS,
    /** 主库文件提供者(V23 存储占用统计;未装配时 [storageUsage] 返回 0)。 */
    private val databaseFile: (() -> java.io.File)? = null,
    /**
     * 库文件治理出口(V45 T1.7 / ST-8):大批量删除后回收 freelist 页 + 收敛 wal。
     * 由装配层注入 `ModevDatabase` 上的 PRAGMA 出口(仓库不拼 PRAGMA、不直触库单例);
     * null = 测试/未装配(内存库无文件可收缩)。
     */
    private val maintenance: (() -> Unit)? = null,
    /**
     * 落库异常治理判定(V45 T1.7 / ST-9):返回 true = 该错误属库损坏,写路径早停
     * 并由治理面排期下次打开重建。装配层注入 `ModevDatabase::noteSqliteError`;
     * 默认恒 false = 纯测试/未装配形态(重试语义与 V44 一致)。
     */
    private val onDbError: (Throwable) -> Boolean = { false },
) : RecordService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    /** 待入库记录通道项:信封 + 其所属会话的 ACK 回调 + 入队时定档的在途载荷字符数。 */
    private data class Pending(
        val wire: WireRecord,
        val ack: (Long) -> Unit,
        val chars: Int,
    )

    private val pending = Channel<Pending>(PENDING_CAPACITY)

    /**
     * 在途载荷字符计(V45 T1.6 / ST-5):通道仅有**条数**上限时,8192 槽 ×
     * wire 单行上限 4M 字符 = 数 GB 级驻留(宿主 GC 风暴/OOM 的直接来源,
     * 与注入侧 V44 T2.3 出口字节预算同口径治理)。入队即定档、出队即释放,
     * 超预算的记录当场拒收并计 [PipelineStats.bytesDropped]。
     */
    private val pendingChars = java.util.concurrent.atomic.AtomicLong(0L)

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

    /** 捕获管线统计契约成员(V45 ML-2:原契约外 `statsFlow`,UI 与 MCP 曾为此注入本具体类)。 */
    override val stats: StateFlow<PipelineStats> = _stats

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
        // 载荷预算闸门:两类拒绝语义不同 ——
        // ① 结构性超大(单条即超整条预算):永不可容,必须回执,
        //    否则注入侧 WAL 从该游标永久重放(死循环 + 记录永不到达);
        // ② 在途占满(本条可容,只是当下排队已满):不回执,让 WAL 稍后重放,
        //    与条数闸门同一「反压可恢复」语义。
        val chars = payloadChars(record)
        if (chars > maxPendingPayloadChars) {
            pendingSlots.release()
            Log.w("Modev", "record exceeds payload budget, dropped for good seq=${record.seq} type=${record.recordType} chars=$chars budget=$maxPendingPayloadChars")
            bumpStats { it.copy(bytesDropped = it.bytesDropped + 1) }
            runCatching { ack(record.seq) }
            return
        }
        if (!reserveChars(chars)) {
            pendingSlots.release()
            Log.w("Modev", "pending payload budget full, dropped seq=${record.seq} type=${record.recordType} chars=$chars")
            bumpStats { it.copy(bytesDropped = it.bytesDropped + 1) }
            return
        }
        val sent = pending.trySend(Pending(record, ack, chars))
        if (sent.isFailure) {
            pendingSlots.release()
            pendingChars.addAndGet(-chars.toLong())
            bumpStats { it.copy(dropped = it.dropped + 1) }
        }
    }

    /** 在途载荷度量:已解析信封的 JSON 字符数(与记录实际驻留规模同阶,UTF-16 ×2 字节)。 */
    private fun payloadChars(record: WireRecord): Int =
        record.payload.toString().length.coerceAtLeast(1)

    /** 预算内则占用并返回 true;超预算零占用。 */
    private fun reserveChars(chars: Int): Boolean {
        while (true) {
            val current = pendingChars.get()
            if (current + chars > maxPendingPayloadChars) return false
            if (pendingChars.compareAndSet(current, current + chars)) return true
        }
    }

    /** 批量写入循环:收到首条后攒批(32 条或 200ms 窗口),Mutex 内批量落库 + ACK。 */
    private suspend fun writerLoop() {
        val batch = ArrayList<Pending>(BATCH_SIZE)
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
            // 出队即释放在途载荷预算(通道不再持有该记录)
            batch.forEach { item -> pendingChars.addAndGet(-item.chars.toLong()) }
            bumpStats { it.copy(received = it.received + batch.size) }
            writeMutex.withLock {
                writeBatch(batch)
            }
            maybeTrim(batch.size)
        }
    }

    private suspend fun writeBatch(batch: List<Pending>) {
        val toPersist = ArrayList<CryptoRecordEntity>(batch.size)
        val decodableAcks = ArrayList<Pair<Long, (Long) -> Unit>>(batch.size)
        val undecodableAcks = ArrayList<Pair<Long, (Long) -> Unit>>(batch.size)
        for (item in batch) {
            val wire = item.wire
            val ack = item.ack
            // 解码 → 落库一条分派(V45 T2.5:registry 值类型收口 MonitoredRecord 后,
            // 原 10 臂 when 仅做同一件事的重复登记,漏臂 = 静默丢档)
            val decoded = registry.decode(wire)
            if (decoded == null) {
                // 未知记录类型/畸形载荷:计数丢弃;ACK 单独回执(防注入侧 WAL 永久重放)
                undecodableAcks.add(wire.seq to ack)
                Log.w("Modev", "dropped undecodable record type=${wire.recordType} seq=${wire.seq}")
                bumpStats { it.copy(dropped = it.dropped + 1) }
                continue
            }
            decodableAcks.add(wire.seq to ack)
            toPersist.add(decoded.writeEntity())
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
                // V45 T1.7 / ST-9:损坏类错误交治理面判定(重放同一把坏库无意义,
                // 早停并把排期信号传出去),其余错误按原语义重试。
                if (onDbError(t)) {
                    Log.w("Modev", "persist aborted: database corruption reported")
                    return false
                }
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
            // V45 T1.6(ST-7):旧形态 `counts(null)` 每周期全库聚合 + `trim` 的
            // `id NOT IN (SELECT …)` 物化命中集;现改为「越限包探针 + keyset 截断点 +
            // 索引范围删除」——三步全部走 (packageName,timestamp) / (timestamp) 索引,
            // 全局侧更连「总数」都不必算(第 keep+1 行不存在即未超限)。
            var trimmedNow = 0L
            for (c in dao.packagesOver(PER_PACKAGE_MAX)) {
                val cutoff = dao.packageTrimCutoff(c.packageName, PER_PACKAGE_MAX) ?: continue
                trimmedNow += dao.deletePackageBefore(c.packageName, cutoff.timestamp, cutoff.id)
            }
            dao.globalTrimCutoff(maxRecords)?.let { cutoff ->
                trimmedNow += dao.deleteBefore(cutoff.timestamp, cutoff.id)
            }
            if (trimmedNow > 0) {
                bumpStats { it.copy(trimmed = it.trimmed + trimmedNow) }
                // V45 T1.7:裁剪释放的是「页数」而不是文件长度 —— 不收缩则存储占用恒高
                shrink()
            }
        } catch (t: Throwable) {
            Log.w("Modev", "trim failed: $t")
        }
    }

    /**
     * 大批量删除后的库文件治理(V45 T1.7 / ST-8):freelist 回收 + wal 检查点。
     * 失败静默(治理是尽力而为,绝不能让一次清理因为收缩失败而报错)。
     */
    private fun shrink() = runCatching { maintenance?.invoke() }

    // ----------------------------------------------------------------
    // RecordService 实现(读 / 写 / 删 / 清理 / 统计)
    // ----------------------------------------------------------------

    /**
     * 流式分页记录(Paging3):记录类型/类别/操作/关键字/时间范围过滤,排序方向随
     * [RecordFilter.sort]。V45 T1.1(ST-1):谓词由 [RecordQueryFactory] 派生
     * (缺省维度不出列 → SQLite 可选索引),取代旧 `@Query + WHERE_FILTER` 哨兵形态;
     * `limit` 仍取 [PAGING_WINDOW_CAP](AGENTS #14:Room 生成式计数会钳制 itemCount)。
     * 关键字在 SQL 侧匹配,不在内存过滤;列表走投影查询,仅物化展示列 + SQL 侧预览。
     * 枚举未知的行返回 null 丢弃(计数在 DAO 层不感知,防 Schema 漂移兜底)。
     */
    override fun observe(filter: RecordFilter): Flow<PagingData<RowModel>> {
        if (RecordQueryFactory.predicate(filter).noMatch) return flowOf(PagingData.empty())
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                dao.pagedRows(
                    RecordQueryFactory.rows(
                        filter = filter,
                        limit = PAGING_WINDOW_CAP,
                        offset = 0,
                        previewChars = ROW_PREVIEW_CHARS,
                    ),
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
        if (RecordQueryFactory.predicate(filter).noMatch) return RecordPage(emptyList(), 0, page, pageSize)
        val total = count(filter).first()
        if (total == 0L) return RecordPage(emptyList(), 0, page, pageSize)
        val safePage = page.coerceAtLeast(0)
        val safeSize = pageSize.coerceAtLeast(1)
        val offset = safePage * safeSize
        val rows: List<CryptoRecordRow> = withContext(Dispatchers.IO) {
            dao.sliceRows(
                RecordQueryFactory.rows(
                    filter = filter,
                    limit = safeSize,
                    offset = offset,
                    previewChars = ROW_PREVIEW_CHARS,
                ),
            )
        }
        val items = rows.mapNotNull { it.toRowModelOrNull() }
        return RecordPage(items = items, total = total, page = safePage, pageSize = safeSize)
    }

    /** 统一查询契约计数入口(V17 R2;REST/MCP 唯一依赖口)。 */
    override fun count(filter: RecordFilter): Flow<Long> {
        if (RecordQueryFactory.predicate(filter).noMatch) return flowOf(0L)
        return dao.countByQuery(RecordQueryFactory.count(filter))
    }

    /** 单条记录(记录详情源),记录被清理或不可解码后返回 null。 */
    override fun get(id: Long): Flow<RecordDetailModel?> =
        dao.observeById(id).map { it?.toRecordDetailOrNull() }

    /** 记录原始 JSON 导出(V45 ISO-2:序列化住在数据层,消费方经契约取用)。 */
    override fun exportJson(record: RecordDetailModel): String = record.rawJson()

    /**
     * 相邻记录 id(详情「上一条/下一条」),仅投影 id,不物化邻居大字段。
     * V45 ST-2:keyset 翻页谓词 = 同类型 + 同包 + `(timestamp, id)` 严格比较,
     * 与列表共享 [RecordQueryFactory] 生成器(旧形态另写一份哨兵 SQL,恒全表扫描)。
     */
    override suspend fun neighbor(id: Long, newer: Boolean): Long? {
        val rec = dao.observeById(id).first() ?: return null
        val filter = RecordFilter(
            recordTypes = setOf(rec.recordType),
            packages = setOf(rec.packageName),
        )
        return withContext(Dispatchers.IO) {
            dao.neighborIdByQuery(RecordQueryFactory.neighborId(filter, rec.timestamp, id, newer))
        }
    }

    /** 新增单条(MCP 导入 / 手动补录),类型化记录 → 实体唯一分派后落库,返回自增 id。 */
    override suspend fun create(record: MonitoredRecord): Long =
        dao.insertReturningId(record.writeEntity())

    /** 更新单条(按主键整行覆盖,MCP 编辑),id 不存在时 upsert 以 0 插入视为失败。 */
    override suspend fun update(id: Long, record: MonitoredRecord): Boolean {
        val entity = record.writeEntity().copy(id = id)
        dao.upsert(entity)
        // upsert 无返回值:回读主键确认目标行存在(不存在则以新 id 插入,删除语义下判失败)
        return dao.observeById(id).first() != null
    }

    /** 按 id 删除单条。 */
    override suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    /**
     * 按 [RecordFilter] 批量删除(候选 id 与 [count] 走同一 [RecordQueryFactory] 谓词
     * → 「删 = 筛」口径单一; `@RawQuery` 不做 DML,故先取 id 再按主键分片删)。
     * 全字段空 = 无约束,拒绝整表误删(显式整清请用 [clearAll])。
     */
    override suspend fun deleteByFilter(filter: RecordFilter): Long {
        if (RecordQueryFactory.predicate(filter).noMatch) return 0L
        if (!filter.hasConstraint) return 0L
        val ids = withContext(Dispatchers.IO) { dao.idsByQuery(RecordQueryFactory.ids(filter)) }
        if (ids.isEmpty()) return 0L
        var deleted = 0L
        // 主键批删分片(SQLite 变量数上限;命中集本身 ≤ 库容量,恒有界)
        ids.chunked(DELETE_ID_CHUNK).forEach { shard ->
            deleted += dao.deleteByIds(shard)
        }
        shrink()
        return deleted
    }

    /** 按包清理(V45 T3.5 后为「按包清空」唯一入口,MCP 与 UI 同上游)。 */
    override suspend fun clearPackage(packageName: String) {
        dao.clearPackage(packageName)
        shrink()
    }

    /** 按记录类型清理(V19 T7 数据管理按类型清空)。 */
    override suspend fun clearByType(recordType: String) {
        dao.clearByType(recordType)
        shrink()
    }

    override suspend fun clearAll() {
        dao.clearAll()
        shrink()
    }

    /**
     * 存储占用:主库 + `-wal` + `-shm` 三文件字节和,单一出口供
     * 数据管理页/诊断消费(UI 禁止直触 [com.lioisme.modev.data.db.ModevDatabase])。
     * 未装配 databaseFile 时返回 0。
     */
    override suspend fun storageUsage(): Long {
        val provider = databaseFile ?: return 0L
        return withContext(Dispatchers.IO) {
            val main = provider()
            listOf("", "-wal", "-shm").sumOf { suffix ->
                val file = java.io.File(main.parentFile, main.name + suffix)
                if (file.exists()) file.length() else 0L
            }
        }
    }

    /** 按记录类型分组计数(V17 R2,全类型总览基础)。 */
    override fun observeTypeCounts(): Flow<List<RecordTypeCount>> = dao.observeRecordTypeCounts()

    /**
     * 单包按类型分组计数:与 [count] 同一 [RecordQueryFactory] 谓词,
     * 一次 `GROUP BY` 出全部类型(旧形态 = 监控页/MCP 各发 10 次 `observeRecordCounts`)。
     */
    override fun observeTypeCountsForPackage(packageName: String): Flow<Map<String, Long>> =
        dao.typeCountsByQuery(
            RecordQueryFactory.typeCounts(RecordFilter(packages = setOf(packageName))),
        ).map { list -> list.associate { it.recordType to it.count } }

    /** 按记录类型分组的每应用记录计数(V44 契约成员:应用徽标 / 监控类型计数 / MCP 共用;null=全类型)。 */
    override fun observeRecordCounts(recordType: String?): Flow<Map<String, Long>> =
        dao.observeRecordCounts(recordType).map { list -> list.associate { it.packageName to it.count } }

    /** 指定类型每类别计数(筛选菜单徽标)。 */
    override suspend fun categoryCounts(type: RecordType): Map<String, Long> =
        dao.observeCategoryCounts(type.wire).first().associate { it.kind to it.count }

    // ----------------------------------------------------------------
    // V44 死面清理:兼容别名(paged/counts/observeById/observeRecordTypeCounts/
    // clear/neighborIdOf/pagedRecords/observePackages)已全部删除——UI/MCP 一律经
    // RecordService 契约方法(observe/count/get/neighbor/observeTypeCounts/
    // observeRecordCounts/clearPackage);消灭「一功能两名」漂移面。
    // ----------------------------------------------------------------

    // V45 T1.1(ST-1):旧 `likePattern` / `toBindList`(哨兵集合绑定)/ `Resolved` +
    // `resolve(...)`(WHERE_FILTER 绑定参数装配)四件套已随 RecordQueryFactory 落地删除——
    // 谓词、转义、空交集短路、绑定顺序全部收归生成器单一事实源,仓库不再感知 SQL 形态。

    companion object {
        private fun cryptoRegistry(): RecordCodecRegistry =
            RecordCodecRegistry().apply {
                register(CryptoRecordCodec.RECORD_TYPE, CryptoRecordCodec::decode)
                register(SqlRecordCodec.RECORD_TYPE, SqlRecordCodec::decode)
                register(BehaviorRecordCodec.RECORD_TYPE, BehaviorRecordCodec::decode)
                register(PrefsRecordCodec.RECORD_TYPE, PrefsRecordCodec::decode)
                register(WebViewRecordCodec.RECORD_TYPE, WebViewRecordCodec::decode)
                register(NetworkRecordCodec.RECORD_TYPE, NetworkRecordCodec::decode)
                register(DeviceEnvRecordCodec.RECORD_TYPE, DeviceEnvRecordCodec::decode)
                register(UiInteractionRecordCodec.RECORD_TYPE, UiInteractionRecordCodec::decode)
                register(SystemRecordCodec.RECORD_TYPE, SystemRecordCodec::decode)
                register(ProcessRecordCodec.RECORD_TYPE, ProcessRecordCodec::decode)
            }

        private const val MAX_RECORDS = 20_000
        /** 页大小 = [RecordService.DEFAULT_PAGE_SIZE] 契约单源(V44 Q4,禁再写字面量)。 */
        private const val PAGE_SIZE = RecordService.DEFAULT_PAGE_SIZE

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

        /**
         * 在途载荷预算(V45 T1.6 / ST-5,单位 = JSON 字符数,UTF-16 下 ×2 字节):
         * 条数闸门单独不构成内存上界 —— [PENDING_CAPACITY] × wire 单行硬上限
         * [RecordLimits.WIRE_MAX_LINE_CHARS](4M 字符)最坏可达数十 GB。
         * 取值口径:常态记录(1–4K 字符)满载 8192 槽 ≈ 8–32M 字符,故 32M
         * (≈64 MiB 驻留)在正常压力下**从不 binding**(闸门不改既有吞吐语义),
         * 只在「大字段洪峰」时先于 OOM 生效并计入 `bytesDropped`。
         */
        private const val PENDING_MAX_PAYLOAD_CHARS = 32 * 1024 * 1024

        /** 背压阻塞窗口:超时丢弃并计数,绝不无限挂起客户端线程。 */
        private const val ACQUIRE_TIMEOUT_MS = 10_000L

        /** 批量落库失败重试次数(仍失败不 ACK,注入侧 WAL 保留重放)。 */
        private const val MAX_INSERT_ATTEMPTS = 3

        /** 每插入这么多条执行一次双层容量治理。 */
        private const val TRIM_EVERY = 500L

        /**
         * 主键批删分片大小(V45 T1.1:`@RawQuery` 不做 DML,删除走「同谓词取 id →
         * `id IN (:ids)` 分片删」;分片上限保守取 500,兼容 SQLite 旧版 999 变量数限制)。
         */
        private const val DELETE_ID_CHUNK = 500
    }
}

