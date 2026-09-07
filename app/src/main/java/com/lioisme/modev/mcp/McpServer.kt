package com.lioisme.modev.mcp

import android.util.Log
import com.google.gson.JsonObject
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.mcp.protocol.JsonRpc
import com.lioisme.modev.mcp.protocol.McpProtocol
import com.lioisme.modev.mcp.protocol.McpSessionManager
import com.lioisme.modev.mcp.tools.McpToolRegistry
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * MCP 服务器(V38;V39 令牌化):进程内常驻 Streamable HTTP,与注入侧 wire
 * [com.lioisme.modev.data.server.ConfigServer] 完全独立生命周期(独立端口/开关/restart,互不触碰)。
 *
 * - 生命周期:`@Volatile epoch` 守卫(镜像 ConfigServer 既有模式),restart = close+start,
 *   旧代 HTTP 引擎 stop 后新代不受污染;
 * - 认证(V39;V40 D-4 解耦):令牌认证独立开关,**启用后全接口强制**(含回环 adb forward);
 *   校验集 = 多令牌 Bearer 集合,变更原地生效(不重启、不踢会话);
 *   外部访问与认证相互独立——是否鉴权**仅**由 tokenAuthEnabled 决定
 *   (外部 0.0.0.0 无认证监听 = 用户显式选择,UI 文案承载风险提示);
 * - 协议分发:initialize 建会话 → Mcp-Session-Id 头;notifications → 202;
 *   DELETE 关会话;GET 405(无服务端主动流,batching 已移除 → 数组 -32600)。
 *
 * 纯分发逻辑([dispatch])传输无关,JVM 真回环 socket 直测。
 */
