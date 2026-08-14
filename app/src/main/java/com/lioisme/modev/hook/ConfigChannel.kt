package com.lioisme.modev.hook

import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.core.model.HookSwitch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * 注入侧配置通道(TCP 客户端):
 *
 * - 连接宿主 App 固定端口,握手携带包名/进程名;
 * - 接收 [WireProtocol.TYPE_CONFIG] 配置推送并回调应用;未连接/未收到推送时使用内存默认配置;
 * - 发送算法记录(供宿主入库)与心跳;
 * - 断线后指数退避重连(上限 [MAX_RETRY_DELAY_MS]),不影响被 Hook 进程运行。
 */
class ConfigChannel(
    private val packageName: String,
    private val processName: String,
    private val onConfig: (HookSwitch) -> Unit,
) {

    @Volatile
    var currentConfig: HookSwitch = HookSwitch.DEFAULT
        private set

    @Volatile
    var connected: Boolean = false
        private set

    private val writerLock = Object()
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    @Volatile
    private var running = false
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = Thread(::connectLoop, "modev-config-channel").apply {
            isDaemon = true
            start()
        }
    }

    /** 由 [RecordSink] 工作线程调用,阻塞直至写入完成或失败。 */
    fun sendRecordJson(json: String): Boolean {
        synchronized(writerLock) {
            val w = writer ?: return false
            return try {
                w.write(json)
                w.newLine()
                w.flush()
                true
            } catch (t: Throwable) {
                closeSocket()
                false
            }
        }
    }

    private fun connectLoop() {
        var retryMs = INITIAL_RETRY_MS
        while (running) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", WireProtocol.PORT), CONNECT_TIMEOUT_MS)
                s.soTimeout = READ_TIMEOUT_MS
                s.tcpNoDelay = true
                val r = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
                val w = BufferedWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))

                synchronized(writerLock) {
                    socket = s
                    writer = w
                }
                connected = true
                retryMs = INITIAL_RETRY_MS

                // 握手
                synchronized(writerLock) {
                    w.write(WireCodec.hello(packageName, processName))
                    w.newLine()
                    w.flush()
                }

                // 读循环:配置推送 / pong
                while (running) {
                    val line = r.readLine() ?: break
                    val msg = WireCodec.parse(line) ?: continue
                    when (msg.type) {
                        WireProtocol.TYPE_CONFIG -> {
                            val cfg = WireCodec.configOf(msg.payload)
                            if (cfg != null) {
                                currentConfig = cfg
                                onConfig(cfg)
                            }
                        }
                        WireProtocol.TYPE_PING -> sendPong()
                    }
                }
            } catch (t: Throwable) {
                // 连接/读取失败,退避重连
                android.util.Log.w("Modev", "config channel connect failed: $t")
            } finally {
                closeSocket()
                connected = false
            }
            if (running) {
                try {
                    Thread.sleep(retryMs)
                } catch (ie: InterruptedException) {
                    break
                }
                retryMs = (retryMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }
    }

    private fun sendPong() {
        synchronized(writerLock) {
            val w = writer ?: return
            try {
                w.write(WireCodec.pong())
                w.newLine()
                w.flush()
            } catch (t: Throwable) {
                closeSocket()
            }
        }
    }

    private fun closeSocket() {
        synchronized(writerLock) {
            writer = null
            try {
                socket?.close()
            } catch (ignored: Throwable) {
            }
            socket = null
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 60000
        private const val INITIAL_RETRY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
    }
}