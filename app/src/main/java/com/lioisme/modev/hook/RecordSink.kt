package com.lioisme.modev.hook

import com.lioisme.modev.core.model.MonitoredRecord
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
 *   (宿主唯一索引幂等吸收重复);空闲期每 [IDLE_POLL_MS] 醒来一次,连接恢复后
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
    private val seqCounter = AtomicLong(restoreSeq())

    @Volatile
    private var spool: RecordSpool? = null

    @Volatile
    private var seqFile: File? = null

    @Volatile
    private var running = false
    private var worker: Thread? = null

    private var lastSeqPersistMs = 0L

    /** M14: Hook 线程仅写内存检查点(volatile),工作线程消费后周期落盘。 */
    @Volatile
    private var seqCheckpoint = 0L

    val droppedCount: Long get() = dropped.get()

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
        val seq = seqCounter.incrementAndGet()
        // M14: 仅写入内存检查点(Hook 线程零 I/O);落盘由工作线程周期执行
        seqCheckpoint = seq
        if (!queue.offer(record.withSeq(seq))) {
            // 队列满:不阻塞 Hook 线程——直接落盘(WAL 追加,低成本),由工作线程 drain 发送;
            // 仅当 spool 尚未就绪(注入极早期)才计入丢弃
            val sp = currentSpool()
            if (sp != null) {
                // 直落盘路径同样必须携带已分配 seq,否则载荷内 seq 为 0,
                // 宿主唯一索引/ACK 游标与行前缀序号错位(前缀被剥除,宿主只见载荷 seq)
                sp.append(seq, jsonOf(record.withSeq(seq)))
            } else {
                dropped.incrementAndGet()
            }
        }
    }

    /**
     * 紧急写入(旁路队列,同步落盘+发送):供 System.exit / killProcess 的 before 回调使用。
     * 不经过 queue,直接写入 spool 并 drain 发送,确保进程退出前记录已持久化/发送。
     */
    fun emitUrgent(record: MonitoredRecord) {
        if (!running) return
        val seq = seqCounter.incrementAndGet()
        seqCheckpoint = seq
        val r = record.withSeq(seq)
        try {
            val sp = currentSpool()
            if (sp != null) {
                sp.append(seq, jsonOf(r))
                if (isConnected()) {
                    sp.drain(sendJson)
                }
            } else {
                sendJson(jsonOf(r))
            }
        } catch (_: Throwable) { dropped.incrementAndGet() }
    }

    /** 宿主 ACK 回调(ConfigChannel 读循环线程):推进 spool 已确认游标并裁剪。 */
    fun onAck(seq: Long) {
        spool?.onAck(seq)
    }

    /**
     * 同步冲洗队列:将内存队列中所有待发记录落盘并发送(阻塞调用线程直到队列清空)。
     * 供退出拦截等「即将死亡」场景使用,确保 before 回调产生的记录不因进程退出而丢失。
     */
    fun flush() {
        while (true) {
            val record = queue.poll() ?: break
            try {
                val sp = currentSpool()
                if (sp == null) {
                    sendJson(jsonOf(record))
                } else {
                    sp.append(record.seq, jsonOf(record))
                    if (isConnected()) {
                        sp.drain(sendJson)
                    }
                }
            } catch (_: Throwable) { /* best effort */ }
        }
    }

    /** 重启恢复:上次持久化的 seq(进程重启续号,避免与库内旧记录撞号)。 */
    private fun restoreSeq(): Long {
        val file = currentSeqFile() ?: return 0L
        return runCatching { file.readText().trim().toLong() }.getOrNull() ?: 0L
    }

    /** 批量持久化 seq 检查点:每 [SEQ_PERSIST_INTERVAL_MS] 或 [SEQ_PERSIST_EVERY] 条一次。 */
    private fun persistSeqCheckpointIfDue() {
        val checkpoint = seqCheckpoint
        if (checkpoint == 0L) return
        if (checkpoint % SEQ_PERSIST_EVERY != 0L && System.currentTimeMillis() - lastSeqPersistMs < SEQ_PERSIST_INTERVAL_MS) return
        lastSeqPersistMs = System.currentTimeMillis()
        val file = currentSeqFile() ?: return
        runCatching { file.writeText(checkpoint.toString()) }
    }

    private fun loop() {
        while (running) {
            val record = queue.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS)
            try {
                val sp = currentSpool()
                if (sp == null) {
                    // 无 spool(注入早于 Application):直发,失败丢弃并计数
                    if (record != null && !sendJson(jsonOf(record))) {
                        dropped.incrementAndGet()
                    }
                } else {
                    if (record != null) {
                        // 先落盘再发送:写入失败也不阻塞 Hook 线程(记录由统计暴露)
                        sp.append(record.seq, jsonOf(record))
                    }
                    if (!isConnected()) {
                        // 断连:回退发送游标,积压保留在 WAL,重连后重放
                        sp.resetSentCursor()
                    } else {
                        sp.drain(sendJson)
                    }
                }
            } catch (t: Throwable) {
                dropped.incrementAndGet()
            }
            // M14: seq 检查点落盘在工作线程(与 WAL 写同一线程,天然串行化文件 I/O)
            persistSeqCheckpointIfDue()
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
        private const val SPOOL_RETRY_MS = 10_000L
        private const val IDLE_POLL_MS = 500L

        /** seq 持久化检查点:时间窗口。 */
        private const val SEQ_PERSIST_INTERVAL_MS = 1_000L

        /** seq 持久化检查点:条数窗口(崩溃丢失窗口 ≤ 两者取小)。 */
        private const val SEQ_PERSIST_EVERY = 100L
    }
}