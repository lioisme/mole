package com.lioisme.modev.data.server

import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.domain.server.ConnectionMonitor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ConfigServer 生命周期与外部访问契约(V26):
 * - 默认绑定回环(127.0.0.1),boundAddress 观测点同步;
 * - externalAccess = true + restart → 立即改绑 0.0.0.0,本地 ping/pong 往返正常;
 * - 回切(默认语义)→ 重启后 boundAddress 复位为 127.0.0.1;
 * - close 后 boundAddress 复位 null。
 * 真实 ServerSocket/Socket(Robolectric 不 shadow 本机 TCP);
 * 代元守卫回归由「重启后往返仍通」承载。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigServerLifecycleTest {

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    /** 等待服务端完成绑定(boundAddress 等于期望地址)。 */
    private suspend fun awaitBound(server: ConfigServer, expected: String) {
        withTimeout(10_000) {
            while (server.boundAddress.first() != expected) {
                Thread.sleep(50)
                server.boundAddress.first()
            }
        }
    }

    /** 本地回环 ping 往返(协议层连通性;ping 无需握手)。 */
    private fun pingPong(port: Int): Boolean {
        Socket(InetAddress.getByName("127.0.0.1"), port).use { socket ->
            socket.soTimeout = 5_000
            val writer = socket.getOutputStream().writer(StandardCharsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            writer.write(WireCodec.ping())
            writer.write("\n")
            writer.flush()
            val line = reader.readLine() ?: return false
            return WireCodec.parse(line)?.type == "pong"
        }
    }

    @Test
    fun defaultBindsLoopback_andServesLocalClients() {
        val port = freePort()
        val server = ConfigServer(port)
        try {
            server.start()
            runBlocking { awaitBound(server, ConnectionMonitor.BIND_LOOPBACK) }
            assertTrue(pingPong(port))
        } finally {
            server.close()
        }
    }

    @Test
    fun externalAccess_restart_rebindsAllInterfaces_thenBackToLoopback() {
        val port = freePort()
        val server = ConfigServer(port)
        try {
            server.start()
            runBlocking { awaitBound(server, ConnectionMonitor.BIND_LOOPBACK) }

            // 开启外部访问 → restart → 全接口改绑 + 本地连通(注入侧恒连回环不受影响)
            server.externalAccess = true
            server.restart()
            runBlocking { awaitBound(server, ConnectionMonitor.BIND_ALL_INTERFACES) }
            assertTrue(pingPong(port))

            // 关闭 → restart → 回环复位
            server.externalAccess = false
            server.restart()
            runBlocking { awaitBound(server, ConnectionMonitor.BIND_LOOPBACK) }
            assertTrue(pingPong(port))
        } finally {
            server.close()
        }
    }

    @Test
    fun close_resetsBoundAddress() {
        val server = ConfigServer(freePort())
        server.start()
        runBlocking { awaitBound(server, ConnectionMonitor.BIND_LOOPBACK) }
        server.close()
        runBlocking {
            assertEquals(null, server.boundAddress.first())
        }
    }
}
