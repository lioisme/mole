package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.BehaviorRow
import com.lioisme.modev.domain.record.RowModel

/**
 * 行为记录映射:wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [CryptoRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 */

/** 行为记录 → 实体(宿主落库唯一写路径,与算法/数据库同表 ADR-1)。 */
internal fun BehaviorRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = "",
    path = path,
    destPath = destPath,
    command = command?.let { Json.gson.toJson(it) },
    fileSize = fileSize,
    contentHex = content?.hex,
    contentB64 = content?.base64,
    contentText = content?.text,
    contentLength = content?.totalLength,
    contentTruncated = content?.truncated ?: false,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    contextId = contextId,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → 行为记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toBehaviorModelOrNull(): BehaviorRecord? {
    val kind = enumOf(BehaviorKind::class.java, kind) ?: return null
    val operation = enumOf(BehaviorOperation::class.java, operation) ?: return null
    return BehaviorRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        path = path,
        destPath = destPath,
        command = command?.let(::parseCommand),
        fileSize = fileSize,
        content = view(contentHex, contentB64, contentText, contentLength, contentTruncated),
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

/** 列表投影行 → 行为轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.behaviorRowModelOrNull(): BehaviorRow? {
    val kind = enumOf(BehaviorKind::class.java, kind) ?: return null
    val operation = enumOf(BehaviorOperation::class.java, operation) ?: return null
    return BehaviorRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        path = path,
        destPath = destPath,
        command = command?.let(::parseCommand),
        fileSize = fileSize,
        threadName = threadName,
    )
}

/**
 * 行为记录结构化 JSON 导出(详情页「导出 JSON」出口,与既有两形态同规)。
 */
internal fun BehaviorRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    path?.let { put("path", it) }
    destPath?.let { put("destPath", it) }
    command?.let { put("command", org.json.JSONArray(it)) }
    fileSize?.let { put("fileSize", it) }
    content?.let { put("content", it.toJsonObject("content")) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    processName?.let { put("processName", it) }
    put("contextId", contextId)
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)

private fun parseCommand(json: String): List<String>? =
    runCatching { Json.gson.fromJson(json, Array<String>::class.java).toList() }
        .onFailure { Log.w("Modev", "command parse failed: $it") }
        .getOrNull()
