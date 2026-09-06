package com.lioisme.modev.mcp.tools

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.mapping.BehaviorRow
import com.lioisme.modev.data.mapping.CryptoRow
import com.lioisme.modev.data.mapping.DeviceEnvRow
import com.lioisme.modev.data.mapping.NetworkRow
import com.lioisme.modev.data.mapping.PrefsRow
import com.lioisme.modev.data.mapping.ProcessRow
import com.lioisme.modev.data.mapping.RecordDetailModel
import com.lioisme.modev.data.mapping.RowModel
import com.lioisme.modev.data.mapping.SqlRow
import com.lioisme.modev.data.mapping.SystemRow
import com.lioisme.modev.data.mapping.UiInteractionRow
import com.lioisme.modev.data.mapping.WebViewRow

/**
 * 记录模型 → JSON DTO 映射(V38 D3,纯 Kotlin 可 JVM 直测):
 * - 列表行:统一信封 `{id, timestamp, packageName, recordType, kind, operation, fields}`,
 *   类型特有字段整体入 `fields`(Gson 直驱,零手写逐字段拷贝);
 * - 详情:`RecordDetailModel` 全量物化(含 stack 与完整内容字段),大字段 1 MiB 截断守卫;
 * - Compose 注解(F1)不外泄:输出为纯 JSON 树。
 */
object McpDtos {

    /** 单字符串字段截断上限(1 MiB;截断后置 envelope 级 truncated 标志)。 */
    const val MAX_FIELD_CHARS = 1 shl 20

    /** 列表行 → 统一信封 DTO。 */
    fun row(row: RowModel): JsonObject {
        val obj = JsonObject()
        obj.addProperty("id", row.id)
        obj.addProperty("timestamp", row.timestamp)
        obj.addProperty("packageName", row.packageName)
        when (row) {
            is CryptoRow -> {
                obj.addProperty("recordType", RecordTypes.CRYPTO)
                obj.add("kind", McpJson.tree(row.record.kind.name))
                obj.add("operation", McpJson.tree(row.record.operation.name))
                obj.add("fields", McpJson.tree(row.record))
            }
            is SqlRow -> {
                obj.addProperty("recordType", RecordTypes.DATABASE)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is BehaviorRow -> {
                obj.addProperty("recordType", RecordTypes.BEHAVIOR)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is PrefsRow -> {
                obj.addProperty("recordType", RecordTypes.PREFS)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is WebViewRow -> {
                obj.addProperty("recordType", RecordTypes.WEBVIEW)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is NetworkRow -> {
                obj.addProperty("recordType", RecordTypes.NETWORK)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is DeviceEnvRow -> {
                obj.addProperty("recordType", RecordTypes.DEVICE_ENV)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is UiInteractionRow -> {
                obj.addProperty("recordType", RecordTypes.UI_INTERACTION)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is SystemRow -> {
                obj.addProperty("recordType", RecordTypes.SYSTEM)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
            is ProcessRow -> {
                obj.addProperty("recordType", RecordTypes.PROCESS_CONTROL)
                obj.add("kind", McpJson.tree(row.kind.name))
                obj.add("operation", McpJson.tree(row.operation.name))
                obj.add("fields", McpJson.tree(row))
            }
        }
        return obj
    }

    /** 详情全量 DTO(大字段截断守卫;返回标志位)。 */
    fun detail(model: RecordDetailModel): JsonObject {
        val obj = JsonObject()
        obj.addProperty("id", model.id)
        obj.addProperty("recordType", model.recordType)
        obj.addProperty("packageName", model.packageName)
        obj.add("processName", McpJson.tree(model.processName))
        obj.addProperty("threadName", model.threadName)
        obj.add("stack", McpJson.tree(model.stack))
        obj.addProperty("timestamp", model.timestamp)
        obj.add("record", McpJson.tree(innerRecord(model)))
        val truncated = walkTruncate(obj)
        if (truncated) obj.addProperty("truncated", true)
        return obj
    }

    /** 内层类型化记录(sealed 分派;Gson 直驱序列化)。 */
    private fun innerRecord(model: RecordDetailModel): Any = when (model) {
        is com.lioisme.modev.data.mapping.CryptoDetail -> model.record
        is com.lioisme.modev.data.mapping.SqlDetail -> model.record
        is com.lioisme.modev.data.mapping.BehaviorDetail -> model.record
        is com.lioisme.modev.data.mapping.PrefsDetail -> model.record
        is com.lioisme.modev.data.mapping.WebViewDetail -> model.record
        is com.lioisme.modev.data.mapping.NetworkDetail -> model.record
        is com.lioisme.modev.data.mapping.DeviceEnvDetail -> model.record
        is com.lioisme.modev.data.mapping.UiInteractionDetail -> model.record
        is com.lioisme.modev.data.mapping.SystemDetail -> model.record
        is com.lioisme.modev.data.mapping.ProcessDetail -> model.record
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
