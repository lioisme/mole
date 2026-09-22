package com.lioisme.modev.data.mapping

import android.util.Log
import com.lioisme.modev.core.codec.Json
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.data.db.CryptoRecordEntity
import com.lioisme.modev.data.db.CryptoRecordRow
import com.lioisme.modev.domain.record.NetworkRow
import com.lioisme.modev.domain.record.RowModel

/**
 * 网络环境监听记录映射(V30 一等公民):wire 模型 ↔ 实体 ↔ 投影行 ↔ 详情模型,
 * 与 [WebViewRecordMappers]/[PrefsRecordMappers] 同构;枚举严格解析(未知返回 null,绝不误标)。
 *
 * 列复用(ADR-1 单表):algorithm=kind.name;path=url;command=method;
 * content 五元组=requestBody/responseBody;
 * destPath=urlHost;paramsJson=请求头/响应头/证书等;
 * sql=sslProtocol;dbPath=cipherSuite;
 * bindArgsJson=peerCertificatesJson;rowsJson=proxyHost;
 * rowCount=statusCode;affectedCount=proxyPort;rowId=vpnActive(0/1)。
 */
private const val TAG = "NetworkRecordMappers"

/** NetworkRecord → 实体(宿主落库唯一写路径)。 */
internal fun NetworkRecord.toEntity() = CryptoRecordEntity(
    recordType = recordType,
    packageName = packageName,
    kind = kind.name,
    operation = operation.name,
    algorithm = urlHost() ?: "",
    path = url,
    destPath = urlHost(),
    command = method,
    sql = sslProtocol,
    dbPath = cipherSuite,
    requestHeadersJson = requestHeaders?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    responseHeadersJson = responseHeaders?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    peerCertificatesJson = peerCertificates?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    proxyHost = proxyHost,
    proxyPort = proxyPort,
    vpnActive = vpnActive?.let { if (it) 1 else 0 },
    statusCode = statusCode,
    paramsJson = params?.takeIf { it.isNotEmpty() }?.let { Json.gson.toJson(it) },
    processName = processName,
    threadName = threadName,
    stackJson = if (stack.isEmpty()) null else Json.gson.toJson(stack),
    timestamp = timestamp,
    seq = seq,
)

/** 实体 → 网络记录模型;枚举未知或畸形返回 null(丢弃并计数,绝不误标)。 */
internal fun CryptoRecordEntity.toNetworkModelOrNull(): NetworkRecord? {
    val kind = enumOf(NetworkKind::class.java, kind) ?: return null
    val operation = enumOf(NetworkOperation::class.java, operation) ?: return null
    return NetworkRecord(
        id = id,
        recordType = recordType,
        packageName = packageName,
        kind = kind,
        operation = operation,
        url = path,
        method = command,
        statusCode = statusCode,
        requestHeaders = requestHeadersJson?.let { json ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                Json.gson.fromJson(json, Map::class.java) as? Map<String, String>
            }.onFailure { Log.w(TAG, "requestHeadersJson parse failed: $it") }
                .getOrNull()
        },
        responseHeaders = responseHeadersJson?.let { json ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                Json.gson.fromJson(json, Map::class.java) as? Map<String, String>
            }.onFailure { Log.w(TAG, "responseHeadersJson parse failed: $it") }
                .getOrNull()
        },
        sslProtocol = sslProtocol,
        cipherSuite = cipherSuite,
        peerCertificates = peerCertificatesJson?.let { json ->
            runCatching { Json.gson.fromJson(json, Array<String>::class.java).toList() }
                .onFailure { Log.w(TAG, "peerCertificatesJson parse failed: $it") }
                .getOrNull()
        },
        proxyHost = proxyHost,
        proxyPort = proxyPort,
        vpnActive = vpnActive?.let { it == 1 },
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

/** 列表投影行 → 网络轻量展示模型;枚举未知返回 null(丢弃该行)。 */
internal fun CryptoRecordRow.networkRowModelOrNull(): NetworkRow? {
    val kind = enumOf(NetworkKind::class.java, kind) ?: return null
    val operation = enumOf(NetworkOperation::class.java, operation) ?: return null
    return NetworkRow(
        recordId = id,
        recordTimestamp = timestamp,
        packageName = packageName,
        kind = kind,
        operation = operation,
        url = path,
        urlHost = destPath,
        method = command,
        statusCode = rowCount,
        proxyHost = proxyHost,
        vpnActive = vpnActive?.let { it == 1 },
        threadName = threadName,
    )
}

/**
 * 网络记录结构化 JSON 导出(详情页「导出 JSON」出口,与既有五形态同规)。
 */
internal fun NetworkRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("recordType", recordType)
    put("kind", kind.name)
    put("operation", operation.name)
    url?.let { put("url", it) }
    method?.let { put("method", it) }
    statusCode?.let { put("statusCode", it) }
    requestHeaders?.takeIf { it.isNotEmpty() }?.let { put("requestHeaders", org.json.JSONObject(it)) }
    responseHeaders?.takeIf { it.isNotEmpty() }?.let { put("responseHeaders", org.json.JSONObject(it)) }
    sslProtocol?.let { put("sslProtocol", it) }
    cipherSuite?.let { put("cipherSuite", it) }
    peerCertificates?.takeIf { it.isNotEmpty() }?.let { put("peerCertificates", org.json.JSONArray(it)) }
    proxyHost?.let { put("proxyHost", it) }
    proxyPort?.let { put("proxyPort", it) }
    vpnActive?.let { put("vpnActive", it) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    put("processName", processName ?: "")
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
    put("seq", seq)
}.toString(2)
