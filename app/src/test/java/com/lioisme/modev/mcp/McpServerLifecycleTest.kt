package com.lioisme.modev.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lioisme.modev.mcp.protocol.McpProtocol
import com.lioisme.modev.mcp.tools.McpTool
import com.lioisme.modev.mcp.tools.McpToolRegistry
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MCP 服务器生命周期门禁:真回环 socket 驱动 Streamable HTTP 全语义 ——
 * 握手/会话/工具分发/错误码/auth/重启代元失效(镜像 ConfigServerLifecycleTest 的
 * 真端口测试模式)。
 */
class McpServerLifecycleTest {

    private val fakeTool = object : McpTool {
        override val name = "fake_tool"
        override val description = "fake tool for lifecycle test"

        override fun inputSchema(): JsonObject {
            val schema = JsonObject()
            schema.addProperty("type", "object")
            val props = JsonObject()
            val echo = JsonObject()
            echo.addProperty("type", "string")
            props.add("echo", echo)
            schema.add("properties", props)
            schema.add("required", com.google.gson.JsonArray().apply { add("echo") })
            return schema
        }

        override suspend fun execute(args: JsonObject): JsonObject {
            if (args.get("echo").asString == "boom") {
                throw McpTool.McpToolException("business failure")
            }
            return JsonObject().apply { addProperty("echo", args.get("echo").asString) }
        }
    }

    private lateinit var server: McpServer
    private var port: Int = 0

