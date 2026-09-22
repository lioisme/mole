package com.lioisme.modev.mcp.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.core.model.UiInteractionRecord
import com.lioisme.modev.core.model.WebViewRecord
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

/**
 * 记录载荷 JSON Schema 的**派生**工厂(V45 T3.2 / DUAL-6):
 * 写入面键集与类型一律从 [MonitoredRecord] 模型族反射取得,零手写清单。
 *
 * 为什么必须派生:旧形态是一份 60 行手抄属性表,而它抄的是**存储列语义**
 * (`contentHex`/`bindArgsJson`/`prefKey`/`httpMethod`/`keyLength`…),
 * V45 T2.5 把写入模型换成类型化记录族后模型字段实际叫
 * `content`/`bindArgs`/`key`/`method`(BytesView 是嵌套对象)——
 * 于是 `tools/list` 广告的参数有相当一部分会被 handler 以「未知字段」拒绝,
 * Agent 照着 schema 写记录**必然失败**;而 handler 真正接受的
 * `stack`/`provider`/`keyAlias`/`params` 又不在 schema 里,读写两侧永远对不上。
 * schema 与实现镜像在此由构造保证:同一批 `declaredFields` 既是 Gson 的序列化面,
 * 也是本工厂的 schema 面。
 *
 * 合并规则(10 个类型 → 一张并集表):
 * - 同名键类型不一致 → `type` 退化为类型名数组(如 `key`: crypto=object / prefs=string);
 * - `enum` 仅在全部来源一致时保留(跨类型 kind 值集不同 → 不声明,由 handler 按类型裁决);
 * - 嵌套对象(如 [com.lioisme.modev.core.util.BytesView])递归展开,冲突时并集。
 */
internal object RecordPayloadSchema {

    /** 记录类型 → 类型化模型类(写入面唯一分派表;密封穷尽,新增类型编译期强制登记)。 */
    fun modelClassOf(type: RecordType): Class<out MonitoredRecord> = when (type) {
        RecordType.CRYPTO -> CryptoRecord::class.java
        RecordType.DATABASE -> SqlRecord::class.java
        RecordType.BEHAVIOR -> BehaviorRecord::class.java
        RecordType.PREFS -> PrefsRecord::class.java
        RecordType.WEBVIEW -> WebViewRecord::class.java
        RecordType.NETWORK -> NetworkRecord::class.java
        RecordType.DEVICE_ENV -> DeviceEnvRecord::class.java
        RecordType.UI_INTERACTION -> UiInteractionRecord::class.java
        RecordType.SYSTEM -> SystemRecord::class.java
        RecordType.PROCESS_CONTROL -> ProcessRecord::class.java
    }

    /** 全部记录类型的载荷键并集 → 已合并的 schema 片段表(声明序按字母,稳定输出)。 */
    fun mergedProperties(): JsonObject {
        val perType = RecordType.entries.map { propertiesOf(modelClassOf(it)) }
        val names = perType.flatMap { it.keySet() }.toSortedSet()
        val merged = JsonObject()
        names.forEach { name ->
            merged.add(name, mergeVariants(perType.mapNotNull { it.get(name) as? JsonObject }))
        }
        // 分派键的值集是闭合且类型无关的(模型里只是 String,反射拿不到)——在此补齐,
        // 使非法 recordType = 协议错误 -32602,而不是穿透成业务错误。
        (merged.get("recordType") as? JsonObject)?.add(
            "enum",
            JsonArray().apply { RecordType.entries.forEach { add(it.wire) } },
        )
        return merged
    }

    /** 单类型载荷键集(与 handler 的 `payloadFields` 同集合 = 镜像对账断言之物)。 */
    fun keysOf(type: RecordType): Set<String> = propertiesOf(modelClassOf(type)).keySet()

    // ---- 反射展开 ----

    private val fieldCache = HashMap<Class<*>, JsonObject>()

