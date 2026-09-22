package com.lioisme.modev.mcp.tools

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordType

/**
 * MCP JSON 基础设施(V38):
 * - [gson]:serializeNulls 专用实例 —— SQL NULL 等空值必须落 `null`(Gson 2.13 实锤:
 *   默认省略 JsonNull 会静默丢键),与注入侧 wire 的 `core/codec/Json` 单例隔离;
 * - **Kotlin 默认值优先实例化**(V45 T3.2):记录模型族经 [InstanceCreator] 走
 *   「合成默认值构造器」而非 Unsafe,缺省的可选字段因此取 Kotlin 默认值而不是 null
 *   (旧形态 `stack` 缺席 = null → 写路径 NPE;`timestamp` 缺席 = 0 → 记录落在 1970 年);
 * - 参数取值助手:统一「缺参 → null」语义,类型不符抛 [McpTool.McpToolException]。
 */
object McpJson {

    val gson: Gson = GsonBuilder().serializeNulls().disableHtmlEscaping()
        .apply {
            (
                RecordType.entries.map { RecordPayloadSchema.modelClassOf(it) } +
                    listOf(com.lioisme.modev.core.util.BytesView::class.java)
                ).filter { DefaultsFirstCreator.defaultsConstructor(it) != null }
                .forEach { registerTypeAdapter(it, DefaultsFirstCreator(it)) }
        }
        .create()

    /**
     * 「默认值优先」实例工厂:反射定位 Kotlin 为带默认值的数据类生成的合成构造器
     * (`(..., int $mask, DefaultConstructorMarker $$marker)`),以全 1 mask 调用它,
     * 使**所有有默认值的参数**取 Kotlin 默认值;无默认值的参数由 Gson 随后的字段注入补齐。
     *
     * 为什么不用 Unsafe(Gson 默认行为):Unsafe 跳过构造器 = 跳过全部默认值表达式,
     * 于是「可选字段缺省」在写入面等价于「该字段为 null」——非空集合字段直接 NPE,
     * 非空 Long 时间戳落 0。工具描述里那句「missing optional fields fall back to
     * defaults」自此才是真的。
     */
    private class DefaultsFirstCreator<T>(private val clazz: Class<T>) : InstanceCreator<T> {

        @Suppress("UNCHECKED_CAST")
        override fun createInstance(type: java.lang.reflect.Type): T = instantiate(clazz) as T

        companion object {
            /**
             * 取合成默认值构造器(末两位 = `int` mask + `DefaultConstructorMarker`)。
             * 无默认值的数据类不生成该构造器 → 返回 null,调用方退回 Gson 默认实例化。
             */
            fun defaultsConstructor(clazz: Class<*>): java.lang.reflect.Constructor<*>? =
                clazz.declaredConstructors.firstOrNull { c ->
                    val p = c.parameterTypes
                    p.size >= 2 &&
                        p[p.size - 1].name == "kotlin.jvm.internal.DefaultConstructorMarker" &&
                        p[p.size - 2] == Int::class.javaPrimitiveType
                }

            /** 建实例:mask 全置 + 非默认参数填占位值(见 [placeholder])。 */
            private fun instantiate(clazz: Class<*>): Any {
                val ctor = requireNotNull(defaultsConstructor(clazz)) {
                    "${clazz.name} has no Kotlin defaults constructor"
                }
                val params = ctor.parameterTypes
                val callArgs = Array(params.size) { i ->
                    when (i) {
                        params.size - 2 -> -1 // mask:有默认值的参数一律取默认值
                        params.size - 1 -> null // DefaultConstructorMarker 哨兵
                        else -> placeholder(params[i])
                    }
                }
                return ctor.newInstance(*callArgs)
            }

            /**
             * 无默认值参数的占位值。
             *
             * 为什么不能传 null:合成构造器把非默认参数**原样交给真实构造器**,
             * 而 Kotlin 在非空参数上生成 `checkNotNullParameter` ⇒ null 直接抛异常。
             * 占位值只是「先满足类型不变式」的临时底,真正的值由 Gson 随后逐字段注入;
             * 写入面必需的键(packageName/kind/operation/threadName/…)已由
             * `recordFromArgs` 的缺值检查强制提供,不会停留在占位态。
             */
            private fun placeholder(type: Class<*>): Any? = when {
                type == java.lang.Boolean.TYPE -> false
                type == java.lang.Byte.TYPE -> 0.toByte()
                type == java.lang.Short.TYPE -> 0.toShort()
                type == java.lang.Integer.TYPE -> 0
                type == java.lang.Long.TYPE -> 0L
                type == java.lang.Float.TYPE -> 0f
                type == java.lang.Double.TYPE -> 0.0
                type == java.lang.Character.TYPE -> 0.toChar()
                type == String::class.java || CharSequence::class.java.isAssignableFrom(type) -> ""
                type.isEnum -> type.enumConstants?.firstOrNull()
                Collection::class.java.isAssignableFrom(type) -> ArrayList<Any>()
                Map::class.java.isAssignableFrom(type) -> HashMap<Any, Any>()
                type.name.startsWith("com.lioisme.modev.") && defaultsConstructor(type) != null ->
                    runCatching { instantiate(type) }.getOrNull()
                else -> null
            }
        }
    }

    /** 任意模型 → JSON 树(纯数据类直驱;AppListenOverride/HookSwitch/记录模型等)。 */
    fun tree(src: Any?): JsonElement = gson.toJsonTree(src)

    /** 标量/树 → JSON 元素(null 恒落 JsonNull,禁 `JsonPrimitive(null)` 形态)。 */
    fun value(v: Any?): JsonElement = when (v) {
        null -> com.google.gson.JsonNull.INSTANCE
        is JsonElement -> v
        else -> tree(v)
    }

    /**
     * 对象构造(V45 T3.3 协议面统一):值一律 `Any?`——
     * 标量直接给 Kotlin 值、模型给对象、缺值给 null(键**恒保留**,Agent 侧键集稳定可解析)。
     */
    fun obj(vararg pairs: Pair<String, Any?>): JsonObject {
        val obj = JsonObject()
        pairs.forEach { (k, v) -> obj.add(k, value(v)) }
        return obj
    }

    /** 数组构造(元素同上,含 null 时落 JsonNull)。 */
    fun arr(items: List<Any?>): JsonArray {
        val arr = JsonArray()
        items.forEach { arr.add(value(it)) }
        return arr
    }

    fun prop(key: String, value: String?): JsonObject = obj(key to value)

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
