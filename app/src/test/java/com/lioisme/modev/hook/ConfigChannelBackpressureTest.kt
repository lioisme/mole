package com.lioisme.modev.hook

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V43 T1 背压回归:writer 持 writerLock 阻塞写 socket 时,watchdog **无锁**关闭连接
 * 必须让阻塞中的写抛异常退出(旧实现 watchdog 与 writer 争同一把锁,超时判死形同虚设,
 * 宿主线程可经队列满降级路径被无限期阻塞)。
 *
 * 驱动方式 = 真实回环 TCP:测试侧 accept 后只读握手不再消费,靠流控令客户端写阻塞;
 * 端口/判死超时经 [ConfigChannel.port] / [ConfigChannel.writeTimeoutMs] 测试 seam 注入。
 */
class ConfigChannelBackpressureTest {

    @Test
    fun `blocked write is killed by watchdog and send returns false`() {
        ServerSocket(0, 8).use { server ->
            server.receiveBufferSize = 8192 // 尽量收窄接收缓冲,加速零窗口形成
            val accepted = java.util.concurrent.atomic.AtomicReference<java.net.Socket?>()
            val acceptThread = Thread {
                // runCatching 自带异常吞并:accept/握手失败仅静默(测试主路径会超时暴露)
                runCatching {
                    val s = server.accept()
                    s.receiveBufferSize = 8192
                    accepted.set(s)
                    // 读掉 hello 握手行,随后冻结消费(不再 readLine)
                    BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine()
                }
            }.apply { isDaemon = true; start() }

            val channel = ConfigChannel(
                packageName = "com.backpressure",
                processName = "com.backpressure",
                onConfig = {},
            )
            channel.port = server.localPort
            channel.writeTimeoutMs = 250L
            channel.heartbeatMs = 60_000L // 排除心跳写对判定的干扰
            channel.start()

            try {
                acceptThread.join(5_000)
                val connectedDeadline = System.currentTimeMillis() + 5_000
                while (!channel.connected && System.currentTimeMillis() < connectedDeadline) {
                    Thread.sleep(20)
                }
                assertTrue("通道应在 5s 内连上测试服务", channel.connected)

                // 灌大载荷直至流控让写失败/解阻(每发 256KiB;回环缓冲耗尽后必有一发阻塞)
                val payload = "x".repeat(256 * 1024)
                val start = System.currentTimeMillis()
                var failed = false
                var sent = 0
                while (sent < 64) {
                    if (!channel.sendRecordJson(payload)) {
                        failed = true
                        break
                    }
                    sent++
                }
                val elapsedMs = System.currentTimeMillis() - start
                assertTrue(
                    "写应在缓冲耗尽后被 watchdog 判死(64 发全通过=流控未生效,环境异常)",
                    failed || sent < 64,
                )
                // 核心断言:发生阻塞时,阻塞必在秒级被判死解除(旧死锁链下此处永不返回)
                assertTrue(
                    "watchdog 应在写超时窗口内解除阻塞,实测 ${elapsedMs}ms(sent=$failed)",
                    elapsedMs < 3_000L,
                )
            } finally {
                runCatching { accepted.get()?.close() }
            }
        }
    }
}
