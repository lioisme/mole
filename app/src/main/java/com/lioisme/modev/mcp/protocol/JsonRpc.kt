package com.lioisme.modev.mcp.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * JSON-RPC 2.0 编解码(V38):MCP 传输层专用,与注入侧 wire 协议([com.lioisme.modev.core.codec])
 * 完全隔离 —— 那一侧 VERSION 恒 4 且双端共源,禁止复用其消息形态。
 *
 * 纪律(2025-06-18 spec):
 * - 单请求单响应(batching 已移除,收到数组 → -32600);
 * - 错误码:-32700 parse / -32600 invalid request / -32601 method not found /
 *   -32602 invalid params / -32603 internal;
 * - 通知(id 缺失)不产生响应;响应恒含 `jsonrpc:"2.0"` + `id`(或 error)。
 */
object JsonRpc {

    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603

    private const val KEY_JSONRPC = "jsonrpc"
    private const val KEY_ID = "id"
    private const val KEY_METHOD = "method"
    private const val KEY_PARAMS = "params"
    private const val KEY_RESULT = "result"
    private const val KEY_ERROR = "error"
    private const val KEY_CODE = "code"
    private const val KEY_MESSAGE = "message"
    private const val KEY_DATA = "data"

    /** 解析后的入站请求。`id==null` 且 [isNotification] = 通知。 */
    data class Request(
        val id: JsonElement?,
        val method: String,
        val params: JsonObject?,
        val isNotification: Boolean,
    )

    sealed interface ParseResult {
        /** 已成形请求(含通知)。 */
        data class Ok(val request: Request) : ParseResult

        /** 已知 id 的错误响应(id 未知时响应 id=null)。 */
        data class Err(val response: String) : ParseResult
    }

    /** 解析入站 JSON 文本为请求(或错误响应)。 */
    fun parse(text: String): ParseResult {
        val root: JsonElement = try {
            JsonParser.parseString(text)
        } catch (t: Throwable) {
            return ParseResult.Err(errorResponse(null, PARSE_ERROR, "Parse error"))
        }
        if (root !is JsonObject) {
            return ParseResult.Err(errorResponse(null, INVALID_REQUEST, "Invalid Request: batched requests are not supported"))
        }
        val id = root.get(KEY_ID)?.takeIf { it !is JsonNull }
        val method = root.get(KEY_METHOD)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        if (method == null) {
            return ParseResult.Err(errorResponse(id, INVALID_REQUEST, "Invalid Request: missing method"))
        }
        val params = root.get(KEY_PARAMS)?.takeIf { it.isJsonObject }?.asJsonObject
        val isNotification = root.get(KEY_ID) == null
        return ParseResult.Ok(Request(id = id, method = method, params = params, isNotification = isNotification))
    }

    /** 成功响应。 */
    fun resultResponse(id: JsonElement?, result: JsonElement): String {
        val obj = JsonObject()
        obj.addProperty(KEY_JSONRPC, "2.0")
        obj.add(KEY_ID, id ?: JsonNull.INSTANCE)
        obj.add(KEY_RESULT, result)
        return obj.toString()
    }

    /** 错误响应。 */
    fun errorResponse(id: JsonElement?, code: Int, message: String, data: JsonElement? = null): String {
        val obj = JsonObject()
        obj.addProperty(KEY_JSONRPC, "2.0")
        obj.add(KEY_ID, id ?: JsonNull.INSTANCE)
        val err = JsonObject()
        err.addProperty(KEY_CODE, code)
        err.addProperty(KEY_MESSAGE, message)
        if (data != null) err.add(KEY_DATA, data)
        obj.add(KEY_ERROR, err)
        return obj.toString()
    }

    /** 工具业务错误响应(`result.isError` 形态,协议错误与业务错误分型)。 */
    fun toolErrorResponse(id: JsonElement?, message: String): String {
        val result = JsonObject()
        val content = JsonArray()
        val text = JsonObject()
        text.addProperty("type", "text")
        text.addProperty("text", message)
        content.add(text)
        result.add("content", content)
        result.addProperty("isError", true)
        return resultResponse(id, result)
    }
}
