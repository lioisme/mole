package com.lioisme.modev.hook.webview

import android.webkit.JavascriptInterface
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.hook.RecordSink

/**
 * JS 桥(V29 重构):经 addJavascriptInterface 注入页面全局 [WebviewProbe.BRIDGE_NAME]。
 * - [report] 运行于 WebView 私有线程,经解析 + 映射后走 [RecordSink.emit](线程安全);
 *   畸形载荷静默丢弃,不抛异常、不影响页面;
 * - [maxHexChars] 为 JS 侧数据量预截断闸(hex 字符数;0 = 不限制),读配置实时生效。
 */
class WebViewJsBridge(
    private val config: () -> HookSwitch,
    private val sink: RecordSink,
    private val packageName: String,
    private val processName: String,
) {

    /** JS 侧事件回传(开关关闭时零记录开销;畸形载荷静默丢弃)。 */
    @JavascriptInterface
    fun report(payload: String?) {
        if (payload.isNullOrBlank()) return
        val switch = config()
        if (!switch.webviewEnabled(WebViewKind.JS_CRYPTO)) return
        val event = WebViewEventParser.parse(payload) ?: return
        val record = WebViewEventMapper.toRecord(event, packageName, processName, switch) ?: return
        sink.emit(record)
    }

    /** JS 侧 hex 预截断上限(字符数);maxBytesPerField=0 时返回 0(不限制)。 */
    @JavascriptInterface
    fun maxHexChars(): Int {
        val max = config().maxBytesPerField
        return if (max <= 0) 0 else max * 2
    }
}
