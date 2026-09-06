package com.lioisme.modev.mcp.tools

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * MCP JSON 基础设施(V38):
 * - [gson]:serializeNulls 专用实例 —— SQL NULL 等空值必须落 `null`(Gson 2.13 实锤:
 *   默认省略 JsonNull 会静默丢键),与注入侧 wire 的 `core/codec/Json` 单例隔离;
 * - 参数取值助手:统一「缺参 → null」语义,类型不符抛 [McpTool.McpToolException]。
 */
object McpJson {

    val gson: Gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    /** 任意模型 → JSON 树(纯数据类直驱;AppListenOverride/HookSwitch/记录模型等)。 */
    fun tree(src: Any?): JsonElement = gson.toJsonTree(src)

    fun obj(vararg pairs: Pair<String, JsonElement?>): JsonObject {
        val obj = JsonObject()
        pairs.forEach { (k, v) -> obj.add(k, v ?: com.google.gson.JsonNull.INSTANCE) }
        return obj
    }

    fun arr(items: List<JsonElement>): JsonArray {
        val arr = JsonArray()
        items.forEach { arr.add(it) }
        return arr
    }

    fun prop(key: String, value: String?): JsonObject {
        val obj = JsonObject()
        obj.add(key, if (value == null) com.google.gson.JsonNull.INSTANCE else com.google.gson.JsonPrimitive(value))
        return obj
    }

    // ---- 参数取值(schema 校验后的二次解析;null = 可选缺省) ----

    fun str(args: JsonObject, key: String): String? {
        val e = args.get(key) ?: return null
        if (e.isJsonNull) return null
        require(e.isJsonPrimitive && e.asJsonPrimitive.isString) { "Parameter '$key' must be a string" }
        return e.asString
    }

    fun bool(args: JsonObject, key: String): Boolean? {
        val e = args.get(key) ?: return null
        if (e.isJsonNull) return null
        require(e.isJsonPrimitive && e.asJsonPrimitive.isBoolean) { "Parameter '$key' must be a boolean" }
        return e.asBoolean
    }

    fun int(args: JsonObject, key: String): Int? {
        val e = args.get(key) ?: return null
        if (e.isJsonNull) return null
        require(e.isJsonPrimitive && e.asJsonPrimitive.isNumber) { "Parameter '$key' must be a number" }
        return e.asInt
    }

    fun long(args: JsonObject, key: String): Long? {
        val e = args.get(key) ?: return null
        if (e.isJsonNull) return null
        require(e.isJsonPrimitive && e.asJsonPrimitive.isNumber) { "Parameter '$key' must be a number" }
        return e.asLong
    }

    /** 字符串数组(空数组 → null,与上游「空表归零为取默认」语义对齐)。 */
    fun strList(args: JsonObject, key: String): List<String>? {
        val e = args.get(key) ?: return null
        if (e.isJsonNull) return null
        require(e.isJsonArray) { "Parameter '$key' must be an array" }
        return e.asJsonArray.mapNotNull { it?.takeIf { x -> x.isJsonPrimitive }?.asString }.ifEmpty { null }
    }
}
