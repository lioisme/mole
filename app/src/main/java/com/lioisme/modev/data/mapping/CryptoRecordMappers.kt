package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SqlResultCell
import com.lioisme.modev.core.model.SqlResultRow
import com.lioisme.modev.core.model.SqlValueType
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow

/**
 * 统一 Gson([Json.gson])实例,映射层确保单例不覆盖。
 *
 * 枚举解析严格:未知枚举值返回 null 而非报错(防止乱码 MESSAGE_DIGEST),
 * 由调用方负责降级处理(枚举漂移或 Schema 演进时,DB 内部兼容)。
 *
 * 数据库监听(V17):[SqlRecord] 与 [CryptoRecord] 共存于 crypto_records
 * (ADR-1),投影按 [RecordType] 分派为 [RowModel];详情按 [RecordDetailModel]。
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
    keyB64 = key?.base64,
    keyText = key?.text,
    keyLength = key?.totalLength,
    keyTruncated = key?.truncated ?: false,
    ivHex = iv?.hex,
    ivB64 = iv?.base64,
    ivText = iv?.text,
    ivLength = iv?.totalLength,
    ivTruncated = iv?.truncated ?: false,
    keyAlias = keyAlias,
    keyExportable = keyExportable,
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
    saltHex = salt?.hex,
    saltB64 = salt?.base64,
    saltText = salt?.text,
    saltLength = salt?.totalLength,
    saltTruncated = salt?.truncated ?: false,
    aadHex = aad?.hex,
    aadB64 = aad?.base64,
    aadText = aad?.text,
    aadLength = aad?.totalLength,
    aadTruncated = aad?.truncated ?: false,
    publicKeyHex = publicKey?.hex,
    publicKeyB64 = publicKey?.base64,
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
        key = view(keyHex, keyB64, keyText, keyLength, keyTruncated),
        iv = view(ivHex, ivB64, ivText, ivLength, ivTruncated),
        keyAlias = keyAlias,
        keyExportable = keyExportable,
        input = view(inputHex, inputB64, inputText, inputLength, inputTruncated),
        output = view(outputHex, outputB64, outputText, outputLength, outputTruncated),
        salt = view(saltHex, saltB64, saltText, saltLength, saltTruncated),
        aad = view(aadHex, aadB64, aadText, aadLength, aadTruncated),
        publicKey = view(publicKeyHex, publicKeyB64, publicKeyText, publicKeyLength, publicKeyTruncated),
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
internal fun CryptoRecordRow.toRowModelOrNull(): RowModel? = when (recordType) {
    RecordTypes.CRYPTO -> cryptoRowModelOrNull()
    RecordTypes.DATABASE -> sqlRowModelOrNull()
    RecordTypes.BEHAVIOR -> behaviorRowModelOrNull()
    RecordTypes.PREFS -> prefsRowModelOrNull()
    RecordTypes.WEBVIEW -> webviewRowModelOrNull()
    else -> null
}

private fun CryptoRecordRow.cryptoRowModelOrNull(): CryptoRow? {
    val kind = enumOf(CryptoKind::class.java, kind) ?: return null
    val operation = enumOf(CryptoOperation::class.java, operation) ?: return null
    return CryptoRow(
        CryptoRecord(
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
        ),
    )
}

private fun CryptoRecordRow.sqlRowModelOrNull(): SqlRow? {
    val kind = enumOf(SqlKind::class.java, kind) ?: return null
    val operation = enumOf(SqlOperation::class.java, operation) ?: return null
    return SqlRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        databaseName = algorithm,
        sqlPreview = sqlPreview,
        rowCount = rowCount,
        affectedCount = affectedCount,
        rowId = rowId,
        threadName = threadName,
    )
}

/** 实体 → 数据库记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toSqlModelOrNull(): SqlRecord? {
    val kind = enumOf(SqlKind::class.java, kind) ?: return null
    val operation = enumOf(SqlOperation::class.java, operation) ?: return null
    return SqlRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        databaseName = algorithm,
        provider = provider,
        dbPath = dbPath,
        sql = sql,
        bindArgs = bindArgsJson?.let(::parseBindArgs),
        rows = rowsJson?.let(::parseRows),
        rowCount = rowCount,
        affectedCount = affectedCount,
        rowId = rowId,
        resultText = resultText,
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

/** 实体 → 详情模型(按 recordType 分派;未知类型返回 null,绝不误标)。 */
internal fun CryptoRecordEntity.toRecordDetailOrNull(): RecordDetailModel? = when (recordType) {
    RecordTypes.CRYPTO -> toModelOrNull()?.let(::CryptoDetail)
    RecordTypes.DATABASE -> toSqlModelOrNull()?.let(::SqlDetail)
    RecordTypes.BEHAVIOR -> toBehaviorModelOrNull()?.let(::BehaviorDetail)
    RecordTypes.PREFS -> toPrefsModelOrNull()?.let(::PrefsDetail)
    RecordTypes.WEBVIEW -> toWebViewModelOrNull()?.let(::WebViewDetail)
    else -> null
}

