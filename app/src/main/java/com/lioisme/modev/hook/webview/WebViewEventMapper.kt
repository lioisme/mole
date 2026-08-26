package com.lioisme.modev.hook.webview

import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import com.lioisme.modev.core.model.WebViewRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.Hex
import com.lioisme.modev.core.util.StackTraceFilter

/**
 * 事件 → WebView 记录映射(V29 重构,纯函数,可 JVM 单测):探针事件落地为
 * kind=JS_CRYPTO 的标准 [WebViewRecord],复用既有存储/详情页/导出链路。
 */
object WebViewEventMapper {

    fun toRecord(
        event: JsEvent,
        packageName: String,
        processName: String,
        switch: HookSwitch,
    ): WebViewRecord? {
        val operation = operationOf(event.op) ?: return null
        return WebViewRecord(
            packageName = packageName,
            processName = processName,
            kind = WebViewKind.JS_CRYPTO,
            operation = operation,
            params = event.params?.takeIf { it.isNotEmpty() },
            input = bytesViewOfHexOrText(event.inputHex, event.inputText, switch),
            output = bytesViewOfHexOrText(event.outputHex, event.outputText, switch),
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
    }

    /** JS 操作语义 → WebView 操作枚举。 */
    private fun operationOf(op: String?): WebViewOperation? = when (op) {
        "encrypt" -> WebViewOperation.JS_ENCRYPT
        "decrypt" -> WebViewOperation.JS_DECRYPT
        "digest" -> WebViewOperation.JS_DIGEST
        "sign" -> WebViewOperation.JS_SIGN
        "verify" -> WebViewOperation.JS_VERIFY
        "importKey" -> WebViewOperation.JS_IMPORT_KEY
        "generateKey" -> WebViewOperation.JS_GENERATE_KEY
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
}
