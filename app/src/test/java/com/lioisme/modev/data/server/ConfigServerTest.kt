package com.lioisme.modev.data.server

import com.lioisme.modev.core.codec.CryptoRecordCodec
import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.core.codec.WireRecord
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** ConfigServer 鉴权与 ACK 集成测试(真实 loopback socket)。 */
class ConfigServerTest {

    private lateinit var server: ConfigServer
    private var port = 0

    @Before
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }
        server = ConfigServer(port)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun connect(): Pair<Socket, BufferedReader> {
        // start() 异步绑定:轮询直到服务就绪(最多 3s)
        var lastError: Throwable? = null
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", port), 1000)
                s.soTimeout = 5000
                return s to BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            } catch (t: Throwable) {
                lastError = t
                Thread.sleep(20)
            }
        }
        throw AssertionError("server not ready", lastError)
    }

    private fun writer(s: Socket): BufferedWriter =
        BufferedWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))

    /** 握手后服务端应立即推送配置;读到 config 行即证明连接被接受。 */
    private fun readConfigLine(reader: BufferedReader): String {
        var line: String?
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            line = reader.readLine() ?: break
            if (WireCodec.parse(line)?.type == WireProtocol.TYPE_CONFIG) return line
        }
        throw AssertionError("no config pushed")
    }

    @Test
    fun `hello without token is allowed when requireToken set`() {
        server.requireToken = "secret"
        server.effectiveResolver = { HookSwitch.DEFAULT }
        server.start()

        val (s, reader) = connect()
        val w = writer(s)
        w.write(WireCodec.hello("com.legacy.app", "com.legacy.app"))
        w.newLine()
        w.flush()

        // 连接存活且收到配置推送(L1 修复:null-token 放行)
        assertNotNull(WireCodec.parse(readConfigLine(reader)))
        assertEquals(1L, server.statsFlow.value.trustedNoToken)
        s.close()
    }

    @Test
    fun `hello with wrong token is rejected and connection closed`() {
        server.requireToken = "secret"
        server.start()

        val (s, reader) = connect()
        val w = writer(s)
        w.write(WireCodec.hello("com.attacker.app", "com.attacker.app", "wrong"))
        w.newLine()
        w.flush()

        // EOF = 服务端已断开;非空不匹配仍严格拒绝
        assertNull(reader.readLine())
        assertTrue(server.statsFlow.value.rejectedHellos >= 1)
        s.close()
    }

    @Test
    fun `hello with correct token is allowed`() {
        server.requireToken = "secret"
        server.effectiveResolver = { HookSwitch.DEFAULT }
        server.start()

        val (s, reader) = connect()
        val w = writer(s)
        w.write(WireCodec.hello("com.ok.app", "com.ok.app", "secret"))
        w.newLine()
        w.flush()

        assertNotNull(WireCodec.parse(readConfigLine(reader)))
        assertEquals(0L, server.statsFlow.value.trustedNoToken)
        s.close()
    }

    @Test
    fun `record ack is sent back with seq`() {
        server.start()
        var acked: Long? = null
        server.onRecord = { record, ack ->
            acked = record.seq
            ack(record.seq)
        }
        val (s, reader) = connect()
        val w = writer(s)
        w.write(WireCodec.hello("com.a.app", "com.a.app"))
        w.newLine()
        w.flush()

        val record = CryptoRecord(
            packageName = "com.a.app",
            kind = CryptoKind.CIPHER,
            operation = CryptoOperation.DO_FINAL,
            algorithm = "AES/GCM/NoPadding",
            threadName = "main",
            timestamp = 1L,
            seq = 42L,
        )
        w.write(WireCodec.record(record.seq, record.recordType, CryptoRecordCodec.encode(record)))
        w.newLine()
        w.flush()

        var line = reader.readLine()
        val deadline = System.currentTimeMillis() + 5000
        var ackSeen: Long? = null
        while (line != null && System.currentTimeMillis() < deadline) {
            val msg = WireCodec.parse(line)
            if (msg?.type == WireProtocol.TYPE_ACK) {
                ackSeen = WireCodec.seqOf(msg.payload)
                break
            }
            line = reader.readLine()
        }
        assertEquals(42L, ackSeen)
        assertEquals(42L, acked)
        s.close()
    }

    @Test
    fun `record with mismatched package is rejected`() {
        server.start()
        val (s, reader) = connect()
        val w = writer(s)
        w.write(WireCodec.hello("com.a.app", "com.a.app"))
        w.newLine()
        w.flush()

        val record = CryptoRecord(
            packageName = "com.b.app", // 与会话包名不符
            kind = CryptoKind.CIPHER,
            operation = CryptoOperation.DO_FINAL,
            algorithm = "AES",
            threadName = "main",
            timestamp = 1L,
        )
        w.write(WireCodec.record(record.seq, record.recordType, CryptoRecordCodec.encode(record)))
        w.newLine()
        w.flush()

        val deadline = System.currentTimeMillis() + 5000
        var rejectedSeen = false
        while (System.currentTimeMillis() < deadline) {
            if (server.statsFlow.value.rejectedRecords >= 1) {
                rejectedSeen = true
                break
            }
            Thread.sleep(20)
        }
        assertTrue(rejectedSeen)
        s.close()
    }

    @Test
    fun `record without recordType is dropped as malformed`() {
        server.start()
        val (s, reader) = connect()
        val w = writer(s)
        w.write(WireCodec.hello("com.a.app", "com.a.app"))
        w.newLine()
        w.flush()

        // 裸载荷(无 recordType/seq):v4 下按畸形丢弃,不得误分发给回调
        var callbackInvocations = 0
        server.onRecord = { _, _ -> callbackInvocations++ }
        val payload = com.google.gson.JsonObject().apply {
            addProperty("packageName", "com.a.app")
            addProperty("kind", "cipher")
        }
        w.write("{\"t\":\"record\",\"p\":$payload}")
        w.newLine()
        w.flush()

        val deadline = System.currentTimeMillis() + 5000
        var droppedSeen = false
        while (System.currentTimeMillis() < deadline) {
            if (server.statsFlow.value.droppedRecords >= 1) {
                droppedSeen = true
                break
            }
            Thread.sleep(20)
        }
        assertTrue(droppedSeen)
        assertEquals(0, callbackInvocations)
        s.close()
    }

    @Test
    fun `ack callback identity is stable within a session`() {
        server.start()
        val callbackIdentities = mutableSetOf<(Long) -> Unit>()
        server.onRecord = { record: WireRecord, ack -> callbackIdentities.add(ack); ack(record.seq) }
        val (s, reader) = connect()
        val w = writer(s)
        w.write(WireCodec.hello("com.a.app", "com.a.app"))
        w.newLine()
        w.flush()

        // 同一会话连续发 3 条记录:ACK 回调必须是同一实例(身份聚合前提,ADR-2)
        for (seq in 1L..3L) {
            val record = CryptoRecord(
                packageName = "com.a.app",
                kind = CryptoKind.CIPHER,
                operation = CryptoOperation.DO_FINAL,
                algorithm = "AES",
                threadName = "main",
                timestamp = seq,
                seq = seq,
            )
            w.write(WireCodec.record(record.seq, record.recordType, CryptoRecordCodec.encode(record)))
            w.newLine()
            w.flush()
        }
        val deadline = System.currentTimeMillis() + 5000
        while (callbackIdentities.size < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals(1, callbackIdentities.size)
        s.close()
    }
}
