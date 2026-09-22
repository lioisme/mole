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

    /** schema 校验(required + 类型 + enum + 未声明键),返回错误消息或 null。 */
    private fun validate(tool: McpTool, args: JsonObject?): String? {
        val schema = tool.inputSchema()
        val properties = schema.get("properties") as? JsonObject ?: return null
        val provided = args ?: JsonObject()
        val declared = properties.keySet()
        // required 缺失 / 显式 null(V45 T3.2:两者同一分型 -32602。「required 但传 null」
        // 旧形态会穿透到 handler 变成业务错误,于是同一个调用错误有两条出口)
        val required = schema.getAsJsonArray("required")
        required?.forEach { r ->
            val name = r.asString
            val value = provided.get(name)
            if (value == null) return "Missing required parameter: $name"
            if (value.isJsonNull) return "Missing required parameter: $name (null is not accepted)"
        }
        // 未声明参数硬拒(additionalProperties=false,由 SchemaBuilder 恒置)
        if (schema.isAdditionalPropertiesFalse()) {
            val unknown = provided.keySet().filterNot { it in declared }
            if (unknown.isNotEmpty()) {
                return "Unknown parameter(s) for ${tool.name}: ${unknown.joinToString(",")} " +
                    "(declared: ${declared.joinToString(",")})"
            }
        }
        // 类型 + enum 校验(宽松:integer 接受 number 整数值;null = 语义化「回默认」豁免)
        properties.entrySet().forEach { (name, schemaValue) ->
            val arg = provided.get(name) ?: return@forEach
            if (arg.isJsonNull) return@forEach
            val obj = schemaValue as? JsonObject ?: return@forEach
            val expected = obj.typeNames()
            if (expected.isNotEmpty() && !typeMatches(expected, arg)) {
                return "Parameter '$name' expects ${expected.joinToString(" or ")}"
            }
            // V45 T3.2:schema 声明了 enum 就必须**由 schema 自己生效**。
            // 旧形态只查类型不查枚举,非法值穿透到 handler 才被判业务错误(isError),
            // 于是「声明面说只接受 A/B/C,实际却要先按业务错误返回」双端口径分叉;
            // 现在非法枚举 = 协议错误 -32602(与 required/类型违规同一分型)。
            val allowed = obj.get("enum") as? JsonArray ?: return@forEach
            val values = allowed.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            if (values.isNotEmpty() && arg.isJsonPrimitive && values.none { it == arg.asString }) {
                val shown = if (values.size <= 12) values.joinToString(",") else values.take(12).joinToString(",") + ",…"
                return "Parameter '$name' must be one of: $shown"
            }
        }
        return null
    }

    /** `type` 既可是单值也可是值数组(派生载荷面在跨记录类型合并时的同名不同型)。 */
    private fun JsonObject.typeNames(): List<String> = when (val t = get("type")) {
        null -> emptyList()
        else -> when {
            t.isJsonPrimitive -> listOf(t.asString)
            t.isJsonArray -> t.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            else -> emptyList()
        }
    }

    private fun JsonObject.isAdditionalPropertiesFalse(): Boolean =
        get("additionalProperties")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == false

    private fun typeMatches(expected: List<String>, arg: JsonElement): Boolean =
        expected.any { typeMatches(it, arg) }

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