private fun parseBindArgs(json: String): List<SqlBindValue>? =
    runCatching { Json.gson.fromJson(json, Array<SqlBindValue>::class.java).toList() }
        .onFailure { Log.w("Modev", "bindArgsJson parse failed: $it") }
        .getOrNull()

private fun parseRows(json: String): List<SqlResultRow>? =
    runCatching { Json.gson.fromJson(json, Array<SqlResultRow>::class.java).toList() }
        .onFailure { Log.w("Modev", "rowsJson parse failed: $it") }
        .getOrNull()

internal fun view(hex: String?, base64: String?, text: String?, length: Int?, truncated: Boolean): BytesView? =
    if (hex == null) null else BytesView(hex = hex, base64 = base64, text = text, totalLength = length ?: 0, truncated = truncated)

/** 投影预览 → 展示 BytesView(仅存在于列表行;totalLength 由 hex 长度推断,仅供摘要展示)。 */
private fun previewView(hex: String?, truncated: Boolean): BytesView? =
    if (hex == null) null else BytesView(hex = hex, text = null, totalLength = hex.length / 2, truncated = truncated)

internal inline fun <reified T : Enum<T>> enumOf(clazz: Class<T>, name: String): T? =
    runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrNull()

/**
 * 结构化 JSON 导出(L8 自 UI 移入映射层,2026-08-17):
 * 详情页「导出 JSON」唯一出口,org.json 随 Android SDK 提供。 */
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

internal fun BytesView.toJsonObject(name: String): org.json.JSONObject = org.json.JSONObject().apply {
    put("name", name)
    put("hex", hex)
    base64?.let { put("base64", it) }
    text?.let { put("text", it) }
    put("totalLength", totalLength)
    put("truncated", truncated)
}

/**
 * 数据库记录 → 实体(宿主落库唯一写路径,与 [CryptoRecord.toEntity] 同表,
 * ADR-1 单表扩展;bindArgs/rows 以 JSON 数组落列)。 */
internal fun SqlRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = databaseName,
    provider = provider,
    dbPath = dbPath,
    sql = sql,
    bindArgsJson = bindArgs?.let { Json.gson.toJson(it) },
    rowsJson = rows?.let { Json.gson.toJson(it) },
    rowCount = rowCount,
    affectedCount = affectedCount,
    rowId = rowId,
    resultText = resultText,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    contextId = contextId,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/**
 * 数据库记录结构化 JSON 导出(详情页「导出 JSON」出口,org.json 随 SDK 提供):
 * 与 [CryptoRecord.toJson] 并列的双形态唯一出口。 */
internal fun SqlRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    put("databaseName", databaseName)
    dbPath?.let { put("dbPath", it) }
    sql?.let { put("sql", it) }
    bindArgs?.let {
        put("bindArgs", org.json.JSONArray(it.map { v -> v.toJsonObject() }))
    }
    rows?.let {
        val arr = org.json.JSONArray()
        it.forEach { row -> arr.put(row.toJsonObject()) }
        put("rows", arr)
    }
    rowCount?.let { put("rowCount", it) }
    affectedCount?.let { put("affectedCount", it) }
    rowId?.let { put("rowId", it) }
    resultText?.let { put("resultText", it) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    processName?.let { put("processName", it) }
    put("contextId", contextId)
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)

private fun SqlBindValue.toJsonObject(): org.json.JSONObject = org.json.JSONObject().apply {
    put("i", i)
    put("t", t.name)
    when (t) {
        SqlValueType.L, SqlValueType.D, SqlValueType.S -> v?.let { put("v", it) }
        SqlValueType.B -> hex?.let { put("hex", it) }
        SqlValueType.N -> {}
    }
    len?.let { put("len", it) }
    if (truncated) put("truncated", true)
}

private fun SqlResultRow.toJsonObject(): org.json.JSONObject = org.json.JSONObject().apply {
    val cells = org.json.JSONArray()
    c.forEach { cell -> cells.put(cell.toJsonObject()) }
    put("c", cells)
}

private fun SqlResultCell.toJsonObject(): org.json.JSONObject = org.json.JSONObject().apply {
    put("n", n)
    put("t", t.name)
    when (t) {
        SqlValueType.L, SqlValueType.D, SqlValueType.S -> v?.let { put("v", it) }
        SqlValueType.B -> hex?.let { put("hex", it) }
        SqlValueType.N -> {}
    }
    len?.let { put("len", it) }
    if (truncated) put("truncated", true)
}