    @Before
    fun setUp() {
        server = McpServer(
            port = 0, // 内核分配空闲端口
            externalAccess = false,
            tokenAuthEnabled = false,
            requiredTokens = setOf("test-token"),
            serverInfo = JsonObject().apply {
                addProperty("name", "modev")
                addProperty("version", "test")
            },
            instructions = "test",
            registry = McpToolRegistry(listOf(fakeTool)),
        )
        server.start()
        port = server.actualPort()
        assertTrue("engine must listen", port > 0)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    // ---- HTTP 助手 ----

    private fun post(body: String, sessionId: String? = null, auth: String? = null): Triple<Int, Map<String, List<String>>, String> {
        val conn = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        sessionId?.let { conn.setRequestProperty("Mcp-Session-Id", it) }
        auth?.let { conn.setRequestProperty("Authorization", it) }
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val headers = conn.headerFields
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
        conn.disconnect()
        return Triple(code, headers, text)
    }

    private fun get(sessionId: String?): Int {
        val conn = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        sessionId?.let { conn.setRequestProperty("Mcp-Session-Id", it) }
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    private fun delete(sessionId: String): Int {
        val conn = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Mcp-Session-Id", sessionId)
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    private fun initialize(): String {
        val (code, headers, body) = post(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","clientInfo":{"name":"t","version":"0"}}}""",
        )
        assertEquals(200, code)
        val sid = headers["Mcp-Session-Id"]?.firstOrNull()
        assertNotNull("initialize must return session id header", sid)
        val obj = JsonParser.parseString(body).asJsonObject
        assertEquals("modev", obj.get("result").asJsonObject.get("serverInfo").asJsonObject.get("name").asString)
        assertEquals("2025-06-18", obj.get("result").asJsonObject.get("protocolVersion").asString)
        return sid!!
    }

    private fun rpc(sessionId: String, id: Int, method: String, params: String = "{}"): Pair<Int, JsonObject> {
        val (code, _, body) = post("""{"jsonrpc":"2.0","id":$id,"method":"$method","params":$params}""", sessionId)
        return code to JsonParser.parseString(body).asJsonObject
    }

    // ---- 断言 ----

    @Test
    fun request_without_session_is_404() {
        val (code, _, body) = post("""{"jsonrpc":"2.0","id":1,"method":"ping"}""")
        assertEquals(404, code)
        assertTrue("body=<$body>", body.contains("re-initialize"))
    }

    @Test
    fun initialize_then_ping_tools_list_and_call() {
        val sid = initialize()
        val (pingCode, ping) = rpc(sid, 2, "ping")
        assertEquals(200, pingCode)
        assertTrue(ping.has("result"))

        val (listCode, list) = rpc(sid, 3, "tools/list")
        assertEquals(200, listCode)
        val tools = list.get("result").asJsonObject.get("tools").asJsonArray
        assertEquals(1, tools.size())
        assertEquals("fake_tool", tools[0].asJsonObject.get("name").asString)

        val (callCode, call) = rpc(sid, 4, "tools/call", """{"name":"fake_tool","arguments":{"echo":"hi"}}""")
        assertEquals(200, callCode)
        val result = call.get("result").asJsonObject
        assertEquals("hi", result.get("structuredContent").asJsonObject.get("echo").asString)
        assertTrue(result.get("content").asJsonArray[0].asJsonObject.get("text").asString.contains("hi"))
    }

    @Test
    fun notification_initialized_is_202() {
        val sid = initialize()
        val (code, _, body) = post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", sid)
        assertEquals(202, code)
        assertEquals("", body)
    }

    @Test
    fun tool_business_error_is_result_isError() {
        val sid = initialize()
        val (code, body) = rpc(sid, 5, "tools/call", """{"name":"fake_tool","arguments":{"echo":"boom"}}""")
        assertEquals(200, code)
        assertTrue(body.get("result").asJsonObject.get("isError").asBoolean)
    }

    @Test
    fun unknown_tool_is_invalid_params() {
        val sid = initialize()
        val (_, body) = rpc(sid, 6, "tools/call", """{"name":"nope","arguments":{}}""")
        assertEquals(-32602, body.get("error").asJsonObject.get("code").asInt)
    }

    @Test
    fun missing_required_param_is_invalid_params() {
        val sid = initialize()
        val (_, body) = rpc(sid, 7, "tools/call", """{"name":"fake_tool","arguments":{}}""")
        val err = body.get("error")
        assertNotNull("missing required must be protocol error", err)
        assertEquals(-32602, err.asJsonObject.get("code").asInt)
    }

    @Test
    fun malformed_json_is_parse_error_http200() {
        val (code, _, body) = post("""{"jsonrpc":""")
        assertEquals(200, code)
        assertTrue("body=<$body>", body.contains("-32700"))
    }

    @Test
    fun batch_is_invalid_request() {
        val (code, _, body) = post("""[{"jsonrpc":"2.0","id":1,"method":"ping"}]""")
        assertEquals(200, code)
        assertTrue("body=<$body>", body.contains("-32600"))
    }

    @Test
    fun unknown_method_is_method_not_found() {
        val sid = initialize()
        val (_, body) = rpc(sid, 8, "resources/list")
        assertEquals(-32601, body.get("error").asJsonObject.get("code").asInt)
    }

    @Test
    fun get_is_405() {
        val sid = initialize()
        assertEquals(405, get(sid))
    }

    @Test
    fun delete_closes_session() {
        val sid = initialize()
        assertEquals(200, delete(sid))
        val (code, _) = rpc(sid, 9, "ping")
        assertEquals(404, code)
    }

    @Test
    fun restart_invalidates_sessions() {
        val sid = initialize()
        server.restart()
        val newPort = server.actualPort()
        assertTrue(newPort > 0)
        // 端口可能不变;会话必须失效
        val conn = URL("http://127.0.0.1:$newPort/mcp").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Mcp-Session-Id", sid)
        conn.outputStream.use { it.write("""{"jsonrpc":"2.0","id":1,"method":"ping"}""".toByteArray()) }
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun external_access_without_auth_serves_unauthenticated() {
        // V40 D-4 解耦:外部访问开启而令牌认证停用 = 用户显式选择的无认证监听,
        // 服务端不再强制 Bearer(0.0.0.0 无认证可直连)。
        server.stop()
        server.externalAccess = true
        server.port = 0
        server.start()
        val p = server.actualPort()
        assertTrue(p > 0)
        val init = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}"""
        val (ok, headers, _) = postOn(p, init, auth = null)
        assertEquals("无认证头部必须直通(认证仅由 tokenAuthEnabled 决定)", 200, ok)
        assertNotNull(headers["Mcp-Session-Id"]?.firstOrNull())
    }

    @Test
    fun external_access_with_auth_requires_bearer_token() {
        server.stop()
        server.externalAccess = true
        server.tokenAuthEnabled = true
        server.port = 0
        server.start()
        val p = server.actualPort()
        assertTrue(p > 0)
        val init = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}"""
        val (unauthorized, _, _) = postOn(p, init, auth = "Bearer wrong")
        assertEquals(401, unauthorized)
        val (ok, headers, _) = postOn(p, init, auth = "Bearer test-token")
        assertEquals(200, ok)
        assertNotNull(headers["Mcp-Session-Id"]?.firstOrNull())
    }

    // ---- V39 令牌矩阵 ----

    private val initBody = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}"""

    @Test
    fun token_auth_off_accepts_loopback_without_header() {
        // 默认停用:回环无 Authorization 直通(setUp 即 tokenAuthEnabled=false)
        val (code, _, _) = post(initBody)
        assertEquals(200, code)
    }

    @Test
    fun token_auth_enforced_on_loopback_when_enabled() {
        server.tokenAuthEnabled = true
        val (noHeader, _, _) = post(initBody)
        assertEquals("回环同样强制(V39 全接口语义)", 401, noHeader)
        val (wrong, _, _) = post(initBody, auth = "Bearer nope")
        assertEquals(401, wrong)
        val (ok, headers, _) = post(initBody, auth = "Bearer test-token")
        assertEquals(200, ok)
        assertNotNull(headers["Mcp-Session-Id"]?.firstOrNull())
    }

    @Test
    fun multiple_tokens_all_accepted_and_revoked_rejected() {
        server.tokenAuthEnabled = true
        server.requiredTokens = setOf("token-a", "token-b")
        assertEquals(200, post(initBody, auth = "Bearer token-a").first)
        assertEquals(200, post(initBody, auth = "Bearer token-b").first)
        // 撤销即时生效(原地更新校验集,不重启不踢会话)
        server.requiredTokens = setOf("token-b")
        assertEquals(401, post(initBody, auth = "Bearer token-a").first)
        assertEquals(200, post(initBody, auth = "Bearer token-b").first)
    }

    @Test
    fun empty_token_set_denies_all_when_auth_enabled() {
        server.tokenAuthEnabled = true
        server.requiredTokens = emptySet()
        assertEquals(401, post(initBody, auth = "Bearer anything").first)
        assertEquals(401, post(initBody).first)
    }

    @Test
    fun bound_address_tracks_bind_state() {
        assertEquals("127.0.0.1", server.boundAddress.value)
        server.stop()
        assertEquals(null, server.boundAddress.value)
    }

    private fun postOn(port: Int, body: String, auth: String?): Triple<Int, Map<String, List<String>>, String> {
        val conn = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        auth?.let { conn.setRequestProperty("Authorization", it) }
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val headers = conn.headerFields
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
        conn.disconnect()
        return Triple(code, headers, text)
    }
}
