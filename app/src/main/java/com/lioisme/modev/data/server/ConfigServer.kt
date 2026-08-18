package com.lioisme.modev.data.server

import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.codec.WireMessage
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.core.codec.WireRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.domain.server.ConnectionMonitor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 宿主侧配置/日志服务端(仅监听 loopback):
 *
 * - 接受注入进程 TCP 连接,握手后登记 (socket → 包名);
 * - 接收 [WireProtocol.TYPE_RECORD] 并回调 [onRecord];
 * - 对指定包名的所有连接广播 [WireProtocol.TYPE_CONFIG];
 * - 心跳应答、断连清理、优雅停止。
 */
class ConfigServer(
    private val port: Int = WireProtocol.PORT,
) : Closeable, ConnectionMonitor {

    /**
     * 类型化记录回调(ADR-1,由 LogRepository 接线,可热替换):
     * 载荷为通用信封 [WireRecord],类型化分发由宿主注册表完成,
     * 本服务不感知具体记录模型 —— 与具体监听能力完全解耦。
     * 第二参数为该记录所属会话的 ACK 通道:宿主处理完成后回调以推进注入侧确认游标。
     */
    @Volatile
    var onRecord: (WireRecord, (Long) -> Unit) -> Unit = { _, _ -> }

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    /** socket → 连接上下文。 */
    private val clients = ConcurrentHashMap<Socket, ClientSession>()

    /** 已注册(握手成功)的包名 → 当前连接数。 */
    private val registeredPackages = ConcurrentHashMap<String, Int>()

    /** 当前已连接(已握手)的包名集合,供 UI 订阅(ConnectionMonitor)。 */
    private val _connectedPackages = MutableStateFlow<Set<String>>(emptySet())
    override val connectedPackagesFlow: StateFlow<Set<String>> = _connectedPackages

    /** 当前已连接(已握手)的包名集合快照。 */
    fun connectedPackages(): Set<String> = registeredPackages.keys.toSet()

    /** 通道统计(拒绝/丢弃计数),供设置页展示。 */
    private val _stats = MutableStateFlow(ChannelStats())
    val statsFlow: StateFlow<ChannelStats> = _stats

    private fun bumpStats(transform: (ChannelStats) -> ChannelStats) {
        _stats.update { transform(it) }
    }

    /**
     * 新客户端握手时查询其全局配置(由 SettingsRepository 注入,V10:与包名解耦)。
     * 为 null 表示尚无配置,注入侧继续使用内存默认值。
     */
    @Volatile
    var configProvider: (() -> HookSwitch?)? = null

    /**
     * 握手令牌要求:
     * - null = 显式关闭鉴权(默认,Vector legacy 框架无法向注入进程提供令牌);
     * - 非 null = 强制校验,hello 携带的 token 缺失或不匹配一律拒绝并断开。
     */
    @Volatile
    var requireToken: String? = null

    fun start() {
        if (running) return
        running = true
        acceptThread = thread(name = "modev-config-server") {
            var bindRetryMs = 2000L
            while (running) {
                try {
                    val ss = ServerSocket()
                    ss.reuseAddress = true
                    ss.bind(InetSocketAddress("127.0.0.1", port))
                    serverSocket = ss
                    bindRetryMs = 2000L
                    while (running) {
                        val socket = try {
                            ss.accept()
                        } catch (t: Throwable) {
                            if (running) continue else break
                        }
                        handleClient(socket)
                    }
                } catch (t: Throwable) {
                    if (!running) break
                    // 端口 bind 失败:指数退避重试(2s→30s)
                    android.util.Log.w("Modev", "config server bind failed, retry in ${bindRetryMs}ms: $t")
                    try {
                        Thread.sleep(bindRetryMs)
                    } catch (ie: InterruptedException) {
                        break
                    }
                    bindRetryMs = (bindRetryMs * 2).coerceAtMost(30000L)
                }
            }
            running = false
        }
    }

    private fun handleClient(socket: Socket) {
        thread(name = "modev-client-${socket.inetAddress.hostAddress}") {
            val session = ClientSession(socket)
            clients[socket] = session
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                session.writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
                while (running) {
                    val line = reader.readLine() ?: break
                    val msg = WireCodec.parse(line) ?: continue
                    when (msg.type) {
                        WireProtocol.TYPE_HELLO -> onHello(socket, session, msg)
                        WireProtocol.TYPE_RECORD -> onRecordMessage(session, msg)
                        WireProtocol.TYPE_PING -> sendPong(session)
                        WireProtocol.TYPE_PONG -> Unit
                    }
                }
            } catch (t: Throwable) {
                // 连接断开(含鉴权拒绝,原因已在上层日志记录)
            } finally {
                removeClient(socket)
            }
        }
    }

    private fun onHello(socket: Socket, session: ClientSession, msg: WireMessage) {
        val payload = msg.payload
        // ① 握手 magic/version 强校验:不匹配断开并记录原因
        if (!WireCodec.isProtocolCompatible(payload)) {
            android.util.Log.w(
                "Modev",
                "rejected hello from ${socket.inetAddress}: protocol mismatch (magic=${WireCodec.magicOf(payload)} version=${WireCodec.versionOf(payload)})"
            )
            bumpStats { it.copy(rejectedHellos = it.rejectedHellos + 1) }
            throw SecurityException("protocol mismatch")
        }
        // ② 令牌校验(无 null 放行):
        // - token 缺失(null):放行并计数(LSPosed legacy / Vector 注入进程无框架令牌可携带),
        //   此类连接信任注入侧包名自报;
        // - token 携带但非空不匹配:拒绝并断开(防欺骗注入)。
        requireToken?.let { required ->
            val token = payload?.get(WireProtocol.KEY_TOKEN)?.asString
            if (token == null) {
                android.util.Log.w(
                    "Modev",
                    "hello from ${socket.inetAddress} allowed without token (legacy/vector path)"
                )
                bumpStats { it.copy(trustedNoToken = it.trustedNoToken + 1) }
            } else if (token != required) {
                android.util.Log.w(
                    "Modev",
                    "rejected hello from ${socket.inetAddress}: token mismatch (got=${token.take(8)}… expect=${required.take(8)}…)"
                )
                bumpStats { it.copy(rejectedHellos = it.rejectedHellos + 1) }
                throw SecurityException("token mismatch")
            }
        }
        val packageName = WireCodec.packageNameOf(payload) ?: return
        val processName = payload?.get("processName")?.asString ?: packageName
        session.packageName = packageName
        session.processName = processName
        registeredPackages[packageName] = (registeredPackages[packageName] ?: 0) + 1
        _connectedPackages.value = connectedPackages()
        // 握手后立即推送全局配置;未配置时由注入侧使用默认值
        val pushed = configProvider?.invoke()?.let { cfg ->
            broadcastConfig(cfg)
            cfg
        }
        android.util.Log.i(
            "Modev",
            "handshake ${packageName}(${processName}) registered, config pushed=${pushed != null}"
        )
    }

    private fun onRecordMessage(session: ClientSession, msg: WireMessage) {
        val record = WireCodec.recordOf(msg.payload)
        if (record == null) {
            android.util.Log.w("Modev", "server dropped record payload: ${msg.payload}")
            bumpStats { it.copy(droppedRecords = it.droppedRecords + 1) }
            return
        }
        // ③ 记录包名与会话包名一致性:缺失按畸形丢弃,不匹配丢弃并计数
        val recordPackage = WireCodec.packageNameOf(record.payload)
        if (recordPackage == null) {
            android.util.Log.w("Modev", "server dropped record: missing packageName")
            bumpStats { it.copy(droppedRecords = it.droppedRecords + 1) }
            return
        }
        val sessionPackage = session.packageName
        if (sessionPackage != null && recordPackage != sessionPackage) {
            android.util.Log.w(
                "Modev",
                "server dropped record: package mismatch (record=$recordPackage session=$sessionPackage)"
            )
            bumpStats { it.copy(rejectedRecords = it.rejectedRecords + 1) }
            return
        }
        // ACK 回调身份稳定(会话内同一实例),宿主按身份聚合每会话单游标
        runCatching { onRecord(record, session.ack) }
    }

    private fun sendPong(session: ClientSession) {
        synchronized(session) {
            runCatching {
                session.writer?.apply {
                    write(WireCodec.pong())
                    newLine()
                    flush()
                }
            }
        }
    }

    /**
     * 向全部已注册客户端广播全局配置(V10:配置全局唯一,不再按包过滤)。
     * 载荷仍携带会话包名(协议兼容,注入侧只读 switch 字段)。
     */
    fun broadcastConfig(switch: HookSwitch) {
        for ((socket, session) in clients) {
            val packageName = session.packageName ?: continue
            synchronized(session) {
                runCatching {
                    session.writer?.apply {
                        write(WireCodec.config(packageName, switch))
                        newLine()
                        flush()
                    }
                }
            }
        }
    }

    private fun removeClient(socket: Socket) {
        val session = clients.remove(socket) ?: return
        session.packageName?.let { pkg ->
            val n = (registeredPackages[pkg] ?: 1) - 1
            if (n <= 0) registeredPackages.remove(pkg) else registeredPackages[pkg] = n
        }
        _connectedPackages.value = connectedPackages()
        runCatching { socket.close() }
    }

    override fun close() {
        running = false
        runCatching { serverSocket?.close() }
        for (socket in clients.keys) {
            runCatching { socket.close() }
        }
        clients.clear()
        registeredPackages.clear()
        _connectedPackages.value = emptySet()
    }

    private class ClientSession(val socket: Socket) {
        @Volatile
        var writer: BufferedWriter? = null

        @Volatile
        var packageName: String? = null

        @Volatile
        var processName: String? = null

        /**
         * 会话内身份稳定的 ACK 回调(唯一实例):
         * 宿主 LogRepository 以回调身份聚合每会话最大 seq(ADR-2),
         * 若每记录新建 lambda 将失去身份一致性,聚合退化为每条一 ACK。
         */
        val ack: (Long) -> Unit = { seq -> sendAck(seq) }

        /** 发送 ACK:通知注入侧该 seq(含)之前的记录已落库,可推进确认游标。 */
        fun sendAck(seq: Long) {
            synchronized(this) {
                runCatching {
                    writer?.apply {
                        write(WireCodec.ack(seq))
                        newLine()
                        flush()
                    }
                }
            }
        }
    }

    companion object {
        private const val READ_TIMEOUT_MS = 120000
    }
}

/**
 * 通道统计:拒绝/丢弃计数,供宿主设置页展示。
 */
data class ChannelStats(
    val rejectedHellos: Long = 0L,
    val droppedRecords: Long = 0L,
    val rejectedRecords: Long = 0L,

    /** 未携带令牌但被放行的握手数(LSPosed legacy / Vector 注入路径)。 */
    val trustedNoToken: Long = 0L,
)