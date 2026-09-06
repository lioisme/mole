package com.lioisme.modev.mcp.protocol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON-RPC 2.0 编解码门禁(V38):解析/错误码/响应形态/通知语义。
 */
class JsonRpcTest {

    @Test
    fun parse_valid_request() {
        val r = JsonRpc.parse("""{"jsonrpc":"2.0","id":1,"method":"ping","params":{}}""")
        assertTrue(r is JsonRpc.ParseResult.Ok)
        r as JsonRpc.ParseResult.Ok
        assertEquals("ping", r.request.method)
        assertEquals(1, r.request.id?.asInt)
        assertTrue(!r.request.isNotification)
    }

    @Test
    fun parse_notification_has_no_id() {
        val r = JsonRpc.parse("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")
        assertTrue(r is JsonRpc.ParseResult.Ok)
        r as JsonRpc.ParseResult.Ok
        assertNull(r.request.id)
        assertTrue(r.request.isNotification)
    }

    @Test
    fun parse_batch_is_invalid_request() {
        val r = JsonRpc.parse("""[{"jsonrpc":"2.0","id":1,"method":"ping"}]""")
        assertTrue(r is JsonRpc.ParseResult.Err)
        r as JsonRpc.ParseResult.Err
        assertTrue(r.response.contains("-32600"))
    }

    @Test
    fun parse_malformed_json_is_parse_error() {
        val r = JsonRpc.parse("""{"jsonrpc": """)
        assertTrue(r is JsonRpc.ParseResult.Err)
        r as JsonRpc.ParseResult.Err
        assertTrue(r.response.contains("-32700"))
    }

    @Test
    fun parse_missing_method_is_invalid() {
        val r = JsonRpc.parse("""{"jsonrpc":"2.0","id":1}""")
        assertTrue(r is JsonRpc.ParseResult.Err)
        r as JsonRpc.ParseResult.Err
        assertTrue(r.response.contains("-32600"))
    }

    @Test
    fun result_response_shape() {
        val result = JsonObject().apply { addProperty("ok", true) }
        val text = JsonRpc.resultResponse(JsonParser.parseString("7"), result)
        val obj = JsonParser.parseString(text).asJsonObject
        assertEquals("2.0", obj.get("jsonrpc").asString)
        assertEquals(7, obj.get("id").asInt)
        assertTrue(obj.get("result").asJsonObject.get("ok").asBoolean)
        assertNull(obj.get("error"))
    }

    @Test
    fun error_response_shape_with_null_id() {
        val text = JsonRpc.errorResponse(null, JsonRpc.METHOD_NOT_FOUND, "Method not found: x")
        val obj = JsonParser.parseString(text).asJsonObject
        assertTrue(obj.get("id").isJsonNull)
        val err = obj.get("error").asJsonObject
        assertEquals(-32601, err.get("code").asInt)
        assertTrue(obj.get("result") == null)
    }

    @Test
    fun tool_error_response_is_result_with_isError() {
        val text = JsonRpc.toolErrorResponse(JsonParser.parseString("1"), "boom")
        val obj = JsonParser.parseString(text).asJsonObject
        val result = obj.get("result").asJsonObject
        assertTrue(result.get("isError").asBoolean)
        assertEquals("boom", result.get("content").asJsonArray[0].asJsonObject.get("text").asString)
        assertNull(obj.get("error"))
    }

    @Test
    fun negotiate_versions() {
        assertEquals("2024-11-05", McpProtocol.negotiate("2024-11-05"))
        assertEquals("2025-03-26", McpProtocol.negotiate("2025-03-26"))
        assertEquals("2025-06-18", McpProtocol.negotiate("2025-06-18"))
        assertEquals("2025-06-18", McpProtocol.negotiate("1999-01-01"))
        assertEquals("2025-06-18", McpProtocol.negotiate(null))
    }
}
