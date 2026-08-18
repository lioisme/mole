package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow

/**
 * 实体 ↔ 模型映射唯一实现(ADR-4):全字段双向映射集中于此,
 * 单一 Gson([Json.gson])实例,映射正确性由单测覆盖。
 *
 * 枚举解析严格化:未知枚举值返回 null 而非回退首项(禁止误标 MESSAGE_DIGEST),
 * 由调用方计数丢弃并报警(枚举漂移仅发生在 Schema 演进时,DB 内不常见)。
 */

/** 模型 → 实体(宿主落库唯一写路径)。 */
internal fun CryptoRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = algorithm,
    provider = provider,
    direction = direction,
    keyHex = key?.hex,
    keyText = key?.text,
    keyLength = key?.totalLength,
    keyTruncated = key?.truncated ?: false,
    ivHex = iv?.hex,
    ivText = iv?.text,
    ivLength = iv?.totalLength,
    ivTruncated = iv?.truncated ?: false,
    keyAlias = keyAlias,
    keyExportable = keyExportable,
    inputHex = input?.hex,
    inputText = input?.text,
    inputLength = input?.totalLength,
    inputTruncated = input?.truncated ?: false,
    outputHex = output?.hex,
    outputText = output?.text,
    outputLength = output?.totalLength,
    outputTruncated = output?.truncated ?: false,
    saltHex = salt?.hex,
    saltText = salt?.text,
    saltLength = salt?.totalLength,
    saltTruncated = salt?.truncated ?: false,
    aadHex = aad?.hex,
    aadText = aad?.text,
    aadLength = aad?.totalLength,
    aadTruncated = aad?.truncated ?: false,
    publicKeyHex = publicKey?.hex,
    publicKeyText = publicKey?.text,
    publicKeyLength = publicKey?.totalLength,
    publicKeyTruncated = publicKey?.truncated ?: false,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    verifyResult = verifyResult,
    processName = processName,
    contextId = contextId,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → 模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toModelOrNull(): CryptoRecord? {
    val kind = enumOf(CryptoKind::class.java, kind) ?: return null
    val operation = enumOf(CryptoOperation::class.java, operation) ?: return null
    return CryptoRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        algorithm = algorithm,
        provider = provider,
        direction = direction,
        key = view(keyHex, keyText, keyLength, keyTruncated),
        iv = view(ivHex, ivText, ivLength, ivTruncated),
        keyAlias = keyAlias,
        keyExportable = keyExportable,
        input = view(inputHex, inputText, inputLength, inputTruncated),
        output = view(outputHex, outputText, outputLength, outputTruncated),
        salt = view(saltHex, saltText, saltLength, saltTruncated),
        aad = view(aadHex, aadText, aadLength, aadTruncated),
        publicKey = view(publicKeyHex, publicKeyText, publicKeyLength, publicKeyTruncated),
        params = paramsJson?.let { json ->
            runCatching { Json.gson.fromJson(json, Map::class.java) as Map<String, String> }
                .onFailure { Log.w("Modev", "paramsJson parse failed: $it") }
                .getOrNull()
        },
        verifyResult = verifyResult,
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

/** 列表投影行 → 轻量展示模型;枚举未知返回 null(丢弃该行,仅影响列表摘要)。 */
internal fun CryptoRecordRow.toRowModelOrNull(): CryptoRecord? {
    val kind = enumOf(CryptoKind::class.java, kind) ?: return null
    val operation = enumOf(CryptoOperation::class.java, operation) ?: return null
    return CryptoRecord(
        id = id,
        packageName = packageName,
        kind = kind,
        operation = operation,
        algorithm = algorithm,
        direction = direction,
        keyAlias = keyAlias,
        key = previewView(keyPreview, keyTruncated),
        input = previewView(inputPreview, inputTruncated),
        output = previewView(outputPreview, outputTruncated),
        threadName = threadName,
        timestamp = timestamp,
    )
}

private fun view(hex: String?, text: String?, length: Int?, truncated: Boolean): BytesView? =
    if (hex == null) null else BytesView(hex = hex, text = text, totalLength = length ?: 0, truncated = truncated)

/** 投影预览 → 展示 BytesView(仅存在于列表行;totalLength 由 hex 长度推断,仅供摘要展示)。 */
private fun previewView(hex: String?, truncated: Boolean): BytesView? =
    if (hex == null) null else BytesView(hex = hex, text = null, totalLength = hex.length / 2, truncated = truncated)

private inline fun <reified T : Enum<T>> enumOf(clazz: Class<T>, name: String): T? =
    runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrNull()

/**
 * 结构化 JSON 导出(L8 自 UI 移入映射层,2026-08-17):
 * 详情页「导出 JSON」唯一出口,org.json 随 Android SDK 提供。
 */
internal fun CryptoRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("kind", kind.name)
    put("operation", operation.name)
    put("algorithm", algorithm)
    provider?.let { put("provider", it) }
    direction?.let { put("direction", it) }
    key?.let { put("key", it.toJsonObject("key")) }
    keyAlias?.let { put("keyAlias", it) }
    put("keyExportable", keyExportable)
    iv?.let { put("iv", it.toJsonObject("iv")) }
    salt?.let { put("salt", it.toJsonObject("salt")) }
    aad?.let { put("aad", it.toJsonObject("aad")) }
    publicKey?.let { put("publicKey", it.toJsonObject("publicKey")) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    verifyResult?.let { put("verifyResult", it) }
    processName?.let { put("processName", it) }
    input?.let { put("input", it.toJsonObject("input")) }
    output?.let { put("output", it.toJsonObject("output")) }
    put("contextId", contextId)
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
}.toString(2)

private fun BytesView.toJsonObject(name: String): org.json.JSONObject = org.json.JSONObject().apply {
    put("name", name)
    put("hex", hex)
    text?.let { put("text", it) }
    put("totalLength", totalLength)
    put("truncated", truncated)
}