package com.lioisme.modev.hook

import com.lioisme.modev.core.model.RecordLimits
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 注入侧记录写前日志(WAL):宿主不可达/未确认期间把记录落盘(目标应用 files 目录,
 * 按进程名分目录,多进程互不干扰),宿主 ACK 后按游标删除,重连回放未确认行。
 *
 * - 行格式 `seq\tjson`:seq 为注入侧进程级单调序号,ACK 与幂等去重的依据;
 * - [append] 先落盘再发送(先写后删的 at-least-once 语义):持续打开的输出流 + flush,
 *   每 [FSYNC_EVERY] 条或 [FSYNC_INTERVAL_MS] 一次 `fd.sync()`(崩溃持久性上限 ≤ 一批);
 * - 双游标:[ackedSeq](宿主已确认,仅增)+ [sentSeq](已发送);[drain] 发送全部
 *   `seq > ackedSeq` 的行(乱序文件安全:低 seq 行不会被已发送高 seq 行挡住),
 *   重复发送由宿主唯一索引幂等吸收,重复量受 ACK 裁剪收敛;断连时 [resetSentCursor]
 *   将 sentSeq 回退到 ackedSeq,重连后完整重放未确认行(宿主唯一索引幂等吸收重复);
 * - [onAck] 推进 ackedSeq 并原子重写文件(临时文件 fsync 后 rename),删除已确认前缀;
 * - 超 [maxBytes] 轮转:当前文件先 fsync 再改名 `.old`,旧文件删除(丢最老数据,计数上报);
 * - 损坏/超长行跳过并计数(仅当发现损坏行时重写清理,避免计数膨胀);
 * - 仅由 [RecordSink] 工作线程与 [ConfigChannel] ACK 回调调用;游标/文件操作同步,
 *   drain 网络发送在锁外(V43 T1,防背压锁链)。
 */
