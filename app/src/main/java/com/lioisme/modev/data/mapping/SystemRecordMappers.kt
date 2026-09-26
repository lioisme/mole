package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.SystemRow
import com.lioisme.modev.domain.record.RowModel

/**
 * 系统信息记录映射:wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [BehaviorRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 */

/** 系统信息记录 → 实体(宿主落库唯一写路径)。 */
internal fun SystemRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = "",
    logcatTag = logcatTag,
    logcatLevel = logcatLevel,
    logcatMessage = logcatMessage,
    appVersion = appVersion,
    appVersionCode = appVersionCode,
    appMinSdk = appMinSdk,
    appTargetSdk = appTargetSdk,
    appPermissions = appPermissions?.let { Json.gson.toJson(it) },
    signatureHash = signatureHash,
    signatureBase64 = signatureBase64,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → 系统信息记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toSystemModelOrNull(): SystemRecord? {
    val kind = enumOf(SystemKind::class.java, kind) ?: return null
    val operation = enumOf(SystemOperation::class.java, operation) ?: return null
    return SystemRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        logcatTag = logcatTag,
        logcatLevel = logcatLevel,
        logcatMessage = logcatMessage,
        appVersion = appVersion,
        appVersionCode = appVersionCode,
        appMinSdk = appMinSdk,
        appTargetSdk = appTargetSdk,
        appPermissions = appPermissions?.let { json ->
            runCatching { Json.gson.fromJson(json, Array<String>::class.java).toList() }
                .onFailure { Log.w("Modev", "appPermissions parse failed: $it") }
                .getOrNull()
        },
        signatureHash = signatureHash,
        signatureBase64 = signatureBase64,
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

/** 列表投影行 → 系统信息轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.systemRowModelOrNull(): SystemRow? {
    val kind = enumOf(SystemKind::class.java, kind) ?: return null
    val operation = enumOf(SystemOperation::class.java, operation) ?: return null
    return SystemRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        logcatTag = logcatTag,
        logcatLevel = logcatLevel,
        logcatMessage = logcatMessage,
        signatureHash = signatureHash,
        threadName = threadName,
    )
}

/** 系统信息记录结构化 JSON 导出(详情页「导出 JSON」出口)。 */
internal fun SystemRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    logcatTag?.let { put("logcatTag", it) }
    logcatLevel?.let { put("logcatLevel", it) }
    logcatMessage?.let { put("logcatMessage", it) }
    appVersion?.let { put("appVersion", it) }
    appVersionCode?.let { put("appVersionCode", it) }
    appMinSdk?.let { put("appMinSdk", it) }
    appTargetSdk?.let { put("appTargetSdk", it) }
    appPermissions?.let { put("appPermissions", org.json.JSONArray(it)) }
    signatureHash?.let { put("signatureHash", it) }
    signatureBase64?.let { put("signatureBase64", it) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    processName?.let { put("processName", it) }
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)
