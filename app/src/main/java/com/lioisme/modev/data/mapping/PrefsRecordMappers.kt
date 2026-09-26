package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.PrefsRow
import com.lioisme.modev.domain.record.RowModel

/**
 * 偏好记录映射:wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [BehaviorRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 *
 * 列复用(ADR-1 单表):path=配置文件路径;content 五元组=键值内容;
 * prefKey=v9→v10 新列;command/fileSize 恒 null。
 */

/** 偏好记录 → 实体(宿主落库唯一写路径)。 */
internal fun PrefsRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = "",
    path = path,
    prefKey = key,
    contentHex = value?.hex,
    contentB64 = value?.base64,
    contentText = value?.text,
    contentLength = value?.totalLength,
    contentTruncated = value?.truncated ?: false,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    contextId = contextId,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → 偏好记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toPrefsModelOrNull(): PrefsRecord? {
    val kind = enumOf(PrefsKind::class.java, kind) ?: return null
    val operation = enumOf(PrefsOperation::class.java, operation) ?: return null
    return PrefsRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        key = prefKey,
        value = view(contentHex, contentB64, contentText, contentLength, contentTruncated),
        path = path,
        params = paramsJson?.let { json ->
            runCatching { Json.gson.fromJson(json, Map::class.java) as Map<String, String> }
                .onFailure { Log.w("Modev", "paramsJson parse failed: $it") }
                .getOrNull()
        },
        processName = processName,
        contextId = contextId,
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

/** 列表投影行 → 偏好轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.prefsRowModelOrNull(): PrefsRow? {
    val kind = enumOf(PrefsKind::class.java, kind) ?: return null
    val operation = enumOf(PrefsOperation::class.java, operation) ?: return null
    return PrefsRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        prefKey = prefKey,
        path = path,
        threadName = threadName,
    )
}

/**
 * 偏好记录结构化 JSON 导出(详情页「导出 JSON」出口,与既有三形态同规)。
 */
internal fun PrefsRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    key?.let { put("key", it) }
    path?.let { put("path", it) }
    value?.let { put("value", it.toJsonObject("value")) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    processName?.let { put("processName", it) }
    put("contextId", contextId)
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)
