package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.core.model.UiInteractionRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.UiInteractionRow
import com.lioisme.modev.domain.record.RowModel

/**
 * UI 交互记录映射:wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [BehaviorRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 */

/** UI 交互记录 → 实体(宿主落库唯一写路径,与算法/数据库同表 ADR-1)。 */
internal fun UiInteractionRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = "",
    dialogTitle = dialogTitle,
    dialogMessage = dialogMessage,
    dialogKeywords = dialogKeywords?.let { Json.gson.toJson(it) },
    dialogIntercepted = dialogIntercepted?.let { if (it) 1 else 0 },
    interventionAction = interventionAction,
    widgetText = widgetText,
    widgetClassName = widgetClassName,
    activityName = activityName,
    intentAction = intentAction,
    intentData = intentData,
    intentExtrasJson = intentExtrasJson,
    intentFlags = intentFlags,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → UI 交互记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toUiInteractionModelOrNull(): UiInteractionRecord? {
    val kind = enumOf(UiInteractionKind::class.java, kind) ?: return null
    val operation = enumOf(UiInteractionOperation::class.java, operation) ?: return null
    return UiInteractionRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        dialogTitle = dialogTitle,
        dialogMessage = dialogMessage,
        dialogKeywords = dialogKeywords?.let { json ->
            runCatching { Json.gson.fromJson(json, Array<String>::class.java).toList() }
                .onFailure { Log.w("Modev", "dialogKeywords parse failed: $it") }
                .getOrNull()
        },
        dialogIntercepted = dialogIntercepted?.let { it == 1 },
        interventionAction = interventionAction,
        widgetText = widgetText,
        widgetClassName = widgetClassName,
        activityName = activityName,
        intentAction = intentAction,
        intentData = intentData,
        intentExtrasJson = intentExtrasJson,
        intentFlags = intentFlags,
        params = paramsJson?.let { json ->
            runCatching { Json.gson.fromJson(json, Map::class.java) as Map<String, String> }
                .onFailure { Log.w("Modev", "paramsJson parse failed: $it") }
                .getOrNull()
        },
        processName = processName,
        threadName = threadName,
        stack = stackJson?.let { json ->
            runCatching { Json.gson.fromJson(json, Array<String>::class.java).toList() }
                .onFailure { Log.w("Modev", "stackJson parse failed: $it") }
                .getOrNull()
        } ?: emptyList(),
        timestamp = timestamp,
        seq = seq ?: 0L,
    )
}

/** 列表投影行 → UI 交互轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.uiInteractionRowModelOrNull(): UiInteractionRow? {
    val kind = enumOf(UiInteractionKind::class.java, kind) ?: return null
    val operation = enumOf(UiInteractionOperation::class.java, operation) ?: return null
    return UiInteractionRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        dialogTitle = dialogTitle,
        dialogMessage = dialogMessage,
        widgetText = widgetText,
        activityName = activityName,
        threadName = threadName,
    )
}

/** UI 交互记录结构化 JSON 导出(详情页「导出 JSON」出口)。 */
internal fun UiInteractionRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    dialogTitle?.let { put("dialogTitle", it) }
    dialogMessage?.let { put("dialogMessage", it) }
    dialogKeywords?.let { put("dialogKeywords", org.json.JSONArray(it)) }
    dialogIntercepted?.let { put("dialogIntercepted", it) }
    interventionAction?.let { put("interventionAction", it) }
    widgetText?.let { put("widgetText", it) }
    widgetClassName?.let { put("widgetClassName", it) }
    activityName?.let { put("activityName", it) }
    intentAction?.let { put("intentAction", it) }
    intentData?.let { put("intentData", it) }
    intentExtrasJson?.let { put("intentExtrasJson", it) }
    intentFlags?.let { put("intentFlags", it) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    processName?.let { put("processName", it) }
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)
