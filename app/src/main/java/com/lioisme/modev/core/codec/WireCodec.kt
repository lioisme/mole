package com.lioisme.modev.core.codec

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch

/**
 * 行协议编解码。使用 Gson 处理 JSON 序列化,双端共用,保证结构一致。
 */
object WireCodec {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    /** 解析一行协议消息;格式非法时返回 null。 */
    fun parse(line: String): WireMessage? = runCatching {
        val obj = gson.fromJson(line, JsonObject::class.java)
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
            add("switch", gson.toJsonTree(switch))
        }
        return envelope(WireProtocol.TYPE_CONFIG, payload)
    }

    fun record(record: CryptoRecord): String =
        envelope(WireProtocol.TYPE_RECORD, gson.toJsonTree(record).asJsonObject)

    /** 宿主 → 注入侧:批量落库确认,seq 及之前的记录已持久化。 */
    fun ack(seq: Long): String = envelope(
        WireProtocol.TYPE_ACK,
        JsonObject().apply { addProperty(WireProtocol.KEY_SEQ, seq) },
    )

    fun ping(): String = envelope(WireProtocol.TYPE_PING, null)

    fun pong(): String = envelope(WireProtocol.TYPE_PONG, null)

    fun configOf(payload: JsonObject?): HookSwitch? =
        payload?.get("switch")?.let { runCatching { gson.fromJson(it, HookSwitch::class.java) }.getOrNull() }

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
    fun isHelloCompatible(payload: JsonObject?): Boolean =
        magicOf(payload) == WireProtocol.MAGIC && versionOf(payload) == WireProtocol.VERSION

    fun recordOf(payload: JsonObject?): CryptoRecord? {
        if (payload == null) return null
        val record = runCatching { gson.fromJson(payload, CryptoRecord::class.java) }.getOrNull() ?: return null
        if (record.kind == null || record.operation == null) return null
        if (record.packageName.isBlank()) return null
        // algorithm 允许为空:SecureRandom/内部随机源等合法记录没有算法名
        // V10 前瞻:非 crypto 记录(未来能力)无法映射为 CryptoRecord,此处按非法丢弃,
        // 待对应能力实现后由类型化分发接管(RecordTypes)。
        return record
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