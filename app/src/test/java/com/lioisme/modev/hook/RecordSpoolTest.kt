package com.lioisme.modev.hook

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** RecordSpool WAL 语义:seq 游标 / ACK 裁剪 / 断连回退重放 / 轮转 / 崩溃窗口(纯 JVM,文件系统)。 */
class RecordSpoolTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("modev-spool-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `drain sends un-acked lines and ack empties spool`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        spool.append(1, "r1")
        spool.append(2, "r2")
        spool.append(3, "r3")

        val first = mutableListOf<String>()
        spool.drain { first.add(it); true }
        assertEquals(listOf("r1", "r2", "r3"), first)

        // 未确认行重发(宿主唯一索引幂等吸收);确认后不再出现
        val second = mutableListOf<String>()
        spool.drain { second.add(it); true }
        assertEquals(listOf("r1", "r2", "r3"), second)
        assertTrue(spool.hasPending())

        spool.onAck(3)
        assertFalse(spool.hasPending())
        val third = mutableListOf<String>()
        spool.drain { third.add(it); true }
        assertTrue(third.isEmpty())
    }

    @Test
    fun `ack trims confirmed prefix and drain continues from next seq`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        spool.append(1, "r1")
        spool.append(2, "r2")
        spool.append(3, "r3")
        spool.drain { true }

        spool.onAck(2) // 宿主确认 r1/r2
        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        assertEquals(listOf("r3"), replayed) // r3 未确认,重发
        assertTrue(spool.hasPending())

        spool.onAck(3)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `reset cursor after disconnect replays all unacked lines`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        spool.append(1, "r1")
        spool.append(2, "r2")
        spool.append(3, "r3")
        spool.drain { true }
        spool.onAck(1) // 仅 r1 已确认
        spool.resetSentCursor() // 断连:回退游标,r2/r3 待重发

        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        assertEquals(listOf("r2", "r3"), replayed)
        assertTrue(spool.hasPending())

        spool.onAck(3)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `failed send retains lines and full replay happens after reset`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        spool.append(1, "r1")
        spool.append(2, "r2")
        spool.append(3, "r3")

        val replayed = mutableListOf<String>()
        spool.drain { line ->
            if (line == "r2") false else {
                replayed.add(line)
                true
            }
        }
        assertEquals(listOf("r1"), replayed)
        assertTrue(spool.hasPending())

        // 重试须先回退游标(断连语义);r1 未确认同样重发(宿主唯一索引幂等吸收)
        spool.resetSentCursor()
        val second = mutableListOf<String>()
        spool.drain { second.add(it); true }
        assertEquals(listOf("r1", "r2", "r3"), second)
        spool.onAck(3)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `drain replays FIFO across rotation`() {
        val spool = RecordSpool(dir, maxBytes = 30)
        for (i in 1..6) spool.append(i.toLong(), "r$i") // 6 行 ≈ 30B,不触发轮转
        spool.append(7, "r7") // 触发首次轮转:r1..r6 → .old
        for (i in 8..10) spool.append(i.toLong(), "r$i")

        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        assertEquals((1..10).map { "r$it" }, replayed)
        assertEquals(0L, spool.spoolDropped)
        spool.onAck(10)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `rotation drops oldest file when exceeding cap`() {
        val spool = RecordSpool(dir, maxBytes = 30)
        for (i in 1..30) spool.append(i.toLong(), "line-$i")

        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        // 多轮轮转:最老的段已被丢弃,剩余按写入顺序回放
        assertTrue(replayed.size < 30)
        assertTrue(replayed.sortedBy { it.removePrefix("line-").toInt() } == replayed)
        assertTrue(spool.spoolDropped > 0)
        spool.onAck(Long.MAX_VALUE)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `blank lines are skipped during drain`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        spool.append(1, "r1")
        // 直接写入空行(模拟损坏/中断残留)
        File(dir, "spool.dat").appendText("\n", Charsets.UTF_8)
        spool.append(2, "r2")

        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        assertEquals(listOf("r1", "r2"), replayed)
    }

    @Test
    fun `drain on empty spool is a no-op`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        var called = false
        spool.drain { called = true; true }
        assertFalse(called)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `overlong lines are skipped as corrupt counted and cleaned`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        spool.append(1, "good-line")
        // 写入一条带 seq 前缀的超长行(模拟半截/异常数据,超过 4MB 上限)
        File(dir, "spool.dat").appendText(
            "2\t" + "x".repeat(RecordSpool.MAX_LINE_CHARS + 10) + "\n",
            Charsets.UTF_8,
        )
        spool.append(3, "good-line-2")

        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        assertEquals(listOf("good-line", "good-line-2"), replayed)
        assertEquals(1L, spool.corruptLines)

        // 第二次 drain 不再重复计数(损坏行已被清理)
        spool.drain { true }
        assertEquals(1L, spool.corruptLines)
    }

    @Test
    fun `half line without trailing newline does not block replay`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        spool.append(1, "r1")
        // 追加未换行结尾的半截行(模拟崩溃中断):seq 前缀仍可解析
        File(dir, "spool.dat").appendText("12\ttruncated", Charsets.UTF_8)

        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        assertEquals(listOf("r1", "truncated"), replayed)

        spool.onAck(12)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `legacy lines without seq prefix are sent and trimmed by ack zero`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        // v2 时代无 seq 前缀的行:按 seq=0 处理(旧数据必然先于新行写入)
        File(dir, "spool.dat").appendText("legacy-no-seq-line\n", Charsets.UTF_8)
        spool.append(1, "r1")

        val replayed = mutableListOf<String>()
        spool.drain { replayed.add(it); true }
        assertEquals(listOf("legacy-no-seq-line", "r1"), replayed)

        spool.onAck(0) // 老行随 ACK(0) 清理
        spool.onAck(1)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `per-process sub directory isolates spool files`() {
        val spool = RecordSpool(dir, sub = "com.a:push")
        spool.append(1, "r1")
        assertTrue(File(dir, "com.a_push/spool.dat").exists())
        assertFalse(File(dir, "spool.dat").exists())
    }

    @Test
    fun `out-of-order file drains all lines and stops on failure`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        // 模拟队列满时直落盘与工作线程消费交错产生的乱序文件
        File(dir, "spool.dat").writeText("3\tr3\n1\tr1\n2\tr2\n", Charsets.UTF_8)

        val replayed = mutableListOf<String>()
        spool.drain { line ->
            if (line == "r1") false else {
                replayed.add(line)
                true
            }
        }
        // 乱序文件按行序发送,遇失败即停
        assertEquals(listOf("r3"), replayed)

        spool.resetSentCursor()
        val second = mutableListOf<String>()
        spool.drain { second.add(it); true }
        // 回退后完整重放(行序),宿主唯一索引幂等吸收
        assertEquals(listOf("r3", "r1", "r2"), second)
        spool.onAck(3)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `out-of-order file drains all lines and trim removes confirmed lines anywhere`() {
        val spool = RecordSpool(dir, maxBytes = 1024)
        File(dir, "spool.dat").writeText("3\tr3\n1\tr1\n2\tr2\n", Charsets.UTF_8)

        val first = mutableListOf<String>()
        spool.drain { first.add(it); true }
        assertEquals(listOf("r3", "r1", "r2"), first)

        // 未确认行重发不卡死(低 seq 行不会被已发送高 seq 行挡住)
        val second = mutableListOf<String>()
        spool.drain { second.add(it); true }
        assertEquals(listOf("r3", "r1", "r2"), second)

        // 确认 r1/r2:乱序文件中位于高 seq 行之后的已确认行同样被裁剪
        spool.onAck(2)
        spool.onAck(1)
        val third = mutableListOf<String>()
        spool.drain { third.add(it); true }
        assertEquals(listOf("r3"), third)
        assertTrue(spool.hasPending()) // r3 未确认,保留

        spool.onAck(3)
        assertFalse(spool.hasPending())
    }

    @Test
    fun `rotation counts dropped oldest lines`() {
        val spool = RecordSpool(dir, maxBytes = 30)
        for (i in 1..30) spool.append(i.toLong(), "line-$i")
        assertTrue(spool.spoolDropped > 0)
    }
}