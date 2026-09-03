package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.core.model.SystemRecord

/**
 * SystemRecord 载荷编解码器(V33 系统信息监听):v4 泛型信封下宿主侧以 [RECORD_TYPE]
 * 分发到本编解码器,编码侧(注入)与解码侧(宿主)共用同一 Gson([Json.gson])。
 *
 * 解码校验与 [BehaviorRecordCodec] 同规:kind/operation 缺失或未知、
 * packageName 为空 → 返回 null(畸形载荷丢弃并计数)。
 */
object SystemRecordCodec {

    const val RECORD_TYPE = RecordTypes.SYSTEM

    fun encode(record: SystemRecord): JsonObject =
        Json.gson.toJsonTree(record).asJsonObject

    fun decode(payload: JsonObject): SystemRecord? {
        val kind = payload.get("kind")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val operation = payload.get("operation")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (enumOf(SystemKind::class.java, kind) == null) return null
        if (enumOf(SystemOperation::class.java, operation) == null) return null
        val record = runCatching { Json.gson.fromJson(payload, SystemRecord::class.java) }.getOrNull()
            ?: return null
        if (record.packageName.isBlank()) return null
        return record
    }

    private inline fun <reified T : Enum<T>> enumOf(clazz: Class<T>, name: String): T? =
        runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrNull()
}
