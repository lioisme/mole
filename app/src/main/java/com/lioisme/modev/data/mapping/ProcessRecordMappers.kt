package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.ProcessRow
import com.lioisme.modev.domain.record.RowModel

/**
 * 进程控制记录映射:wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [BehaviorRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 */

/** 进程控制记录 → 实体(宿主落库唯一写路径)。 */
internal fun ProcessRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = "",
    exitCode = exitCode,
    processPid = processPid,
    crashType = crashType,
    crashMessage = crashMessage,
    crashBlocked = crashBlocked?.let { if (it) 1 else 0 },
    screenRecordBlocked = screenRecordBlocked?.let { if (it) 1 else 0 },
    interventionAction = interventionAction,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → 进程控制记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toProcessModelOrNull(): ProcessRecord? {
    val kind = enumOf(ProcessKind::class.java, kind) ?: return null
    val operation = enumOf(ProcessOperation::class.java, operation) ?: return null
    return ProcessRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        exitCode = exitCode,
        processPid = processPid,
        crashType = crashType,
        crashMessage = crashMessage,
        crashBlocked = crashBlocked?.let { it == 1 },
        screenRecordBlocked = screenRecordBlocked?.let { it == 1 },
        interventionAction = interventionAction,
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

/** 列表投影行 → 进程控制轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.processRowModelOrNull(): ProcessRow? {
    val kind = enumOf(ProcessKind::class.java, kind) ?: return null
    val operation = enumOf(ProcessOperation::class.java, operation) ?: return null
    return ProcessRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        exitCode = exitCode,
        processPid = processPid,
        crashType = crashType,
        crashBlocked = crashBlocked,
        screenRecordBlocked = screenRecordBlocked,
        interventionAction = interventionAction,
        threadName = threadName,
    )
}

/** 进程控制记录结构化 JSON 导出(详情页「导出 JSON」出口)。 */
internal fun ProcessRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    exitCode?.let { put("exitCode", it) }
    processPid?.let { put("processPid", it) }
    crashType?.let { put("crashType", it) }
    crashMessage?.let { put("crashMessage", it) }
    crashBlocked?.let { put("crashBlocked", it) }
    screenRecordBlocked?.let { put("screenRecordBlocked", it) }
    interventionAction?.let { put("interventionAction", it) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    processName?.let { put("processName", it) }
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)