    private fun propertiesOf(clazz: Class<*>): JsonObject {
        fieldCache[clazz]?.let { return it }
        val obj = JsonObject()
        clazz.declaredFields
            .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) }
            .sortedBy { it.name }
            .forEach { field -> obj.add(field.name, schemaOf(field)) }
        fieldCache[clazz] = obj
        return obj
    }

    private fun schemaOf(field: Field): JsonObject = schemaFor(field.type, field.genericType)

    private fun schemaFor(raw: Class<*>, generic: java.lang.reflect.Type): JsonObject = when {
        raw == String::class.java || CharSequence::class.java.isAssignableFrom(raw) -> typeProp("string")
        raw == Boolean::class.javaPrimitiveType || raw == java.lang.Boolean::class.java -> typeProp("boolean")
        raw == Double::class.javaPrimitiveType || raw == Float::class.javaPrimitiveType ||
            raw == java.lang.Double::class.java || raw == java.lang.Float::class.java -> typeProp("number")
        isIntegral(raw) -> typeProp("integer")
        raw.isEnum -> withEnumValues(typeProp("string"), raw)
        Collection::class.java.isAssignableFrom(raw) || raw.isArray -> {
            val elementType = if (raw.isArray) raw.componentType else typeArgument(generic)
            val items = if (elementType != null) schemaFor(elementType, elementType) else JsonObject()
            typeProp("array").apply { add("items", mergeVariants(listOf(items))) }
        }
        Map::class.java.isAssignableFrom(raw) -> typeProp("object")
        raw.name.startsWith("com.lioisme.modev.") ->
            typeProp("object").apply { add("properties", mergeVariants(listOf(propertiesOf(raw)))) }
        else -> typeProp("object")
    }

    private fun isIntegral(raw: Class<*>): Boolean = raw == Int::class.javaPrimitiveType ||
        raw == Long::class.javaPrimitiveType || raw == Short::class.javaPrimitiveType ||
        raw == Byte::class.javaPrimitiveType ||
        raw == java.lang.Integer::class.java || raw == java.lang.Long::class.java ||
        raw == java.lang.Short::class.java || raw == java.lang.Byte::class.java

    private fun typeArgument(generic: java.lang.reflect.Type): Class<*>? {
        val arg = (generic as? ParameterizedType)?.actualTypeArguments?.getOrNull(1)
            ?: (generic as? ParameterizedType)?.actualTypeArguments?.getOrNull(0)
        return (arg as? Class<*>) ?: (arg as? ParameterizedType)?.rawType as? Class<*>
    }

    private fun withEnumValues(prop: JsonObject, raw: Class<*>): JsonObject {
        val names = raw.enumConstants?.mapNotNull { (it as? Enum<*>)?.name } ?: return prop
        if (names.isNotEmpty()) {
            prop.add("enum", JsonArray().apply { names.forEach { add(it) } })
        }
        return prop
    }

    private fun typeProp(type: String): JsonObject = JsonObject().apply { addProperty("type", type) }

    // ---- 并集合并 ----

    private fun mergeVariants(variants: List<JsonObject>): JsonObject {
        val present = variants.filterIsInstance<JsonObject>().ifEmpty { return JsonObject() }
        if (present.size == 1) return present.single().deepCopy()
        val out = JsonObject()
        val types = present.mapNotNull { it.get("type")?.takeIf { t -> t.isJsonPrimitive }?.asString }.distinct()
        when {
            types.size == 1 -> out.addProperty("type", types.single())
            types.size > 1 -> out.add("type", JsonArray().apply { types.sorted().forEach { add(it) } })
        }
        // enum 只在完全一致时保留(跨类型值集不同 = 交由 handler 按 recordType 裁决)
        val enums = present.mapNotNull { it.get("enum") as? JsonArray }
        if (enums.isNotEmpty() && enums.all { it.toString() == enums.first().toString() }) {
            out.add("enum", enums.first().deepCopy())
        }
        val items = present.mapNotNull { it.get("items") as? JsonObject }
        if (items.isNotEmpty()) out.add("items", mergeVariants(items))
        val nested = present.mapNotNull { it.get("properties") as? JsonObject }
        if (nested.isNotEmpty()) {
            val names = nested.flatMap { it.keySet() }.toSortedSet()
            val props = JsonObject()
            names.forEach { name ->
                props.add(name, mergeVariants(nested.mapNotNull { it.get(name) as? JsonObject }))
            }
            out.add("properties", props)
        }
        return out
    }
}
