package com.lioisme.modev.hook

import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.EffectiveSwitch
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
 * - 连接宿主 App 固定端口,握手携带主包名/进程名(现代变体另带令牌供认证);
 * - 接收 [WireProtocol.TYPE_CONFIG] 配置推送并回调应用;未连接/未收到推送时使用内存默认配置;
 * - 接收 [WireProtocol.TYPE_ACK] 落库确认并回调(记录可靠性:ACK 游标推进);
 * - 发送算法记录(供宿主入库)与心跳;
 * - 断线后指数退避重连(上限 [MAX_RETRY_DELAY_MS]),不影响被 Hook 进程运行;
 * - 写超时兜底采用**每连接单共享 watchdog 线程**(写活动时间戳驱动),杜绝逐发送建线程。
 */
class ConfigChannel(
    private val packageName: String,
    private val processName: String,
    private val onConfig: (HookSwitch) -> Unit,
    private val onAck: (Long) -> Unit = {},
    private val tokenProvider: () -> String? = { null },
) {

    @Volatile
    var currentConfig: HookSwitch = HookSwitch.DEFAULT
        private set

    /**
     * 全局配置层(TCP 推送/远程偏好直读写入;V36 D-1 修复:与本地偏差层分离)。
     */
    @Volatile
    private var globalConfig: HookSwitch = HookSwitch.DEFAULT

    /**
     * 本地持久偏差层(V36 D-1 修复):Vector 2.2 不投递 XposedService,per-app 偏差经
     * V35 磁盘兜底进入本字段;宿主 TCP 推送仅更新全局层,有效配置恒为「全局 ⊕ 偏差」,
     * 推送不再覆盖磁盘兜底偏差。赋值即重解析生效。
     */
    @Volatile
    var localDeviation: AppListenOverride? = null
        set(value) {
            field = value
            currentConfig = applyDeviation(globalConfig)
        }

    private fun applyDeviation(global: HookSwitch): HookSwitch =
        localDeviation?.let { EffectiveSwitch.resolve(global, it) } ?: global

    /** 更新全局配置层并叠加本地偏差后回调(RemotePreferences 直读/变更监听路径用)。 */
    fun updateConfig(switch: HookSwitch) {
        globalConfig = switch
        currentConfig = applyDeviation(switch)
        onConfig(currentConfig)
    }

    @Volatile
    var connected: Boolean = false
        private set

    private val writerLock = Object()

    /**
     * 测试 seam(V43 T1 背压回归):连接端口 / 写判死超时 / 心跳间隔。
     * 生产路径恒为伴生对象默认值;仅单测缩短看门狗窗口以秒级验证「阻塞写被解阻」。
     */
    internal var port = WireProtocol.PORT
    internal var writeTimeoutMs = WRITE_TIMEOUT_MS
    internal var heartbeatMs = HEARTBEAT_MS

    /** watchdog 无锁读取判定「连接对象是否仍为 s」,故必须 volatile(V43 T1)。 */
    @Volatile
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    /**
     * 最近一次写活动时间戳(共享 watchdog 判定写阻塞用)。
     */
    @Volatile
    private var lastWriteMs = 0L

    /**
     * 是否有写操作在途(write/flush 尚未返回)。
     * watchdog 仅在「写操作在途且超时」时杀连接——空闲连接(仅读配置/心跳)永不误杀,
     * 否则每次 10s 无写入都会断开健康连接,引发 12s 重连抖动与记录送达延迟。
     */
    @Volatile
    private var writeInFlight = false

    private var watchdog: Thread? = null
    private var heartbeat: Thread? = null

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
        // D-3 修复:主动心跳(20s)——协议定义 ping/pong 但注入侧从不发送,
        // 空闲连接被宿主 120s 读超时杀死后形成半开连接,记录写入已死 socket 丢失 ACK。
        heartbeat = Thread(::heartbeatLoop, "modev-heartbeat").apply {
            isDaemon = true
            start()
        }
    }

    private fun heartbeatLoop() {
        while (running) {
            try {
                Thread.sleep(heartbeatMs)
            } catch (ie: InterruptedException) {
                break
            }
            if (!connected) continue
            synchronized(writerLock) {
                val w = writer
                if (w != null) {
                    writeInFlight = true
                    lastWriteMs = System.currentTimeMillis()
                    try {
                        w.write(WireCodec.ping())
                        w.newLine()
                        w.flush()
                    } catch (t: Throwable) {
                        closeSocket()
                    } finally {
                        writeInFlight = false
                    }
                }
            }
        }
    }

    /** 由 [RecordSink] 工作线程调用,阻塞直至写入完成或失败。 */
    fun sendRecordJson(json: String): Boolean {
        synchronized(writerLock) {
            val w = writer ?: return false
            val s = socket ?: return false
            writeInFlight = true
            lastWriteMs = System.currentTimeMillis()
            return try {
                w.write(json)
                w.newLine()
                w.flush()
                true
            } catch (t: Throwable) {
                closeSocket()
                false
            } finally {
                writeInFlight = false
            }
        }
    }

    /**
     * 每连接单共享 watchdog:仅当**写操作在途**且静止超过 [writeTimeoutMs] 时**直接关闭 socket**
     * (V43 T1:关锁——`Socket.close` 线程安全,阻塞中的 write 随即抛 SocketException 退出;
     * 旧实现取 writerLock 判死,而阻塞的 writer 正持该锁,看门狗永远进不了临界区,超时机制
     * 形同虚设并可与写侧构成背压死锁链)。空闲连接(无在途写入)不触发,杜绝误杀健康连接。
     * 单线程循环按连接生命周期运行:代元失效(socket 已替换)即自退出,不随重连累积。
     */
    private fun startWatchdog(s: Socket) {
        val w = Thread(
            {
                while (running) {
                    try {
                        Thread.sleep(writeTimeoutMs)
                    } catch (ie: InterruptedException) {
                        break
                    }
                    // 无锁判定:volatile socket/writeInFlight/lastWriteMs
                    if (socket !== s) break // 代元失效:连接已被替换/关闭,本 watchdog 退役
                    if (writeInFlight && System.currentTimeMillis() - lastWriteMs > writeTimeoutMs) {
                        runCatching { s.close() }
                    }
                }
            },
            "modev-write-watchdog",
        ).apply { isDaemon = true }
        watchdog = w
        w.start()
    }

    private fun connectLoop() {
        var retryMs = INITIAL_RETRY_MS
        while (running) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
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
                startWatchdog(s)

                // 握手(现代变体携带令牌,宿主校验;legacy 不带令牌走免认证路径)
                synchronized(writerLock) {
                    writeInFlight = true
                    lastWriteMs = System.currentTimeMillis()
                    try {
                        w.write(WireCodec.hello(packageName, processName, tokenProvider()))
                        w.newLine()
                        w.flush()
                    } finally {
                        writeInFlight = false
                    }
                }

                // 读循环:配置推送 / 落库确认 / pong
                while (running) {
                    val line = r.readLine() ?: break
                    val msg = WireCodec.parse(line) ?: continue
                    when (msg.type) {
                        WireProtocol.TYPE_CONFIG -> {
                            // 双端强校验:配置推送同样携带 magic/version,不匹配视为协议异常断开
                            if (!WireCodec.isProtocolCompatible(msg.payload)) {
                                android.util.Log.w(
                                    "Modev",
                                    "config channel: protocol mismatch in config push, disconnecting"
                                )
                                break
                            }
                            val cfg = WireCodec.configOf(msg.payload)
                            if (cfg != null) {
                                updateConfig(cfg)
                            }
                        }
                        WireProtocol.TYPE_ACK -> {
                            WireCodec.seqOf(msg.payload)?.let(onAck)
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
            writeInFlight = true
            lastWriteMs = System.currentTimeMillis()
            try {
                w.write(WireCodec.pong())
                w.newLine()
                w.flush()
            } catch (t: Throwable) {
                closeSocket()
            } finally {
                writeInFlight = false
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
            watchdog?.interrupt()
            watchdog = null
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 60000
        private const val INITIAL_RETRY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val WRITE_TIMEOUT_MS = 10000L

        /** 主动心跳间隔(D-3):远小于宿主 120s / 注入侧 60s 读超时,保活并快速探测半开。 */
        private const val HEARTBEAT_MS = 20_000L
    }
}