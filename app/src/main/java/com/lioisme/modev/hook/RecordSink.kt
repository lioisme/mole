package com.lioisme.modev.hook

import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.RecordLimits
import com.lioisme.modev.core.model.contentWeight
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 记录出口:有界内存队列 + 异步工作线程,写前日志(WAL)保证不丢。
 *
 * - [emit] 在 Hook 线程内为记录分配进程级单调 [MonitoredRecord.seq] 并入队
 *   (有界 [CAPACITY],满时丢最旧并计数,防宿主进程内存膨胀);
 * - seq 单调且**跨进程重启保持递增**([seqFile] 持久化,重启续号):
 *   宿主侧以 `(packageName, processName, seq)` 唯一索引做幂等吸收,若进程重启后
 *   seq 重置,新记录会与库内旧记录撞号被 REPLACE 覆盖(内容不同则旧记录丢失);
 * - 工作线程:先 [RecordSpool.append] 落盘(seq\tjson),再尝试发送——先写后删的
 *   at-least-once 语义;发送/断连都不删,仅宿主 ACK 后才由 spool 裁剪;
 * - 断连时 [RecordSpool.resetSentCursor] 回退发送游标,重连后完整重放未确认行
 *   (宿主唯一索引幂等吸收重复);空闲期轮询退避(500ms→[MAX_IDLE_POLL_MS]),连接恢复后
 *   无需新记录即可自动回放积压;
 * - 无 spool 时(注入早于 Application,Context 不可用)降级为直发,失败丢弃计数;
 * - [onAck] 由 [ConfigChannel] 的 ACK 回调调用,推进 spool 已确认游标。
 */
