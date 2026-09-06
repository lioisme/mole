package com.lioisme.modev.mcp.tools

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lioisme.modev.mcp.protocol.JsonRpc
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import com.lioisme.modev.mcp.protocol.McpProtocol

/**
 * 工具注册表(V38):工具清单 / schema 校验 / 分发 / 错误包装的唯一入口。
 * - schema 校验:required 缺失或参数类型不符 → -32602(协议错误);
 * - 工具执行异常/超时 → `result.isError=true`(业务错误,分型不混淆);
 * - 未知工具 → -32602 + 消息(2025-06-18 对 tools/call 未知工具的推荐语义);
 * - 返回 [Outcome] 结构化结果,响应信封(id 回显)由传输层组装。
 */
class McpToolRegistry(private val tools: List<McpTool>) {

    /** tools/call 结果(协议错误 / 业务错误 / 成功三分型)。 */
    sealed interface Outcome {
        data class ProtocolError(val code: Int, val message: String) : Outcome
        data class BusinessError(val message: String) : Outcome
        data class Ok(val result: JsonObject) : Outcome
    }

    private val byName: Map<String, McpTool> = tools.associateBy { it.name }

    /** 工具名清单(parity 快照消费,注册表构造序)。 */
    fun names(): List<String> = tools.map { it.name }

    fun count(): Int = tools.size

    /** tools/list 的 tools 数组。 */
    fun listJson(): JsonArray {
        val arr = JsonArray()
        tools.forEach { tool ->
            val obj = JsonObject()
            obj.addProperty("name", tool.name)
            obj.addProperty("description", tool.description)
            obj.add("inputSchema", tool.inputSchema())
            arr.add(obj)
        }
        return arr
    }

    /** tools/call 分发(传输无关;[execute] 由调用方注入便于测试)。 */
    suspend fun call(
        toolName: String?,
        args: JsonObject?,
        execute: suspend (McpTool, JsonObject) -> JsonObject,
    ): Outcome {
        if (toolName == null) {
            return Outcome.ProtocolError(JsonRpc.INVALID_PARAMS, "Invalid params: missing tool name")
        }
        val tool = byName[toolName] ?: return Outcome.ProtocolError(JsonRpc.INVALID_PARAMS, "Unknown tool: $toolName")
        val validationError = validate(tool, args)
        if (validationError != null) {
            return Outcome.ProtocolError(JsonRpc.INVALID_PARAMS, validationError)
        }
        return try {
            val result = withTimeout(McpProtocol.TOOL_TIMEOUT_MS) { execute(tool, args ?: JsonObject()) }
            Outcome.Ok(wrapResult(result))
        } catch (e: TimeoutCancellationException) {
            Outcome.BusinessError("Tool execution timed out after ${McpProtocol.TOOL_TIMEOUT_MS}ms")
        } catch (e: McpTool.McpToolException) {
            Outcome.BusinessError(e.message ?: "Tool execution failed")
        } catch (e: Exception) {
            Outcome.BusinessError("Tool execution failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** schema 校验(required + 类型),返回错误消息或 null。 */
    private fun validate(tool: McpTool, args: JsonObject?): String? {
        val schema = tool.inputSchema()
        val properties = schema.get("properties") as? JsonObject ?: return null
        val provided = args ?: JsonObject()
        // required 缺失
        val required = schema.getAsJsonArray("required")
        required?.forEach { r ->
            val name = r.asString
            if (provided.get(name) == null) return "Missing required parameter: $name"
        }
        // 类型校验(宽松:integer 接受 number 整数值)
        properties.entrySet().forEach { (name, schemaValue) ->
            val arg = provided.get(name) ?: return@forEach
            if (arg.isJsonNull) return@forEach // null = 语义化「回默认」,类型豁免
            val expected = (schemaValue as? JsonObject)?.get("type")?.asString ?: return@forEach
            if (!typeMatches(expected, arg)) {
                return "Parameter '$name' expects $expected"
            }
        }
        return null
    }

    private fun typeMatches(expected: String, arg: JsonElement): Boolean = when (expected) {
        "string" -> arg.isJsonPrimitive && arg.asJsonPrimitive.isString
        "boolean" -> arg.isJsonPrimitive && arg.asJsonPrimitive.isBoolean
        "integer" -> arg.isJsonPrimitive && arg.asJsonPrimitive.isNumber &&
            arg.asJsonPrimitive.asDouble == Math.floor(arg.asJsonPrimitive.asDouble)
        "number" -> arg.isJsonPrimitive && arg.asJsonPrimitive.isNumber
        "array" -> arg.isJsonArray
        "object" -> arg.isJsonObject
        else -> true
    }

    /** 工具结果 → tools/call result(content 文本兜底 + structuredContent)。 */
    private fun wrapResult(result: JsonObject): JsonObject {
        val wrapped = JsonObject()
        val content = JsonArray()
        val text = JsonObject()
        text.addProperty("type", "text")
        text.addProperty("text", result.toString())
        content.add(text)
        wrapped.add("content", content)
        wrapped.add("structuredContent", result)
        return wrapped
    }
}
