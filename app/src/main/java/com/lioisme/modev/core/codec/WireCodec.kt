package com.lioisme.modev.core.codec

import com.google.gson.JsonObject
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes

/**
 * 行协议编解码(v4,ADR-1)。双端共用同一 Gson([Json.gson]),保证结构一致。
 *
 * v4 起 record 语义泛型化:[record] 接收已编码的 [JsonObject] 载荷
 * (由对应监听能力编解码器产出,[CryptoRecordCodec.encode]),
 * [seq]/[recordType] 由载荷自身承载,编码侧强制统一后宿主按类型分发;
 * 解码侧 [recordOf] 返回类型化信封 [WireRecord],再经 [RecordCodecRegistry] 解码。
 */
object WireCodec {

    /** 解析一行协议消息;格式非法时返回 null。 */
    fun parse(line: String): WireMessage? = runCatching {
        val obj = Json.gson.fromJson(line, JsonObject::class.java)
        val type = obj.get(WireProtocol.KEY_TYPE)?.asString ?: return null
        val payload = obj.get(WireProtocol.KEY_PAYLOAD)?.asJsonObject
        WireMessage(type, payload)
    }.getOrNull()

    fun hello(packageName: String, processName: String, token: String? = null): String {
        val payload = JsonObject().apply {
            addProperty("magic", WireProtocol.MAGIC)
            addProperty("version", WireProtocol.VERSION)
            addProperty("packageName", packageName)
            addProperty("processName", processName)
            if (token != null) addProperty(WireProtocol.KEY_TOKEN, token)
        }
        return envelope(WireProtocol.TYPE_HELLO, payload)
    }

    fun config(packageName: String, switch: HookSwitch): String {
        val payload = JsonObject().apply {
            addProperty("magic", WireProtocol.MAGIC)
            addProperty("version", WireProtocol.VERSION)
            addProperty("packageName", packageName)
            add("switch", Json.gson.toJsonTree(switch))
        }
        return envelope(WireProtocol.TYPE_CONFIG, payload)
    }

    /**
     * 记录编码:写入类型化信封。载荷由监听能力编解码器产出([CryptoRecordCodec.encode]);
     * [seq] 与 [recordType] 强制写入载荷(与模型字段同值),保证宿主侧分发依据始终存在。
     */
    fun record(seq: Long, recordType: String, payload: JsonObject): String {
        payload.addProperty(WireProtocol.KEY_SEQ, seq)
        payload.addProperty(WireProtocol.KEY_RECORD_TYPE, recordType)
        return envelope(WireProtocol.TYPE_RECORD, payload)
    }

    /** 宿主 → 注入侧:批量落库确认,seq 及之前的记录已持久化。 */
    fun ack(seq: Long): String = envelope(
        WireProtocol.TYPE_ACK,
        JsonObject().apply { addProperty(WireProtocol.KEY_SEQ, seq) },
    )

    fun ping(): String = envelope(WireProtocol.TYPE_PING, null)

    fun pong(): String = envelope(WireProtocol.TYPE_PONG, null)

    fun configOf(payload: JsonObject?): HookSwitch? =
        payload?.get("switch")?.let { runCatching { HookSwitch.fromJson(Json.gson, it.toString()) }.getOrNull() }

    /** 读取 ack 载荷中的确认游标;缺失或非法时返回 null。 */
    fun seqOf(payload: JsonObject?): Long? =
        payload?.get(WireProtocol.KEY_SEQ)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asLong

    fun packageNameOf(payload: JsonObject?): String? = payload?.get("packageName")?.asString

    /** 解析 hello 中的协议版本;缺失或非法时返回 null。 */
    fun versionOf(payload: JsonObject?): Int? = payload?.get(WireProtocol.KEY_VERSION)?.takeIf { it.isJsonPrimitive }?.asInt

    /** 解析 hello 中的协议魔数;缺失时返回 null。 */
    fun magicOf(payload: JsonObject?): String? = payload?.get(WireProtocol.KEY_MAGIC)?.asString

    /** 校验握手载荷的 magic/version 是否与协议定义一致。 */
    fun isProtocolCompatible(payload: JsonObject?): Boolean =
        magicOf(payload) == WireProtocol.MAGIC && versionOf(payload) == WireProtocol.VERSION

    /**
     * 解析 record 载荷为类型化信封;recordType 缺失/空白或 seq 非法时返回 null。
     * 信封解码(载荷 → 业务模型)由宿主 [RecordCodecRegistry] 按类型完成。
     */
    fun recordOf(payload: JsonObject?): WireRecord? {
        if (payload == null) return null
        val type = payload.get(WireProtocol.KEY_RECORD_TYPE)
            ?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isNumber }
            ?.asString
        if (type.isNullOrBlank()) return null
        val seq = payload.get(WireProtocol.KEY_SEQ)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asLong ?: return null
        return WireRecord(seq = seq, recordType = type, payload = payload)
    }

    private fun envelope(type: String, payload: JsonObject?): String {
        val obj = JsonObject()
        obj.addProperty(WireProtocol.KEY_TYPE, type)
        if (payload != null) obj.add(WireProtocol.KEY_PAYLOAD, payload)
        return obj.toString()
    }
}

/**
 * 一条已解析的协议消息。
 */
data class WireMessage(
    val type: String,
    val payload: JsonObject?,
)
