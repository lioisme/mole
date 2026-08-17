package com.lioisme.modev.hook.hooks

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.RecordSink
import com.lioisme.modev.hook.webview.WebViewJsBridge
import com.lioisme.modev.hook.webview.WebviewProbe
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * WebView JS 加解密监听(V12):注入探针包装 WebCrypto / CryptoJS / JSEncrypt,
 * 经 JS 桥回传记录(kind=WEB_CRYPTO)。注入面(D5):
 * 1. 基类 [android.webkit.WebViewClient.onPageFinished] —— 页面加载完成后注入探针
 *    (覆盖绝大多数"复写并调 super"的自定义客户端);
 * 2. WebView.<init> / loadUrl / loadData / loadDataWithBaseURL / evaluateJavascript
 *    —— 尽早注册 JS 桥(SPA 动态脚本与不复写 onPageFinished 的客户端兜底)。
 * 全部 hook 经 [MethodFinder] 空安全 + runCatching 包裹,零异常外泄宿主;
 * 探针幂等守卫在 JS 侧([WebviewProbe.IDEMPOTENCE_GUARD]),重复注入零副作用。
 */
class WebViewHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.WEB_CRYPTO) {

    private val bridge by lazy { WebViewJsBridge(config, sink, packageName, processName) }

    /** 已注册桥的 WebView 弱引用集合(幂等,不阻止 GC)。 */
    private val bridgedViews = Collections.newSetFromMap(
        Collections.synchronizedMap(WeakHashMap<WebView, Boolean>()),
    )

    override fun install() {
        val f = MethodFinder
        hookAfter(f.find("android.webkit.WebViewClient", "onPageFinished", WebView::class.java, String::class.java)) { inv ->
            (inv.getArg(0) as? WebView)?.let(::injectProbe)
        }
        hookCtorBefore(f.findCtor("android.webkit.WebView", Context::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }
        hookBefore(f.find("android.webkit.WebView", "loadUrl", String::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }
        hookBefore(f.find("android.webkit.WebView", "loadUrl", String::class.java, Map::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }
        hookBefore(f.find("android.webkit.WebView", "loadData", String::class.java, String::class.java, String::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }
        hookBefore(f.find("android.webkit.WebView", "loadDataWithBaseURL", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }
        hookBefore(f.find("android.webkit.WebView", "evaluateJavascript", String::class.java, ValueCallback::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }
        hookBefore(f.find("android.webkit.WebView", "evaluateJavascript", String::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }
    }

    /** 注册 JS 桥(弱引用幂等,失败静默降级——页面仍可正常使用)。 */
    private fun ensureBridge(view: WebView) {
        if (bridgedViews.contains(view)) return
        runCatching { view.addJavascriptInterface(bridge, WebviewProbe.BRIDGE_NAME) }
        bridgedViews.add(view)
    }

    /** 注入探针:桥注册幂等 + evaluateJavascript 失败静默(页面无 JS 环境时零影响)。 */
    private fun injectProbe(view: WebView) {
        if (!enabled()) return
        ensureBridge(view)
        runCatching { view.evaluateJavascript(WebviewProbe.PROBE_JS, null) }
    }

    private fun hookBefore(method: Method?, before: (Invocation) -> Unit) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (enabled()) before(invocation)
            }
        })
    }

    private fun hookCtorBefore(ctor: Constructor<*>?, before: (Invocation) -> Unit) {
        if (ctor == null) return
        engine.hook(ctor, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (enabled()) before(invocation)
            }
        })
    }

    private fun hookAfter(method: Method?, after: (Invocation) -> Unit) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (enabled()) after(invocation)
            }
        })
    }
}
