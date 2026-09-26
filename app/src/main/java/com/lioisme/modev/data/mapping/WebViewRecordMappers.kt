package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import com.lioisme.modev.core.model.WebViewRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.WebViewRow
import com.lioisme.modev.domain.record.RowModel

/**
 * WebView 记录映射:wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [PrefsRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 *
 * 列复用(ADR-1 单表):path=url;content 五元组=script/input/output;
 * destPath=urlHost;command=bridgeName。
 */

/** WebView 记录 → 实体(宿主落库唯一写路径)。 */
internal fun WebViewRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = urlHost ?: "",
    path = url,
    destPath = urlHost,
    contentHex = script?.hex,
    contentB64 = script?.base64,
    contentText = script?.text,
    contentLength = script?.totalLength,
    contentTruncated = script?.truncated ?: false,
    inputHex = input?.hex,
    inputB64 = input?.base64,
    inputText = input?.text,
    inputLength = input?.totalLength,
    inputTruncated = input?.truncated ?: false,
    outputHex = output?.hex,
    outputB64 = output?.base64,
    outputText = output?.text,
    outputLength = output?.totalLength,
    outputTruncated = output?.truncated ?: false,
    command = bridgeName,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → WebView 记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toWebViewModelOrNull(): WebViewRecord? {
    val kind = enumOf(WebViewKind::class.java, kind) ?: return null
    val operation = enumOf(WebViewOperation::class.java, operation) ?: return null
    return WebViewRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        url = path,
        urlHost = destPath,
        script = view(contentHex, contentB64, contentText, contentLength, contentTruncated),
        input = view(inputHex, inputB64, inputText, inputLength, inputTruncated),
        output = view(outputHex, outputB64, outputText, outputLength, outputTruncated),
        bridgeName = command,
        params = paramsJson?.let { json ->
            runCatching { Json.gson.fromJson(json, Map::class.java) as Map<String, String> }
                .onFailure { Log.w("Modev", "paramsJson parse failed: $it") }
                .getOrNull()
        },
        processName = processName ?: "",
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

/** 列表投影行 → WebView 轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.webviewRowModelOrNull(): WebViewRow? {
    val kind = enumOf(WebViewKind::class.java, kind) ?: return null
    val operation = enumOf(WebViewOperation::class.java, operation) ?: return null
    return WebViewRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        url = path,
        urlHost = destPath,
        bridgeName = command,
        threadName = threadName,
    )
}

/**
 * WebView 记录结构化 JSON 导出(详情页「导出 JSON」出口,与既有四形态同规)。
 */
internal fun WebViewRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    url?.let { put("url", it) }
    urlHost?.let { put("urlHost", it) }
    script?.let { put("script", it.toJsonObject("script")) }
    bridgeName?.let { put("bridgeName", it) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    input?.let { put("input", it.toJsonObject("input")) }
    output?.let { put("output", it.toJsonObject("output")) }
    debugEnabled?.let { put("debugEnabled", it) }
    put("processName", processName)
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)
