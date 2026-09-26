package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import com.lioisme.modev.core.model.WebViewRecord

/**
 * WebViewRecord 载荷编解码器:v4 泛型信封下宿主侧以 [RECORD_TYPE]
 * 分发到本编解码器,编码侧(注入)与解码侧(宿主)共用同一 Gson([Json.gson])
 * 保证双端形状一致。
 *
 * 解码校验与 [CryptoRecordCodec]/[SqlRecordCodec]/[BehaviorRecordCodec]/[PrefsRecordCodec] 同规:
 * kind/operation 缺失或未知、packageName 为空 → 返回 null
 * (畸形载荷丢弃并计数,不进入 ACK 豁免路径)。
 */
object WebViewRecordCodec {

    const val RECORD_TYPE = RecordTypes.WEBVIEW

    /** 模型 → 载荷 JSON(注入侧写前日志 / 复用链路)。 */
    fun encode(record: WebViewRecord): JsonObject =
        Json.gson.toJsonTree(record).asJsonObject

    /** 载荷 JSON → 模型;畸形载荷返回 null(调用方计数丢弃,不进行 ACK 豁免路径)。 */
    fun decode(payload: JsonObject): WebViewRecord? {
        val kind = payload.get("kind")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val operation = payload.get("operation")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (enumOf(WebViewKind::class.java, kind) == null) return null
        if (enumOf(WebViewOperation::class.java, operation) == null) return null
        val record = runCatching { Json.gson.fromJson(payload, WebViewRecord::class.java) }.getOrNull()
            ?: return null
        if (record.packageName.isBlank()) return null
        return record
    }

    private inline fun <reified T : Enum<T>> enumOf(clazz: Class<T>, name: String): T? =
        runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrNull()
}
