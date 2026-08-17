package com.lioisme.modev.hook.webview

import com.google.gson.Gson
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.Hex
import com.lioisme.modev.core.util.StackTraceFilter

/**
 * WebView 探针事件(V12):JS 侧经 [WebviewProbe.BRIDGE_NAME] 桥接回传的结构化载荷。
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

    private val gson = Gson()

    fun parse(json: String?): JsEvent? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, JsEvent::class.java) }.getOrNull()?.takeIf { it.op != null }
    }
}

/**
 * 事件 → 记录映射(纯函数,可 JVM 单测):探针事件落地为
 * kind=WEB_CRYPTO 的标准 [CryptoRecord],复用既有存储/详情页/导出链路(D10)。
 */
object WebViewEventMapper {

    fun toRecord(
        event: JsEvent,
        packageName: String,
        processName: String,
        switch: HookSwitch,
    ): CryptoRecord? {
        val operation = operationOf(event.op) ?: return null
        return CryptoRecord(
            packageName = packageName,
            kind = CryptoKind.WEB_CRYPTO,
            operation = operation,
            algorithm = event.alg?.takeIf { it.isNotBlank() } ?: ALGORITHM_FALLBACK,
            provider = PROVIDER_WEBVIEW,
            direction = event.direction?.takeIf { it in 1..4 },
            iv = bytesViewOf(event.iv, switch),
            key = bytesViewOf(event.key, switch),
            input = bytesViewOfHexOrText(event.inputHex, event.inputText, switch),
            output = bytesViewOfHexOrText(event.outputHex, event.outputText, switch),
            params = event.params?.takeIf { it.isNotEmpty() },
            verifyResult = event.verify,
            processName = processName,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
    }

    /** JS 操作语义 → 既有操作枚举(importKey/generateKey 映射到语义最贴近的既有值)。 */
    private fun operationOf(op: String?): CryptoOperation? = when (op) {
        "encrypt" -> CryptoOperation.ENCRYPT
        "decrypt" -> CryptoOperation.DECRYPT
        "digest" -> CryptoOperation.DIGEST
        "sign" -> CryptoOperation.SIGN
        "verify" -> CryptoOperation.VERIFY
        "importKey" -> CryptoOperation.GET_KEY
        "generateKey" -> CryptoOperation.GENERATE
        else -> null
    }

    private fun bytesViewOf(hex: String?, switch: HookSwitch): BytesView? =
        hex?.let { Hex.decode(it) }?.let { viewOf(it, switch) }

    /** hex 优先;hex 缺失/非法时回退 text(JSEncrypt/CryptoJS 明文路径)。 */
    private fun bytesViewOfHexOrText(hex: String?, text: String?, switch: HookSwitch): BytesView? =
        bytesViewOf(hex, switch)
            ?: text?.toByteArray(Charsets.UTF_8)?.let { viewOf(it, switch) }

    /** 与 BaseHooker.viewOf 同规:maxBytesPerField 双态视图 + 截断保险丝。 */
    private fun viewOf(bytes: ByteArray, switch: HookSwitch): BytesView? {
        val max = switch.maxBytesPerField
        return if (max <= 0) BytesView.of(bytes) else BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
    }

    private const val PROVIDER_WEBVIEW = "WebView"
    private const val ALGORITHM_FALLBACK = "WebView"
}
