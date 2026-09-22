package com.lioisme.modev.mcp.tools

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.domain.record.BehaviorRow
import com.lioisme.modev.domain.record.CryptoRow
import com.lioisme.modev.domain.record.DeviceEnvRow
import com.lioisme.modev.domain.record.NetworkRow
import com.lioisme.modev.domain.record.PrefsRow
import com.lioisme.modev.domain.record.ProcessRow
import com.lioisme.modev.domain.record.RecordDetailModel
import com.lioisme.modev.domain.record.RowModel
import com.lioisme.modev.domain.record.SqlRow
import com.lioisme.modev.domain.record.SystemRow
import com.lioisme.modev.domain.record.UiInteractionRow
import com.lioisme.modev.domain.record.WebViewRow

/**
 * 记录模型 → JSON DTO 映射(V38 D3,纯 Kotlin 可 JVM 直测):
 * - 列表行:统一信封 `{id, timestamp, packageName, recordType, kind, operation, fields}`,
 *   类型特有字段整体入 `fields`(Gson 直驱,零手写逐字段拷贝);
 * - 详情:`RecordDetailModel` 全量物化(含 stack 与完整内容字段),大字段 1 MiB 截断守卫;
 * - Compose 注解(F1)不外泄:输出为纯 JSON 树。
 */
object McpDtos {

    /** 单字符串字段截断上限(1 MiB;详情响应 envelope 级 `truncated` 标志随之恒在)。 */
    const val MAX_FIELD_CHARS = 1 shl 20

    /**
     * 列表行信封承载的身份键(V45 T3.3 / DUAL-7):这些键**只**出现在顶层,
     * 一律从 `fields` 中剔除 —— 旧形态 `fields` 是各类型 RowModel/记录模型的原样序列化,
     * 于是算法行叫 `fields.id`、其余九型叫 `fields.recordId`,而身份键在信封与
     * `fields` 里各存一份(两处可能不一致)。统一后 Agent 侧读法唯一。
     */
    private val ENVELOPE_KEYS = setOf(
        "id", "seq", "recordId", "recordType", "packageName", "kind", "operation", "threadName",
        "timestamp", "recordTimestamp",
    )

    /** 列表行 → 统一信封 DTO `{id, recordType, packageName, kind, operation, threadName, timestamp, fields}`。 */
    fun row(row: RowModel): JsonObject {
        val identity = identityOf(row)
        return McpJson.obj(
            "id" to row.id,
            "recordType" to identity.recordType,
            "packageName" to row.packageName,
            "kind" to identity.kind,
            "operation" to identity.operation,
            "threadName" to identity.threadName,
            "timestamp" to row.timestamp,
            "fields" to identity.fields,
        )
    }

    /** 一行的信封身份 + 类型特有投影列(sealed 穷尽:新增记录类型编译期强制登记)。 */
    private class RowIdentity(
        val recordType: String,
        val kind: String,
        val operation: String,
        val threadName: String,
        val fields: JsonObject,
    )

    private fun identityOf(row: RowModel): RowIdentity = when (row) {
        is CryptoRow -> RowIdentity(
            RecordTypes.CRYPTO, row.record.kind.name, row.record.operation.name, row.record.threadName,
            fieldsOf(row.record),
        )
        is SqlRow -> RowIdentity(
            RecordTypes.DATABASE, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is BehaviorRow -> RowIdentity(
            RecordTypes.BEHAVIOR, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is PrefsRow -> RowIdentity(
            RecordTypes.PREFS, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is WebViewRow -> RowIdentity(
            RecordTypes.WEBVIEW, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is NetworkRow -> RowIdentity(
            RecordTypes.NETWORK, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is DeviceEnvRow -> RowIdentity(
            RecordTypes.DEVICE_ENV, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is UiInteractionRow -> RowIdentity(
            RecordTypes.UI_INTERACTION, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is SystemRow -> RowIdentity(
            RecordTypes.SYSTEM, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
        is ProcessRow -> RowIdentity(
            RecordTypes.PROCESS_CONTROL, row.kind.name, row.operation.name, row.threadName, fieldsOf(row),
        )
    }

    /** 类型特有列:载体原样序列化后剔除已上移信封的身份键。 */
    private fun fieldsOf(carrier: Any): JsonObject =
        McpJson.tree(carrier).asJsonObject.apply { ENVELOPE_KEYS.forEach { remove(it) } }

    /**
     * 详情全量 DTO(V45 T3.3 / DUAL-8):键集恒 9 项,`truncated` **恒在**
     * (旧形态仅在发生截断时补键 ⇒ Agent 必须用「键不存在」推断「没截断」,
     * 与 `false` 两种口径混用;现在读法唯一)。
     * 身份键直接取 [RecordDetailModel] 接口面,与 UI 详情页同源同一对象。
     */
    fun detail(model: RecordDetailModel): JsonObject {
        val obj = McpJson.obj(
            "id" to model.id,
            "recordType" to model.recordType,
            "packageName" to model.packageName,
            "processName" to model.processName,
            "threadName" to model.threadName,
            "stack" to McpJson.tree(model.stack),
            "timestamp" to model.timestamp,
            "record" to McpJson.tree(innerRecord(model)),
            "truncated" to false,
        )
        if (walkTruncate(obj)) obj.addProperty("truncated", true)
        return obj
    }

    /** 内层类型化记录(sealed 分派;Gson 直驱序列化)。 */
    private fun innerRecord(model: RecordDetailModel): Any = when (model) {
        is com.lioisme.modev.domain.record.CryptoDetail -> model.record
        is com.lioisme.modev.domain.record.SqlDetail -> model.record
        is com.lioisme.modev.domain.record.BehaviorDetail -> model.record
        is com.lioisme.modev.domain.record.PrefsDetail -> model.record
        is com.lioisme.modev.domain.record.WebViewDetail -> model.record
        is com.lioisme.modev.domain.record.NetworkDetail -> model.record
        is com.lioisme.modev.domain.record.DeviceEnvDetail -> model.record
        is com.lioisme.modev.domain.record.UiInteractionDetail -> model.record
        is com.lioisme.modev.domain.record.SystemDetail -> model.record
        is com.lioisme.modev.domain.record.ProcessDetail -> model.record
    }

    /** 深度遍历:超限字符串截断(2^20 字符);任一截断返回 true。 */
    private fun walkTruncate(element: JsonElement): Boolean {
        var truncated = false
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                for (key in obj.keySet().toList()) {
                    val child = obj.get(key)
                    if (child != null && child.isJsonPrimitive && child.asJsonPrimitive.isString) {
                        val value = child.asString
                        if (value.length > MAX_FIELD_CHARS) {
                            obj.addProperty(key, value.take(MAX_FIELD_CHARS) + "…[truncated]")
                            truncated = true
                        }
                    } else if (child != null && (child.isJsonObject || child.isJsonArray)) {
                        if (walkTruncate(child)) truncated = true
                    }
                }
            }
            element.isJsonArray -> {
                for (child in element.asJsonArray) {
                    if (child.isJsonObject || child.isJsonArray) {
                        if (walkTruncate(child)) truncated = true
                    }
                }
            }
        }
        return truncated
    }
}
