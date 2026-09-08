package com.lioisme.modev.hook

import com.lioisme.modev.core.model.CryptoRecord
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** RecordSink 出口测试:seq 分配、进程重启续号(seq 持久化)。 */
class RecordSinkTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("modev-sink").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun record(algorithm: String) = CryptoRecord(
        id = 0,
        packageName = "com.test",
        kind = com.lioisme.modev.core.model.CryptoKind.MESSAGE_DIGEST,
        operation = com.lioisme.modev.core.model.CryptoOperation.DIGEST,
        algorithm = algorithm,
        provider = null,
        direction = null,
        key = null,
        iv = null,
        keyAlias = null,
        keyExportable = true,
        input = null,
        output = null,
        salt = null,
        aad = null,
        publicKey = null,
        params = null,
        verifyResult = null,
        processName = "com.test",
        contextId = 1,
        threadName = "t",
        stack = emptyList(),
        timestamp = System.currentTimeMillis(),
        seq = 0,
    )

    private fun sinkWith(
        seqFile: File,
        emitCount: Int = 0,
        emit: (RecordSink, Int) -> Unit = { s, i -> s.emit(record("A-$i")) },
    ): RecordSink {
        val sink = RecordSink(
            jsonOf = { "{}" },
            sendJson = { true },
            isConnected = { false },
            spoolProvider = { null },
            seqFileProvider = { seqFile },
        )
        sink.start()
        repeat(emitCount) { emit(sink, it) }
        return sink
    }

    /**
     * 有界轮询等待 seq 检查点落盘为 [expected](异步工作线程写盘,固定 sleep 在
     * 并行 CI/重负载下会竞态;轮询直至断言成立或超时)。
     */
    private fun awaitSeqFile(file: File, expected: Long, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            runCatching { file.readText().trim().toLong() }
                .getOrNull()
                ?.takeIf { it == expected }
                ?.let { return }
            Thread.sleep(100)
        }
        val actual = runCatching { file.readText().trim().toLong() }.getOrNull()
        assertEquals("seq 检查点应在 $timeoutMs ms 内持久化为 $expected", expected, actual)
    }

    @Test
    fun `seq persists and resumes after process restart`() {
        val seqFile = File(dir, "seq.dat")
        val s1 = sinkWith(seqFile, emitCount = 300)
        awaitSeqFile(seqFile, 300L) // 等待持久化检查点(条数窗口 100 触发,第 300 条必已持久化)

        // 模拟进程重启:新实例从持久化值继续(续号,不撞库内旧记录)
        val s2 = sinkWith(seqFile)
        s2.emit(record("resumed"))
        awaitSeqFile(seqFile, 301L)
    }

    /**
     * V38 回归(注入早于 Application):构造期 seq 文件路径不可见(provider 暂返 null),
     * 首次发号时文件已可见 → 必须续号而非从 1 重发(旧实现构造期一次性求值,
     * 宿主唯一索引 REPLACE 会静默覆盖上一代记录)。
     */
    @Test
    fun `seq restores when file becomes visible after construction`() {
        val seqFile = File(dir, "late.dat")
        seqFile.writeText("41")
        var visible = false
        val seen = java.util.concurrent.ConcurrentLinkedQueue<Long>()
        val sink = RecordSink(
            jsonOf = { rec -> seen.add(rec.seq); "{}" },
            sendJson = { true },
            isConnected = { false },
            spoolProvider = { null },
            seqFileProvider = { if (visible) seqFile else null },
        )
        sink.start()
        visible = true
        sink.emit(record("late"))
        val deadline = System.currentTimeMillis() + 5_000
        while (seen.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(listOf(42L), seen.toList())
    }

    @Test
    fun `missing seq file starts at zero`() {
        val seqFile = File(dir, "absent.dat")
        sinkWith(seqFile, emitCount = 1)
        awaitSeqFile(seqFile, 1L)
    }

    @Test
    fun `corrupt seq file starts at zero`() {
        val seqFile = File(dir, "bad.dat")
        seqFile.writeText("not-a-number")
        sinkWith(seqFile, emitCount = 1)
        awaitSeqFile(seqFile, 1L)
    }

    @Test
    fun `queue-full direct append carries assigned seq in payload`() {
        val seqFile = File(dir, "seq.dat")
        val spool = RecordSpool(dir)
        val sink = RecordSink(
            jsonOf = { it.seq.toString() },
            sendJson = { true },
            isConnected = { false },
            spoolProvider = { spool },
            seqFileProvider = { seqFile },
        )
        sink.start()
        // 同步连发远超队列容量(2048)的记录:溢出部分走直落盘路径
        repeat(4000) { sink.emit(record("burst-$it")) }
        // 有界轮询等工作线程消费完(负载下固定 sleep 会竞态)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (File(dir, "spool.dat").readLines().size >= 4000) break
            Thread.sleep(100)
        }
        val lines = File(dir, "spool.dat").readLines()
        assertTrue("全部 4000 行应已落盘,实际 ${lines.size}", lines.size >= 4000)
        val bad = lines.filter { line ->
            val tab = line.indexOf('\t')
            tab > 0 && line.substring(0, tab) != line.substring(tab + 1)
        }
        assertTrue(
            "直落盘行载荷必须携带已分配 seq,发现 ${bad.size} 行不符",
            bad.isEmpty(),
        )
    }
}