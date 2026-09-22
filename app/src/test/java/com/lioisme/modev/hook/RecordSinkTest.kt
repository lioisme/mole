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

    /**
     * V45 T1.4(ST-4/ST-5):seq 检查点文件损坏 **且 spool 仍有未确认行**时,
     * 不得回落 0 —— 否则以低号重发会被宿主 `(packageName,processName,seq)` 唯一索引
     * REPLACE 静默覆盖上一代记录(V38 同一失效面)。续号下界 = spool 内已见最大 seq。
     */
    @Test
    fun `corrupt seq file resumes above spool max seq`() {
        val seqFile = File(dir, "bad2.dat")
        seqFile.writeText("ÿgarbage")
        val spoolDir = Files.createTempDirectory("modev-spool").toFile()
        try {
            val spool = RecordSpool(spoolDir)
            spool.append(9_000L, """{"seq":9000}""")
            spool.append(9_001L, """{"seq":9001}""")
            val seen = java.util.concurrent.ConcurrentLinkedQueue<Long>()
            val sink = RecordSink(
                jsonOf = { rec -> seen.add(rec.seq); "{}" },
                sendJson = { true },
                isConnected = { false },
                spoolProvider = { spool },
                seqFileProvider = { seqFile },
            )
            sink.start()
            sink.emit(record("after-corruption"))
            val deadline = System.currentTimeMillis() + 5_000
            while (seen.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertEquals("必须从 spool 最大 seq 之上续号", listOf(9_002L), seen.toList())
        } finally {
            spoolDir.deleteRecursively()
        }
    }

    /** V45 T1.4:检查点落盘必须走 temp+fsync+rename —— 不留 `.tmp` 残渣(半写文件即丢基线)。 */
    @Test
    fun `seq checkpoint leaves no temp residue`() {
        val seqFile = File(dir, "atomic.dat")
        sinkWith(seqFile, emitCount = 300)
        awaitSeqFile(seqFile, 300L)
        assertTrue(
            "临时文件不得残留:" + dir.listFiles()?.joinToString(),
            File(dir, "atomic.dat.tmp").length() == 0L,
        )
        assertEquals(300L, seqFile.readText().trim().toLong())
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

    @Test
    fun `emitUrgent persists WAL without synchronous send on host thread`() {
        // V43 T1:emitUrgent(宿主 hook 线程所走)只落盘不发送——退出已被拦、进程存活,
        // 送达由 worker 异步 drain;socket 写不得出现在宿主线程(旧实现在此同步 drain)。
        val spool = RecordSpool(dir)
        val sendCount = java.util.concurrent.atomic.AtomicInteger(0)
        val connected = java.util.concurrent.atomic.AtomicBoolean(false)
        val sink = RecordSink(
            jsonOf = { "{}" },
            sendJson = { _ -> sendCount.incrementAndGet(); true },
            isConnected = { connected.get() },
            spoolProvider = { spool },
            seqFileProvider = { File(dir, "seq.dat") },
        )
        sink.start()
        sink.emitUrgent(record("exit-1"))
        val lines = File(dir, "spool.dat").readLines()
        assertTrue("emitUrgent 必须同步落盘", lines.any { it.startsWith("1\t") })
        assertEquals("emitUrgent 宿主线程零发送", 0, sendCount.get())
        // 连接恢复后由 worker 异步排空送达
        connected.set(true)
        val deadline = System.currentTimeMillis() + 10_000
        while (sendCount.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("worker 应在连接恢复后 drain 送达", sendCount.get() > 0)
    }

    @Test
    fun `seq checkpoint only rewritten when seq advanced`() {
        // V43 T1:空闲零重写——emit 推进后落盘一次;此后无新记录,文件不再被触碰
        val seqFile = File(dir, "ckpt.dat")
        val sink = RecordSink(
            jsonOf = { "{}" },
            sendJson = { true },
            isConnected = { false },
            spoolProvider = { null },
            seqFileProvider = { seqFile },
        )
        sink.start()
        sink.emit(record("first"))
        awaitSeqFile(seqFile, 1L)
        val stampBefore = seqFile.lastModified()
        val lengthBefore = seqFile.length()
        Thread.sleep(2_500) // 覆盖多个 1s 持久化窗口:无推进即零写(mtime/长度不变)
        assertEquals("seq 未推进不得重写文件", stampBefore, seqFile.lastModified())
        assertEquals(lengthBefore, seqFile.length())
    }

    // ---------------------------------------------------------------
    // V44 T2.3 内容捕获护栏:队列字节预算 + 溢出路径出钩线程
    // ---------------------------------------------------------------

    /**
     * 携带大字段的记录(单字段取**内容物化上限**,即三态视图仍在预算内生成,
     * 记录重量 ≈ 0.65MB 字符;V45 T1.3 起更大输入会走「保元弃内容」而重量极小,
     * 因此夹具必须取上限值而非任意大值)。
     */
    private fun bigRecord(algorithm: String, bytes: Int = com.lioisme.modev.core.model.RecordLimits.MAX_CONTENT_MATERIALIZE_BYTES) =
        record(algorithm).copy(
            input = com.lioisme.modev.core.util.BytesView.of(ByteArray(bytes) { (it % 251).toByte() }),
        )

    /**
     * 字节预算(V44 T2.3):条数闸门(2048)约束不了「少条数 × 大字段」,旧形态最坏
     * 可驻留 GB 级内存。以阻塞的发送端把工作线程钉住,连续投入大字段记录 →
     * 预算必然触发,拒绝入队并计入 dropped(绝不 OOM、绝不阻塞 hook 线程)。
     */
    @Test
    fun `byte budget rejects oversized residency and counts drops`() {
        val gate = java.util.concurrent.CountDownLatch(1)
        val sink = RecordSink(
            jsonOf = { "{}" },
            sendJson = {
                gate.await(5, java.util.concurrent.TimeUnit.SECONDS)
                true
            },
            isConnected = { false },
            spoolProvider = { null },
            seqFileProvider = { File(dir, "seq-budget.dat") },
        )
        sink.start()
        repeat(120) { sink.emit(bigRecord("big-$it")) }
        assertTrue(
            "超字节预算必须拒绝入队并计入 dropped(实测 dropped=${sink.droppedCount})",
            sink.droppedCount > 0,
        )
        gate.countDown()
    }

    /**
     * 溢出路径出钩线程(V44 T2.3,审计 P6):旧形态主队列满时在 **hook 线程**同步
     * `jsonOf`(Gson 整条序列化含 MB 级字段)+ WAL 追加 = 宿主主线程 stall。
     * 断言:全部序列化都发生在工作线程,发射线程零 `jsonOf`。
     */
    @Test
    fun `overflow path never serializes on the emitting thread`() {
        val emitThread = Thread.currentThread()
        val jsonThreads = java.util.concurrent.ConcurrentLinkedQueue<Thread>()
        val spool = RecordSpool(dir)
        val sink = RecordSink(
            jsonOf = { rec ->
                jsonThreads.add(Thread.currentThread())
                rec.seq.toString()
            },
            sendJson = { true },
            isConnected = { false },
            spoolProvider = { spool },
            seqFileProvider = { File(dir, "seq-overflow.dat") },
        )
        sink.start()
        // 远超主队列容量(2048):后续记录全部走溢出队列
        repeat(3000) { sink.emit(record("burst-$it")) }
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (File(dir, "spool.dat").readLines().size >= 3000) break
            Thread.sleep(50)
        }
        assertTrue("序列化必须发生过(否则断言无意义)", jsonThreads.isNotEmpty())
        assertTrue(
            "hook 线程不得执行 jsonOf(实测 ${jsonThreads.count { it == emitThread }} 次)",
            jsonThreads.none { it == emitThread },
        )
        assertEquals(
            "溢出路径同样零丢失:全部 3000 条应落盘",
            3000,
            File(dir, "spool.dat").readLines().size,
        )
    }
}