package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.DeviceEnvRow
import com.lioisme.modev.domain.record.RowModel

/**
 * 设备环境监听记录映射:wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [NetworkRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 *
 * 列复用(ADR-1 单表,**本段与下方实现逐行对齐** —— V47 T2 修正此前注释与实现的
 * 四处漂移,漂移会让读代码的人按错的列去查数据):
 *   algorithm=targetSummary;path=targetPath;destPath=targetClass;
 *   command=targetProperty;sql=detectionPattern;
 *   proxyHost=originalValue(干预前原值);maskedValue=干预后值(V47 T2 新增列,
 *     两者配对 = 伪装的双侧 oracle);
 *   vpnActive=intervention(0/1);interventionAction=干预动作;paramsJson=params;
 *   processName/threadName/timestamp/seq 同名列。
 */
private const val TAG = "DeviceEnvRecordMappers"

/** DeviceEnvRecord → 实体(宿主落库唯一写路径)。 */
internal fun DeviceEnvRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = targetSummary() ?: "",
    path = targetPath,
    destPath = targetClass,
    command = targetProperty,
    sql = detectionPattern,
    dbPath = null,
    statusCode = null,
    requestHeadersJson = null,
    responseHeadersJson = null,
    peerCertificatesJson = null,
    proxyHost = originalValue,
    maskedValue = maskedValue?.takeIf { it.isNotEmpty() },
    proxyPort = null,
    vpnActive = if (intervention) 1 else 0,
    interventionAction = interventionAction,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → 设备环境记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toDeviceEnvModelOrNull(): DeviceEnvRecord? {
    val kind = enumOf(DeviceEnvKind::class.java, kind) ?: return null
    val operation = enumOf(DeviceEnvOperation::class.java, operation) ?: return null
    return DeviceEnvRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        originalValue = proxyHost,
        maskedValue = maskedValue,
        intervention = vpnActive?.let { it == 1 } ?: true,
        interventionAction = interventionAction,
        targetPath = path,
        targetClass = destPath,
        targetProperty = command,
        detectionPattern = sql,
        params = paramsJson?.let { json ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                Json.gson.fromJson(json, Map::class.java) as? Map<String, String>
            }.onFailure { Log.w(TAG, "paramsJson parse failed: $it") }
                .getOrNull()
        },
        processName = processName ?: "",
        threadName = threadName,
        stack = stackJson?.let { json ->
            runCatching { Json.gson.fromJson(json, Array<String>::class.java).toList() }
                .onFailure { Log.w(TAG, "stackJson parse failed: $it") }
                .getOrNull()
        } ?: emptyList(),
        timestamp = timestamp,
        seq = seq ?: 0L,
    )
}

/** 列表投影行 → 设备环境轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.deviceEnvRowModelOrNull(): DeviceEnvRow? {
    val kind = enumOf(DeviceEnvKind::class.java, kind) ?: return null
    val operation = enumOf(DeviceEnvOperation::class.java, operation) ?: return null
    return DeviceEnvRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        targetPath = path,
        targetClass = destPath,
        targetProperty = command,
        intervention = vpnActive?.let { it == 1 } ?: true,
        interventionAction = interventionAction,
        threadName = threadName,
    )
}

/**
 * 设备环境记录结构化 JSON 导出(详情页「导出 JSON」出口,org.json 随 SDK 提供)。
 */
internal fun DeviceEnvRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    put("intervention", intervention)
    interventionAction?.let { put("interventionAction", it) }
    originalValue?.let { put("originalValue", it) }
    maskedValue?.let { put("maskedValue", it) }
    targetPath?.let { put("targetPath", it) }
    targetClass?.let { put("targetClass", it) }
    targetProperty?.let { put("targetProperty", it) }
    detectionPattern?.let { put("detectionPattern", it) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    put("processName", processName ?: "")
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)