class RecordSpool(
    private val dir: File,
    sub: String = "",
    private val maxBytes: Long = MAX_BYTES,
) {

    private val root = if (sub.isBlank()) dir else File(dir, sub.replace(":", "_"))

    private val spoolFile = File(root, "spool.dat")
    private val oldFile = File(root, "spool.dat.old")

    /** 宿主已确认游标(ACK 单调推进)。 */
    private var ackedSeq = -1L

    /** 已发送游标;断连时回退到 [ackedSeq] 以允许重放。 */
    private var sentSeq = -1L

    private var out: FileOutputStream? = null
    private var writtenSinceSync = 0
    private var lastSyncMs = 0L

    /** 回放时跳过/丢弃的损坏行计数(超长行)。 */
    var corruptLines: Long = 0L
        private set

    /** 轮转丢弃的最老行计数(并入丢记录统计)。 */
    var spoolDropped: Long = 0L
        private set

    /** 是否存在待回放数据(含轮转文件)。 */
    fun hasPending(): Boolean = spoolFile.exists() || oldFile.exists()

    /** 追加一行记录(seq + JSON);超限先轮转。 */
    @Synchronized
    fun append(seq: Long, json: String) {
        if (!root.exists() && !root.mkdirs()) return
        if (spoolFile.length() > maxBytes) rotate()
        try {
            val s = openOut()
            s.write(seq.toString().toByteArray(Charsets.UTF_8))
            s.write('\t'.code)
            s.write(json.toByteArray(Charsets.UTF_8))
            s.write('\n'.code)
            s.flush()
            maybeSync()
        } catch (t: Throwable) {
            // 落盘失败静默:记录优先级低于注入进程稳定性,丢记录由统计暴露
            closeOut()
        }
    }

    /** 宿主确认游标推进:删除 ≤ seq 的已确认前缀(原子重写)。 */
    @Synchronized
    fun onAck(seq: Long) {
        if (seq <= ackedSeq) return
        ackedSeq = seq
        trimConfirmed(spoolFile)
        trimConfirmed(oldFile)
    }

    /** 断连:回退发送游标,重连后重放未确认行。 */
    @Synchronized
    fun resetSentCursor() {
        sentSeq = ackedSeq
    }

    /**
     * 按序发送全部未确认行(`seq > ackedSeq`),失败保留待下次。
     * **V43 T1 两段式**:锁内仅做行快照(`readLines` 读盘),网络 `send` 在锁外执行——
     * 旧实现 `@Synchronized drain` 把 socket 写包进监视器,队列满降级路径的
     * [append](同锁)会被网络阻塞无限期拖住,宿主 hook 线程随之卡死(背压锁链根因之一)。
     * 快照后新落盘的行留待下一轮 drain——乱序文件(队列满直落盘与工作线程消费交错)中
     * 低 seq 行可能位于高 seq 行之后,故统一按 ackedSeq 判定仅跳已确认行,其余重发
     * (宿主唯一索引幂等吸收,重复量受 ACK 裁剪收敛),重放正确性与顺序无关。
     */
    fun drain(send: (String) -> Boolean) {
        val batch = snapshotPending()
        for ((seq, line) in batch) {
            // 发送纯 JSON 部分(剥掉 seq 前缀;无前缀旧行原样发送)
            val json = line.substringAfter('\t')
            if (!send(json)) return
            markSent(seq)
        }
    }

    private fun markSent(seq: Long) {
        synchronized(this) { sentSeq = seq }
    }

    /** 锁内快照未确认行(有界 [MAX_DRAIN_BATCH],余量下轮重放);损坏行就地计数并清理。 */
    @Synchronized
    internal fun snapshotPending(): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>(MAX_DRAIN_BATCH)
        collectPending(oldFile, out)
        if (out.size < MAX_DRAIN_BATCH) collectPending(spoolFile, out)
        return out
    }

    private fun collectPending(f: File, into: MutableList<Pair<Long, String>>) {
        if (!f.exists()) return
        var sawCorrupt = false
        readLines(f).forEach { (seq, line) ->
            when {
                seq <= ackedSeq || line.length > MAX_LINE_CHARS ->
                    if (line.length > MAX_LINE_CHARS) sawCorrupt = true
                into.size < MAX_DRAIN_BATCH -> into.add(seq to line)
            }
        }
        if (sawCorrupt) {
            corruptLines++
            // 清理损坏行,避免重复计数与永久滞留
            rewriteFile(f, readLines(f).filter { (s, line) -> line.length <= MAX_LINE_CHARS }.map { it.second })
        }
    }

    /**
     * 删除已确认行:乱序文件中低 seq 行可能位于高 seq 行之后,前缀裁剪(原 dropWhile)
     * 无法移除,导致已确认行永久滞留。改为过滤全部 `seq <= ackedSeq` 的行:
     * ACK 仅由宿主在批量落库成功后按"会话内最大 seq"回执,ackedSeq 单调推进且
     * 恒 ≤ 已成功发送的最大 seq(宿主只确认收到的记录),故被裁行必然已持久化,
     * 不违反 at-least-once 语义。
     */
    private fun trimConfirmed(f: File) {
        if (!f.exists()) return
        val lines = readLines(f)
        val keep = lines.filter { it.first > ackedSeq }
        if (keep.size == lines.size) return
        rewriteFile(f, keep.map { it.second })
    }

    /** 原子重写:临时文件 fsync 后 rename 替换(POSIX rename 原子);失败保留原文件不丢数据。 */
    private fun rewriteFile(f: File, lines: List<String>) {
        closeOut()
        val tmp = File(root, f.name + ".tmp")
        try {
            if (lines.isEmpty()) {
                tmp.delete()
                f.delete()
                return
            }
            FileOutputStream(tmp).use { t ->
                for (line in lines) {
                    t.write(line.toByteArray(Charsets.UTF_8))
                    t.write('\n'.code)
                }
                t.flush()
                t.fd.sync()
            }
            if (!tmp.renameTo(f)) {
                // rename 失败(罕见):退化为删除后改名
                f.delete()
                if (!tmp.renameTo(f)) tmp.delete()
            }
        } catch (t: Throwable) {
            // 重写失败:保留原文件,下次 ACK 再试
            tmp.delete()
        }
    }

    /** 轮转:先 fsync 当前文件,再删除最老的 `.old` 并统计丢弃行数。 */
    private fun rotate() {
        closeOut()
        try {
            FileOutputStream(spoolFile, true).use { it.fd.sync() }
        } catch (ignored: Throwable) {
        }
        spoolDropped += countLines(oldFile)
        oldFile.delete()
        spoolFile.renameTo(oldFile)
    }

    private fun openOut(): FileOutputStream {
        out?.let { return it }
        return FileOutputStream(spoolFile, true).also { out = it }
    }

    private fun closeOut() {
        runCatching { out?.flush() }
        runCatching { out?.close() }
        out = null
    }

    /** 批量 fsync checkpoint:每 [FSYNC_EVERY] 条或 [FSYNC_INTERVAL_MS] 一次。 */
    private fun maybeSync() {
        writtenSinceSync++
        val now = System.currentTimeMillis()
        if (writtenSinceSync < FSYNC_EVERY && now - lastSyncMs < FSYNC_INTERVAL_MS) return
        writtenSinceSync = 0
        lastSyncMs = now
        runCatching { out?.fd?.sync() }
    }

    /**
     * spool(含 `.old`)内已见最大 seq —— V45 T1.4:seq 检查点文件读不出时的**续号下界**。
     * 流式扫描行前缀,不物化行内容(仅启动期一次性调用)。
     */
    fun maxSeqSeen(): Long = maxOf(scanMaxSeq(spoolFile), scanMaxSeq(oldFile))

    private fun scanMaxSeq(f: File): Long {
        var max = 0L
        runCatching {
            FileInputStream(f).bufferedReader(Charsets.UTF_8).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val tab = line.indexOf('\t')
                    if (tab > 0) {
                        val seq = line.substring(0, tab).toLongOrNull()
                        if (seq != null && seq > max) max = seq
                    }
                    line = reader.readLine()
                }
            }
        }
        return max
    }

    /** 按行读取并解析 seq 前缀;缺前缀/非法(旧格式)的行按 seq=0 处理(发送后随 ACK(0) 清理)。 */
    private fun readLines(f: File): List<Pair<Long, String>> {
        val lines = ArrayList<Pair<Long, String>>()
        try {
            FileInputStream(f).bufferedReader(Charsets.UTF_8).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        val tab = line.indexOf('\t')
                        val seq = if (tab > 0) {
                            line.substring(0, tab).toLongOrNull() ?: 0L
                        } else {
                            0L
                        }
                        lines.add(seq to line)
                    }
                    line = reader.readLine()
                }
            }
        } catch (ignored: Throwable) {
        }
        return lines
    }

    private fun countLines(f: File): Long {
        if (!f.exists()) return 0L
        var count = 0L
        try {
            FileInputStream(f).bufferedReader(Charsets.UTF_8).use { reader ->
                while (reader.readLine() != null) count++
            }
        } catch (ignored: Throwable) {
        }
        return count
    }

    companion object {
        const val MAX_BYTES = 5L * 1024 * 1024

        /** 单行 JSON 上限(字符):全量捕获单条记录的理论上限,超限按损坏行处理。 */
        const val MAX_LINE_CHARS = RecordLimits.WIRE_MAX_LINE_CHARS

        /** 批量 fsync checkpoint:条数窗口。 */
        const val FSYNC_EVERY = 100

        /** 批量 fsync checkpoint:时间窗口。 */
        const val FSYNC_INTERVAL_MS = 500L

        /** 单次 drain 快照行上限(有界内存,V43 T1;余量下一轮重放)。 */
        const val MAX_DRAIN_BATCH = 2048
    }
}
