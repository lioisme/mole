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

    fun hello(packageName: String, processName: String): String {
        val payload = JsonObject().apply {
            addProperty("magic", WireProtocol.MAGIC)
            addProperty("version", WireProtocol.VERSION)
            addProperty("packageName", packageName)
            addProperty("processName", processName)
        }
        return envelope(WireProtocol.TYPE_HELLO, payload)
    }

    fun config(packageName: String, switch: HookSwitch): String {
        val payload = JsonObject().apply {
            addProperty("packageName", packageName)
            add("switch", gson.toJsonTree(switch))
        }
        return envelope(WireProtocol.TYPE_CONFIG, payload)
    }

    fun record(record: CryptoRecord): String =
        envelope(WireProtocol.TYPE_RECORD, gson.toJsonTree(record).asJsonObject)

    fun ping(): String = envelope(WireProtocol.TYPE_PING, null)

    fun pong(): String = envelope(WireProtocol.TYPE_PONG, null)

    fun configOf(payload: JsonObject?): HookSwitch? =
        payload?.get("switch")?.let { runCatching { gson.fromJson(it, HookSwitch::class.java) }.getOrNull() }

    fun packageNameOf(payload: JsonObject?): String? = payload?.get("packageName")?.asString

    fun recordOf(payload: JsonObject?): CryptoRecord? {
        if (payload == null) return null
        val record = runCatching { gson.fromJson(payload, CryptoRecord::class.java) }.getOrNull() ?: return null
        if (record.packageName == null || record.kind == null || record.operation == null) return null
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