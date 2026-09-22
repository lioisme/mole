package com.lioisme.modev

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MCP 端到端验收(V38 §9.1):instrumented 进程内起真实容器 MCP 服务
 * (默认端口回环),HttpURLConnection 走 initialize → tools/list → records_query
 * 全链路(真实仓库 + 真实 HTTP 引擎,与 Agent 消费路径逐位一致)。
 */
@RunWith(AndroidJUnit4::class)
class McpEndToEndTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun post(port: Int, body: String, sessionId: String? = null): Triple<Int, String, String> {
        val conn = URL("http://127.0.0.1:$port/mcp").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 30000
        conn.setRequestProperty("Content-Type", "application/json")
        sessionId?.let { conn.setRequestProperty("Mcp-Session-Id", it) }
        conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val sid = conn.headerFields["Mcp-Session-Id"]?.firstOrNull() ?: ""
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
        conn.disconnect()
        return Triple(code, sid, text)
    }

    @Test
    fun initialize_tools_list_and_records_query_over_real_container() = runBlocking(Dispatchers.IO) {
        val mcp = (ctx.applicationContext as ModevApp).container.mcpServer
        if (!mcp.isRunning) mcp.start()
        assertTrue("mcp server must be running", mcp.isRunning)
        val port = mcp.actualPort().takeIf { it > 0 } ?: mcp.port

        val (initCode, sid, initBody) = post(
            port,
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","clientInfo":{"name":"androidTest","version":"1"}}}""",
        )
        assertEquals(200, initCode)
        assertTrue("session header required", sid.isNotBlank())
        val init = JsonParser.parseString(initBody).asJsonObject.get("result").asJsonObject
        assertEquals("modev", init.get("serverInfo").asJsonObject.get("name").asString)
        assertEquals("2025-06-18", init.get("protocolVersion").asString)

        val (notifCode, _, _) = post(port, """{"jsonrpc":"2.0","method":"notifications/initialized"}""", sid)
        assertEquals(202, notifCode)

        val (listCode, _, listBody) = post(port, """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}""", sid)
        assertEquals(200, listCode)
        val tools = JsonParser.parseString(listBody).asJsonObject.get("result").asJsonObject.get("tools").asJsonArray
        // V45 T0.1:期望值来自装配侧真值(registry),非写死数字——协议序列化丢工具即红。
        val servedNames = tools.map { it.asJsonObject.get("name").asString }
        assertEquals("tools/list must mirror the assembled tool surface", mcp.toolNames(), servedNames)
        assertEquals(mcp.toolCount(), tools.size())
        tools.forEach { tool ->
            val obj = tool.asJsonObject
            assertTrue("tool name blank", obj.get("name").asString.isNotBlank())
            assertNotNull("schema missing", obj.get("inputSchema").asJsonObject.get("properties"))
        }

        val (callCode, _, callBody) = post(
            port,
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"records_query","arguments":{"pageSize":5}}}""",
            sid,
        )
        assertEquals(200, callCode)
        val result = JsonParser.parseString(callBody).asJsonObject.get("result").asJsonObject
        val structured = result.get("structuredContent").asJsonObject
        assertTrue(structured.has("items"))
        assertTrue(structured.get("total").asLong >= 0)
        assertEquals(5, structured.get("pageSize").asInt)
        assertTrue(result.get("content").asJsonArray[0].asJsonObject.get("text").asString.isNotEmpty())
    }
}
