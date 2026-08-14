package com.lioisme.modev.data.server

import com.lioisme.modev.core.codec.WireCodec
import com.lioisme.modev.core.codec.WireMessage
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
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
) : Closeable {

    /** 记录回调,由 LogRepository 接线(可热替换)。 */
    @Volatile
    var onRecord: (CryptoRecord) -> Unit = {}

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    /** socket → 连接上下文。 */
    private val clients = ConcurrentHashMap<Socket, ClientSession>()

    /** 已注册(握手成功)的包名 → 当前连接数。 */
    private val registeredPackages = ConcurrentHashMap<String, Int>()

    /** 当前已连接(已握手)的包名集合,供 UI 订阅。 */
    private val _connectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val connectedPackagesFlow: StateFlow<Set<String>> = _connectedPackages

    /** 当前已连接(已握手)的包名集合快照。 */
    fun connectedPackages(): Set<String> = registeredPackages.keys.toSet()

    /**
     * 新客户端握手时查询其持久化配置(由 SettingsRepository 注入)。
     * 为 null 表示尚无配置,注入侧继续使用内存默认值。
     */
    @Volatile
    var configProvider: ((String) -> HookSwitch?)? = null

    /** 最近一次推送的配置,供调试与热更新参考。 */
    private val lastConfig = ConcurrentHashMap<String, HookSwitch>()

    fun start() {
        if (running) return
        running = true
        acceptThread = thread(name = "modev-config-server") {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("127.0.0.1", port))
                serverSocket = ss
                while (running) {
                    val socket = try {
                        ss.accept()
                    } catch (t: Throwable) {
                        if (running) continue else break
                    }
                    handleClient(socket)
                }
            } catch (t: Throwable) {
                running = false
            }
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
                        WireProtocol.TYPE_RECORD -> onRecordMessage(msg)
                        WireProtocol.TYPE_PING -> sendPong(session)
                        WireProtocol.TYPE_PONG -> Unit
                    }
                }
            } catch (t: Throwable) {
                // 连接断开
            } finally {
                removeClient(socket)
            }
        }
    }

    private fun onHello(socket: Socket, session: ClientSession, msg: WireMessage) {
        val payload = msg.payload
        val packageName = WireCodec.packageNameOf(payload) ?: return
        val processName = payload?.get("processName")?.asString ?: packageName
        session.packageName = packageName
        session.processName = processName
        registeredPackages[packageName] = (registeredPackages[packageName] ?: 0) + 1
        _connectedPackages.value = connectedPackages()
        // 握手后立即推送持久化配置;未配置过的包由注入侧使用默认值
        configProvider?.invoke(packageName)?.let { broadcastConfig(packageName, it) }
    }

    private fun onRecordMessage(msg: WireMessage) {
        val record = WireCodec.recordOf(msg.payload)
        if (record == null) {
            android.util.Log.w("Modev", "server dropped record payload: ${msg.payload}")
            return
        }
        runCatching { onRecord(record) }
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

    /** 向指定包名的全部连接广播配置。 */
    fun broadcastConfig(packageName: String, switch: HookSwitch) {
        lastConfig[packageName] = switch
        for ((socket, session) in clients) {
            if (session.packageName != packageName) continue
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
    }

    companion object {
        private const val READ_TIMEOUT_MS = 120000
    }
}