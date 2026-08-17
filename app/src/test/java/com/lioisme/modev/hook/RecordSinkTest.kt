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

    @Test
    fun `seq persists and resumes after process restart`() {
        val seqFile = File(dir, "seq.dat")
        val s1 = sinkWith(seqFile, emitCount = 300)
        Thread.sleep(1100) // 等待持久化检查点(条数窗口 100 触发,第 300 条必已持久化)
        assertTrue("seq checkpoint file should exist", seqFile.exists())
        val persisted = seqFile.readText().trim().toLong()
        assertEquals(300L, persisted)

        // 模拟进程重启:新实例从持久化值继续(续号,不撞库内旧记录)
        val s2 = sinkWith(seqFile)
        s2.emit(record("resumed"))
        Thread.sleep(1100)
        assertEquals(persisted + 1, seqFile.readText().trim().toLong())
    }

    @Test
    fun `missing seq file starts at zero`() {
        val seqFile = File(dir, "absent.dat")
        val s = sinkWith(seqFile, emitCount = 1)
        Thread.sleep(1100)
        assertEquals(1L, seqFile.readText().trim().toLong())
    }

    @Test
    fun `corrupt seq file starts at zero`() {
        val seqFile = File(dir, "bad.dat")
        seqFile.writeText("not-a-number")
        val s = sinkWith(seqFile, emitCount = 1)
        Thread.sleep(1100)
        assertEquals(1L, seqFile.readText().trim().toLong())
    }
}