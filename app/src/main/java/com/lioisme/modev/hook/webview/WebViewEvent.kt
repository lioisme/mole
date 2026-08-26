package com.lioisme.modev.hook.webview

import com.lioisme.modev.core.codec.Json

/**
 * WebView 探针事件(V12→V29):JS 侧经 [WebviewProbe.BRIDGE_NAME] 桥接回传的结构化载荷。
 * 字段名与探针 JSON 键一一对应(Gson 直接映射);hex 数据已在 JS 侧按
 * `maxHexChars()` 预截断,Java 侧 [WebViewEventMapper] 再经 [BytesView.of] 保险丝。
 */
data class JsEvent(
    val op: String? = null,
    val alg: String? = null,
    val direction: Int? = null,
    val iv: String? = null,
    val key: String? = null,
    val inputHex: String? = null,
    val inputText: String? = null,
    val outputHex: String? = null,
    val outputText: String? = null,
    val verify: Boolean? = null,
    val params: Map<String, String>? = null,
)

/** 事件解析:畸形载荷返回 null,静默丢弃(与 WireCodec.recordOf 同规)。 */
object WebViewEventParser {

    fun parse(json: String?): JsEvent? {
        if (json.isNullOrBlank()) return null
        return runCatching { Json.gson.fromJson(json, JsEvent::class.java) }.getOrNull()?.takeIf { it.op != null }
    }
}