class RecordSink(
    private val jsonOf: (MonitoredRecord) -> String,
    private val sendJson: (String) -> Boolean,
    private val isConnected: () -> Boolean,
    private val spoolProvider: () -> RecordSpool? = { null },
    private val seqFileProvider: () -> File? = { null },
) {

    private val queue = LinkedBlockingQueue<MonitoredRecord>(CAPACITY)
    private val dropped = AtomicLong()

    /** 行长超限丢弃计数(V45 T1.3,唯一裁决出口 [deliver] 递增)。 */
    private val oversized = AtomicLong()

    /**
     * 队列驻留字节估算(V44 T2.3 字节闸门):条数闸门([CAPACITY])无法约束
     * 「少条数 × 大字段」形态(1MB 字段 ≈ 数 MB 物化字符),旧形态最坏可驻留 GB 级
     * → 宿主 GC 风暴/OOM。主队列与溢出队列**共享**本预算,总驻留 ≤ [MAX_QUEUED_BYTES]。
     * 单条超预算时若队列为空仍放行(绝不因预算把一条合法记录饿死)。
     */
    private val queuedBytes = AtomicLong()

    /**
     * 溢出待落盘队列(V44 T2.3,审计 P6):主队列满/超字节预算时**不再**在 hook 线程
     * 同步 `jsonOf`(Gson 整条序列化含 MB 级字段)+ WAL 追加(@Synchronized 写盘 +
     * 周期 fsync)——该路径曾让宿主断连+记录风暴时主线程 stall 数十~百 ms。
     * 改为把记录对象转入本队列,序列化与落盘全在工作线程;本队列仍有界(条数 + 共享字节预算)。
     */
    private val overflow = LinkedBlockingQueue<MonitoredRecord>(OVERFLOW_CAPACITY)

    /**
     * seq 计数器。V38 修复:构造期求值 restoreSeq 在「注入早于 Application」场景下
     * applicationContext 恒 null → 文件不可见 → 每次进程重启 seq 从 1 重发,
     * 宿主 (packageName, processName, seq) 唯一索引 + REPLACE 静默覆盖上一代记录
     * (数据丢失)。改为首次发号时惰性续号:文件路径就绪前每次发号重试,
     * 已发号后不再回溯(极早期无 spool 记录直发路径,窗口可忽略)。
     */
    private val seqCounter = AtomicLong(0L)

    private val seqRestoreLock = java.util.concurrent.locks.ReentrantLock()

    @Volatile
    private var seqRestored = false

    @Volatile
    private var spool: RecordSpool? = null

    @Volatile
    private var seqFile: File? = null

    @Volatile
    private var running = false
    private var worker: Thread? = null

    private var lastSeqPersistMs = 0L

    /** 最近一次落盘的 seq(仅当检查点推进才重写文件,空闲零写——V43 T1)。 */
    @Volatile
    private var lastPersistedSeq = 0L

    /**
     * M14: Hook 线程仅写内存检查点,工作线程消费后周期落盘。
     * V45 T1.4:**max 语义**——并发发号下低号不得覆盖高号(旧形态普通 volatile 直赋值,
     * 线程 A 写 10 可覆盖线程 B 已写的 11 → 检查点回退 → 重启续号撞号 →
     * 宿主唯一索引 REPLACE 静默覆盖上一代记录,与 V38 seq bug 同一失效面)。
     */
    private val seqCheckpoint = java.util.concurrent.atomic.AtomicLong(0L)

    val droppedCount: Long get() = dropped.get()

    /**
     * 超出行长上限被丢弃的条数(V45 T1.3 / ST-3):与 [droppedCount] 分列,
     * 因为「配置上限与传输上限不自洽」是**架构不变量违背**,不是流量背压 ——
     * 混计会让 ST-3 类回归在观测面上伪装成正常丢弃。
     */
    val oversizedCount: Long get() = oversized.get()

    /**
     * 发号入口。V43 T1:续号优先由工作线程首帧完成(宿主 hook 线程零磁盘读);
     * 仅当 hook 先于 worker 首次恢复触发时走非阻塞兜底(tryLock 失败即暂用内存计数,
     * worker 恢复后以 max(持久值, 已发号) 续接,单调性不破坏)。
     */
    private fun nextSeq(): Long {
        if (!seqRestored) {
            if (seqRestoreLock.tryLock()) {
                try {
                    restoreSeqIfNeeded()
                } finally {
                    seqRestoreLock.unlock()
                }
            }
        }
        return seqCounter.incrementAndGet()
    }

    /** 锁内续号(worker 与兜底路径共用;调用方持有 seqRestoreLock)。 */
    private fun restoreSeqIfNeeded() {
        if (seqRestored) return
        // 注入早于 Application:路径未就绪即保持未恢复态,由后续调用重试
        if (currentSeqFile() == null) return
        val persisted = restoreSeq()
        // 并发发号可能先行(极早期直发路径):取 max 续接,单调性不破坏
        seqCounter.accumulateAndGet(persisted) { a, b -> maxOf(a, b) }
        seqRestored = true
    }

    fun start() {
        if (running) return
        running = true
        worker = Thread(::loop, "modev-record-sink").apply {
            isDaemon = true
            start()
        }
    }

    fun emit(record: MonitoredRecord) {
        if (!running) return
        val seq = nextSeq()
        // M14: 仅写入内存检查点(Hook 线程零 I/O);落盘由工作线程周期执行
        seqCheckpoint.accumulateAndGet(seq) { a, b -> maxOf(a, b) }
        val stamped = record.withSeq(seq)
        val weight = stamped.contentWeight
        // 双闸门(条数 + 共享字节预算):主队列优先,溢出队列兜底;
        // 两者皆拒 = 丢弃计数(绝不阻塞 hook 线程,绝不在 hook 线程序列化)
        if (reserve(weight)) {
            if (queue.offer(stamped) || overflow.offer(stamped)) return
            releaseReservation(weight)
        }
        dropped.incrementAndGet()
    }

    /**
     * 字节预算预留。返回 false = 超预算(拒绝入队)。
     * 空队列时恒放行——保证单条超大记录不会因预算被永久饿死。
     */
    private fun reserve(weight: Int): Boolean {
        while (true) {
            val current = queuedBytes.get()
            if (current > 0 && current + weight > MAX_QUEUED_BYTES) return false
            if (queuedBytes.compareAndSet(current, current + weight)) return true
        }
    }

    /** 预留回滚(入队失败时)。 */
    private fun releaseReservation(weight: Int) {
        queuedBytes.addAndGet(-weight.toLong())
    }

    /**
     * 紧急写入(旁路队列,同步落盘):供 System.exit / killProcess 的 before 回调使用。
     * V43 T1:不再同步 drain 发送——退出已被 setResult(null) 拦下,进程继续存活,
     * WAL 落盘即为送达性保证(worker 下轮排空;宿主不可达时重连回放,重启续号),
     * 宿主线程白付 socket 写(及背压锁链)的旧语义废除。
     */
    fun emitUrgent(record: MonitoredRecord) {
        if (!running) return
        val seq = nextSeq()
        seqCheckpoint.accumulateAndGet(seq) { a, b -> maxOf(a, b) }
        val r = record.withSeq(seq)
        try {
            val sp = currentSpool()
            if (sp != null) {
                sp.append(seq, jsonOf(r))
            } else {
                // 无 spool(注入极早期):唯一不落盘即不失的路径是直发,保留
                sendJson(jsonOf(r))
            }
        } catch (_: Throwable) { dropped.incrementAndGet() }
    }

    /** 宿主 ACK 回调(ConfigChannel 读循环线程):推进 spool 已确认游标并裁剪。 */
    fun onAck(seq: Long) {
        spool?.onAck(seq)
    }

    /**
     * 重启恢复:上次持久化的 seq(进程重启续号,避免与库内旧记录撞号)。
     * V45 T1.4:seq 文件读不出时**不回落 0**(旧形态会以低号重发 → 唯一索引 REPLACE
     * 静默覆盖上一代记录),改取 spool(含 `.old`)内已见最大 seq 作为续号下界,
     * 并告警留证。
     */
    private fun restoreSeq(): Long {
        val file = currentSeqFile() ?: return 0L
        val persisted = runCatching { file.readText().trim().toLongOrNull() }.getOrNull()
        if (persisted != null) return persisted
        val floor = runCatching { currentSpool()?.maxSeqSeen() ?: 0L }.getOrDefault(0L)
        if (floor > 0L) {
            android.util.Log.w("Modev", "seq checkpoint unreadable, continuing from spool floor=$floor")
        }
        return floor
    }

    /** 批量持久化 seq 检查点:仅当 seq 推进(> 上次落盘值)且命中条数/时间窗口。 */
    private fun persistSeqCheckpointIfDue() {
        val checkpoint = seqCheckpoint.get()
        if (checkpoint == 0L || checkpoint <= lastPersistedSeq) return
        if (checkpoint % SEQ_PERSIST_EVERY != 0L && System.currentTimeMillis() - lastSeqPersistMs < SEQ_PERSIST_INTERVAL_MS) return
        val file = currentSeqFile() ?: return
        lastSeqPersistMs = System.currentTimeMillis()
        runCatching { writeSeqAtomically(file, checkpoint) }.onSuccess { lastPersistedSeq = checkpoint }
    }

    /**
     * seq 检查点原子写(V45 T1.4,与 [RecordSpool] 的 temp+fsync+rename 同一原语):
     * 旧形态 `file.writeText` 截断后写、无 fsync —— 崩溃即留下半空文件,
     * 下次启动读不出 → 续号基线丢失 → 撞号覆盖。rename 保证读者只见新旧两态之一。
     */
    private fun writeSeqAtomically(file: java.io.File, value: Long) {
        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        try {
            java.io.FileOutputStream(tmp).use { t ->
                t.write(value.toString().toByteArray(Charsets.UTF_8))
                t.flush()
                t.fd.sync()
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private fun loop() {
        var pollMs = IDLE_POLL_MS
        while (running) {
            // V43 T1:续号优先在 worker 首帧完成(典型场景 hook 触发时 seqRestored 已定档,
            // 宿主 hook 线程零磁盘读;文件未就绪则随轮询重试,与 V38 懒重试语义一致)
            if (!seqRestored && seqRestoreLock.tryLock()) {
                try {
                    restoreSeqIfNeeded()
                } finally {
                    seqRestoreLock.unlock()
                }
            }
            val record = queue.poll(pollMs, TimeUnit.MILLISECONDS)
            if (record != null) releaseReservation(record.contentWeight)
            try {
                val sp = currentSpool()
                if (record != null) deliver(sp, record)
                if (sp != null) {
                    if (!isConnected()) {
                        // 断连:回退发送游标,积压保留在 WAL,重连后重放
                        sp.resetSentCursor()
                    } else {
                        sp.drain(sendJson)
                    }
                }
                // 溢出队列同管线排空(V44 T2.3:序列化与 WAL 追加全在本线程)
                drainOverflow(sp)
            } catch (t: Throwable) {
                dropped.incrementAndGet()
            }
            // M14: seq 检查点落盘在工作线程(与 WAL 写同一线程,天然串行化文件 I/O)
            persistSeqCheckpointIfDue()
            // 空闲指数退避(500ms→8s),收到记录即回基线;降空闲轮询与 provider 重试开销
            pollMs = if (record == null && overflow.isEmpty()) {
                (pollMs * 2).coerceAtMost(MAX_IDLE_POLL_MS)
            } else {
                IDLE_POLL_MS
            }
        }
    }

    /**
     * 唯一投递出口(V45 T1.3 / ST-3):序列化 + **行长裁决** → 直发或 WAL。
     *
     * 旧形态两条通路命运不同:直发不检行长(宿主按「损坏帧」丢弃并可能拖垮解析),
     * WAL 则把超长行判为损坏行**永久丢弃且每次判废触发整文件重写**;同一条记录
     * 是否存活取决于时序 —— 不可复现的丢数。现统一在本出口裁决:
     * 超限 → 计数丢弃(`oversizedCount`,并入 [droppedCount] 语义)且**绝不落 WAL**;
     * 合法配置是否会产出超限行由 `RecordLimitsInvariantTest` 的数值不变量看门。
     */
    private fun deliver(sp: RecordSpool?, record: MonitoredRecord) {
        val json = jsonOf(record)
        if (json.length > RecordLimits.WIRE_MAX_LINE_CHARS) {
            oversized.incrementAndGet()
            android.util.Log.w(
                "Modev",
                "record seq=${record.seq} exceeds line cap ${RecordLimits.WIRE_MAX_LINE_CHARS} (${json.length}), dropped",
            )
            return
        }
        if (sp == null) {
            // 无 spool(注入早于 Application):直发,失败丢弃并计数
            if (!sendJson(json)) dropped.incrementAndGet()
        } else {
            // 先落盘再发送:写入失败也不阻塞 Hook 线程(记录由统计暴露)
            sp.append(record.seq, json)
        }
    }

    /**
     * 排空溢出队列:记录对象已在内存,hook 线程零序列化;
     * 本线程统一执行序列化与投递(与主队列同一裁决出口)。
     */
    private fun drainOverflow(sp: RecordSpool?) {
        while (true) {
            val extra = overflow.poll() ?: return
            releaseReservation(extra.contentWeight)
            try {
                deliver(sp, extra)
            } catch (_: Throwable) {
                dropped.incrementAndGet()
            }
        }
    }

        /** 懒获取 spool:注入早于 Application 时 provider 返回 null,每次调用重试(成功后缓存)。 */
    private fun currentSpool(): RecordSpool? {
        spool?.let { return it }
        return runCatching { spoolProvider() }.getOrNull()?.also { spool = it }
    }

    /** 懒获取 seq 文件:与 spool 同策略(路径不可用即重试,成功后缓存)。 */
    private fun currentSeqFile(): File? {
        seqFile?.let { return it }
        return runCatching { seqFileProvider() }.getOrNull()?.also { seqFile = it }
    }

    companion object {
        private const val CAPACITY = 2048

        /**
         * 队列驻留字节预算:主队列 + 溢出队列共享。
         * 32MB 足以吸收记录风暴的突发(worker 持续落盘,稳态远低于此),
         * 又对「大字段 × 高并发」形成硬上界——宿主内存永不因监听队列失控。
         */
        private const val MAX_QUEUED_BYTES = 32L * 1024 * 1024

        /**
         * 溢出队列条数上限:**突发缓冲**,不是稳态队列——主队列满后
         * 记录先在此暂存、由工作线程序列化并落盘,故容量须显著大于主队列,
         * 避免「突发即丢」(旧形态溢出走同步 WAL 追加、零丢失,本改造必须保持
         * 同等的零丢失语义,只把成本从 hook 线程移到工作线程)。
         * 内存上界由共享字节预算 [MAX_QUEUED_BYTES] 主控,本条数上限是
         * 「零内容小记录 × 极端突发」场景的最后保险丝。
         */
        private const val OVERFLOW_CAPACITY = 32_768

        /** 空闲轮询基线(V43 T1 起按 2^n 退避至 [MAX_IDLE_POLL_MS])。 */
        private const val IDLE_POLL_MS = 500L

        /** 空闲轮询退避上限:保连接恢复后积压最迟 8s 内开始回放。 */
        private const val MAX_IDLE_POLL_MS = 8_000L

        /** seq 持久化检查点:时间窗口。 */
        private const val SEQ_PERSIST_INTERVAL_MS = 1_000L

        /** seq 持久化检查点:条数窗口(崩溃丢失窗口 ≤ 两者取小)。 */
        private const val SEQ_PERSIST_EVERY = 100L
    }
}
