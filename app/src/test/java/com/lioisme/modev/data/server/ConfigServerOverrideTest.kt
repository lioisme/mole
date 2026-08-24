package com.lioisme.modev.data.server

import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.core.model.HookSwitch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * V23 每应用监听配置下发契约(D3):
 * - 握手按包 resolve:双包会话各自收到自己的有效配置(隔离);
 * - [ConfigServer.pushToPackage] 定向推送:仅目标包收到,其他会话零泄漏;
 * - resolver 未装配:握手后不推送(降级注入侧默认值)。
 */
class ConfigServerOverrideTest {

    private lateinit var server: ConfigServer
    private var port = 0

    /** 测试用有效配置源:包名 → 配置(可变,模拟宿主侧覆盖变更)。 */
    private val effectiveConfigs = mutableMapOf<String, HookSwitch>()

    @Before
    fun setUp() {
        port = ServerSocket(0).use { it.localPort }
        server = ConfigServer(port)
        server.effectiveResolver = { pkg -> effectiveConfigs[pkg] ?: HookSwitch.DEFAULT }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun connect(): Pair<Socket, BufferedReader> {
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

    private fun handshake(w: BufferedWriter, packageName: String) {
        w.write(WireCodec.hello(packageName, packageName))
        w.newLine()
        w.flush()
    }

    /**
     * 读取下一条 config 消息的 switch 载荷(跳过 ack/pong 等;
     * 缺字段防御走 [HookSwitch.fromJson],与注入侧解析同规)。
     */
    private fun readNextConfig(reader: BufferedReader): HookSwitch {
        var line: String?
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            line = reader.readLine() ?: break
            val msg = WireCodec.parse(line)
            if (msg?.type == WireProtocol.TYPE_CONFIG) {
                val obj = msg.payload?.get("switch")?.asJsonObject
                assertNotNull(obj)
                return HookSwitch.fromJson(com.google.gson.Gson(), obj.toString())
                    ?: throw AssertionError("config switch payload unparsable")
            }
        }
        throw AssertionError("no config message")
    }

    /** 在 timeout 内读不到任何行则通过(零泄漏断言)。 */
    private fun assertSilence(socket: Socket, reader: BufferedReader, timeoutMs: Long) {
        socket.soTimeout = timeoutMs.toInt()
        try {
            val line = reader.readLine()
            if (line != null) {
                throw AssertionError("unexpected message leaked: $line")
            }
        } catch (_: SocketTimeoutException) {
            // 期望路径
        } finally {
            socket.soTimeout = 5000
        }
    }

    @Test
    fun `handshake pushes package-specific effective config`() {
        // com.a 有覆盖(总开关强制关但 cipher 强制开),com.b 无覆盖走全局
        effectiveConfigs["com.a"] = HookSwitch.DEFAULT.copy(
            cryptoListen = false,
            cipher = true,
        )
        server.start()

        val (sa, ra) = connect()
        handshake(writer(sa), "com.a")
        val configA = readNextConfig(ra)
        assertTrue(configA.cipher)
        assertFalse(configA.cryptoListen)

        val (sb, rb) = connect()
        handshake(writer(sb), "com.b")
        val configB = readNextConfig(rb)
        assertEquals(HookSwitch.DEFAULT, configB)

        sa.close()
        sb.close()
    }

    @Test
    fun `pushToPackage targets only matching sessions`() {
        server.start()

        val (sa, ra) = connect()
        handshake(writer(sa), "com.a")
        readNextConfig(ra)

        val (sb, rb) = connect()
        handshake(writer(sb), "com.b")
        readNextConfig(rb)

        // 宿主侧变更 com.a 的覆盖并定向推送
        effectiveConfigs["com.a"] = HookSwitch.DEFAULT.copy(base64 = true, random = true, dbQuery = false)
        val pushed = server.pushToPackage("com.a")
        assertEquals(1, pushed)

        val configA = readNextConfig(ra)
        assertTrue(configA.base64)
        assertFalse(configA.dbQuery)
        // com.b 会话不得收到任何消息(零泄漏)
        assertSilence(sb, rb, timeoutMs = 1000)

        // 未连接包:no-op
        assertEquals(0, server.pushToPackage("com.not.connected"))

        sa.close()
        sb.close()
    }

    @Test
    fun `broadcastEffectiveConfig delivers per-package resolved values`() {
        server.start()

        val (sa, ra) = connect()
        handshake(writer(sa), "com.a")
        readNextConfig(ra)

        val (sb, rb) = connect()
        handshake(writer(sb), "com.b")
        readNextConfig(rb)

        // 全局关闭 base64(com.b 无覆盖跟随)+ com.a 保持自身覆盖(base64 强制开)
        effectiveConfigs["com.b"] = HookSwitch.DEFAULT.copy(base64 = false)
        effectiveConfigs["com.a"] = HookSwitch.DEFAULT.copy(base64 = true, random = false)
        server.broadcastEffectiveConfig()

        val configB = readNextConfig(rb)
        assertFalse(configB.base64)
        val configA = readNextConfig(ra)
        assertTrue(configA.base64)
        assertFalse(configA.random)

        sa.close()
        sb.close()
    }

    @Test
    fun `missing resolver degrades to no push on handshake`() {
        server.effectiveResolver = null
        server.start()

        val (s, reader) = connect()
        handshake(writer(s), "com.a")

        assertSilence(s, reader, timeoutMs = 800)
        s.close()
    }
}