class McpServer(
    @Volatile var port: Int = McpProtocol.DEFAULT_PORT,
    @Volatile var externalAccess: Boolean = false,
    @Volatile var tokenAuthEnabled: Boolean = false,
    @Volatile var requiredTokens: Set<String> = emptySet(),
    private val serverInfo: JsonObject,
    private val instructions: String,
    private val registry: McpToolRegistry,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)

    @Volatile private var epoch = 0L
    @Volatile private var engine: McpHttpEngine? = null

    private val sessions = McpSessionManager()

    /** 绑定地址观测点("127.0.0.1"/"0.0.0.0";未绑定 = null),服务页与 server_status 消费。 */
    private val _boundAddress = MutableStateFlow<String?>(null)
    val boundAddress: StateFlow<String?> = _boundAddress

    /** 最近一次绑定失败原因(null = 无错误);服务页错误态展示消费。 */
    private val _bindError = MutableStateFlow<String?>(null)
    val bindError: StateFlow<String?> = _bindError

    val isRunning: Boolean get() = running.get()

    /** 实际监听端口(port=0 由内核分配时;未启动 = -1,测试消费)。 */
    fun actualPort(): Int = engine?.getListeningPort() ?: -1

    fun start() {
        synchronized(lifecycleLock) {
            if (running.get()) return
            epoch += 1
            val myEpoch = epoch
            val bind =
                if (externalAccess) ConnectionMonitor.BIND_ALL_INTERFACES else ConnectionMonitor.BIND_LOOPBACK
            try {
                val e = McpHttpEngine(bind, port) { method, uri, headers, body ->
                    handleHttp(method, uri, headers, body)
                }
                // NanoHTTPD 默认 socket 读超时 5s 对 Agent 长请求足够;
                // daemon=true:JVM 测试不因引擎线程悬挂
                e.start(SOCKET_READ_TIMEOUT_SECONDS * 1000, true)
                engine = e
                running.set(true)
                _boundAddress.value = bind
                _bindError.value = null
                Log.i(TAG, "mcp server started $bind:$port epoch=$myEpoch")
            } catch (t: Throwable) {
                Log.w(TAG, "mcp server bind failed $bind:$port: ${t.message}")
                // 绑定失败不置 running:端口被占等场景退避重试由上层(设置写入/restart)驱动
                running.set(false)
                _boundAddress.value = null
                _bindError.value = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            if (!running.get()) return
            epoch += 1
            running.set(false)
            _boundAddress.value = null
            sessions.closeAll()
            runCatching { engine?.stop() }
            engine = null
            Log.i(TAG, "mcp server stopped")
        }
    }

    /** 重启(端口/绑定语义变更时);带代元守卫,任意线程可调。 */
    fun restart() {
        stop()
        start()
    }

    /** 会话数(server_status 消费)。 */
    fun sessionCount(): Int = sessions.count()

    /**
     * HTTP 入口(引擎回调)。返回状态码/体/会话头。
     */
    private fun handleHttp(method: String, uri: String, headers: Map<String, String>, body: String): HttpResult {
        if (uri != McpProtocol.ENDPOINT_PATH) {
            return HttpResult(404, JsonRpc.errorResponse(null, JsonRpc.INVALID_REQUEST, "Not found"))
        }
        // 认证(V39;V40 D-4 解耦):仅令牌认证开关决定是否强制 Bearer(启用后全接口,含回环);
        // 外部访问与认证互相独立,无服务端强制耦合。
        if (tokenAuthEnabled) {
            val auth = headers.entries.firstOrNull { it.key.equals("authorization", ignoreCase = true) }?.value
            val bearer = auth?.removePrefix("Bearer ")?.trim()
            if (bearer.isNullOrEmpty() || bearer !in requiredTokens) {
                return HttpResult(401, JsonRpc.errorResponse(null, JsonRpc.INVALID_REQUEST, "Unauthorized"))
            }
        }
        return when (method.uppercase()) {
            "POST" -> handlePost(headers, body)
            "GET" -> HttpResult(405, JsonRpc.errorResponse(null, JsonRpc.INVALID_REQUEST, "SSE streaming not supported; use POST"))
            "DELETE" -> {
                val sid = headerOf(headers, McpProtocol.HEADER_SESSION)
                sessions.close(sid)
                HttpResult(200, "{}")
            }
            else -> HttpResult(405, JsonRpc.errorResponse(null, JsonRpc.INVALID_REQUEST, "Method not allowed"))
        }
    }

    private fun handlePost(headers: Map<String, String>, body: String): HttpResult {
        if (body.length > McpProtocol.MAX_BODY_BYTES) {
            return HttpResult(413, JsonRpc.errorResponse(null, JsonRpc.INVALID_REQUEST, "Payload too large"))
        }
        // 会话校验:initialize 前无需会话;其余请求必须持有效会话
        val sid = headerOf(headers, McpProtocol.HEADER_SESSION)
        val parse = JsonRpc.parse(body)
        val request = when (parse) {
            is JsonRpc.ParseResult.Err -> return HttpResult(200, parse.response)
            is JsonRpc.ParseResult.Ok -> parse.request
        }
        val isInitialize = request.method == McpProtocol.METHOD_INITIALIZE
        if (!isInitialize) {
            sessions.validate(sid) ?: return HttpResult(
                404,
                JsonRpc.errorResponse(request.id, JsonRpc.INVALID_REQUEST, "Session not found; re-initialize"),
            )
        }
        when (request.method) {
            McpProtocol.METHOD_INITIALIZE -> {
                val version = request.params?.get("protocolVersion")?.takeIf { it.isJsonPrimitive }?.asString
                val negotiated = McpProtocol.negotiate(version)
                val created = sessions.create(negotiated)
                val result = JsonObject()
                result.addProperty("protocolVersion", negotiated)
                result.add("capabilities", capabilities())
                result.add("serverInfo", serverInfo)
                result.addProperty("instructions", instructions)
                return HttpResult(200, JsonRpc.resultResponse(request.id, result), mapOf(McpProtocol.HEADER_SESSION to created.id))
            }
            McpProtocol.NOTIFICATION_INITIALIZED ->
                return HttpResult(202, "")
            McpProtocol.METHOD_PING ->
                return HttpResult(200, JsonRpc.resultResponse(request.id, JsonObject()))
            McpProtocol.METHOD_TOOLS_LIST -> {
                val result = JsonObject()
                result.add("tools", registry.listJson())
                return HttpResult(200, JsonRpc.resultResponse(request.id, result))
            }
            McpProtocol.METHOD_TOOLS_CALL -> {
                val toolName = request.params?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                val args = request.params?.get("arguments") as? JsonObject
                // 引擎线程上同步执行(工具自带 30s 超时),结果由引擎写回
                val outcome = runBlocking {
                    registry.call(toolName, args) { tool, safeArgs -> tool.execute(safeArgs) }
                }
                val response = when (outcome) {
                    is McpToolRegistry.Outcome.ProtocolError ->
                        JsonRpc.errorResponse(request.id, outcome.code, outcome.message)
                    is McpToolRegistry.Outcome.BusinessError ->
                        JsonRpc.toolErrorResponse(request.id, outcome.message)
                    is McpToolRegistry.Outcome.Ok ->
                        JsonRpc.resultResponse(request.id, outcome.result)
                }
                return HttpResult(200, response)
            }
            else ->
                return if (request.isNotification) {
                    HttpResult(202, "")
                } else {
                    HttpResult(200, JsonRpc.errorResponse(request.id, JsonRpc.METHOD_NOT_FOUND, "Method not found: ${request.method}"))
                }
        }
    }

    private fun capabilities(): JsonObject {
        val caps = JsonObject()
        val tools = JsonObject()
        tools.addProperty("listChanged", false)
        caps.add("tools", tools)
        return caps
    }

    private fun headerOf(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    companion object {
        private const val TAG = "ModevMcp"
        private const val SOCKET_READ_TIMEOUT_SECONDS = 5
    }
}
