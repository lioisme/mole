package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.model.RecordTypes

/**
 * ProcessRecord 载荷编解码器:v4 泛型信封下宿主侧以 [RECORD_TYPE]
 * 分发到本编解码器,编码侧(注入)与解码侧(宿主)共用同一 Gson([Json.gson])。
 *
 * 解码校验与 [BehaviorRecordCodec] 同规:kind/operation 缺失或未知、
 * packageName 为空 → 返回 null(畸形载荷丢弃并计数)。
 */
object ProcessRecordCodec {

    const val RECORD_TYPE = RecordTypes.PROCESS_CONTROL

    fun encode(record: ProcessRecord): JsonObject =
        Json.gson.toJsonTree(record).asJsonObject

    fun decode(payload: JsonObject): ProcessRecord? {
        val kind = payload.get("kind")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val operation = payload.get("operation")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (enumOf(ProcessKind::class.java, kind) == null) return null
        if (enumOf(ProcessOperation::class.java, operation) == null) return null
        val record = runCatching { Json.gson.fromJson(payload, ProcessRecord::class.java) }.getOrNull()
            ?: return null
        if (record.packageName.isBlank()) return null
        return record
    }

    private inline fun <reified T : Enum<T>> enumOf(clazz: Class<T>, name: String): T? =
        runCatching { java.lang.Enum.valueOf(clazz, name) }.getOrNull()
}
