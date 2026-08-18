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
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import com.lioisme.modev.hook.webview.WebViewJsBridge
import com.lioisme.modev.hook.webview.WebviewProbe
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * WebView JS 鍔犺В瀵嗙洃鍚?V12):娉ㄥ叆鎺㈤拡鍖呰 WebCrypto / CryptoJS / JSEncrypt,
 * 缁?JS 妗ュ洖浼犺褰?kind=WEB_CRYPTO)銆傛敞鍏ラ潰(D5):
 * 1. 鍩虹被 [android.webkit.WebViewClient.onPageFinished] 鈥斺€?椤甸潰鍔犺浇瀹屾垚鍚庢敞鍏ユ帰閽? *    (瑕嗙洊缁濆ぇ澶氭暟"澶嶅啓骞惰皟 super"鐨勮嚜瀹氫箟瀹㈡埛绔?;
 * 2. WebView.<init> / loadUrl / loadData / loadDataWithBaseURL / evaluateJavascript
 *    鈥斺€?灏芥棭娉ㄥ唽 JS 妗?SPA 鍔ㄦ€佽剼鏈笌涓嶅鍐?onPageFinished 鐨勫鎴风鍏滃簳)銆? * 鍏ㄩ儴 hook 缁?[MethodFinder] 绌哄畨鍏?+ runCatching 鍖呰９,闆跺紓甯稿娉勫涓?
 * 鎺㈤拡骞傜瓑瀹堝崼鍦?JS 渚?[WebviewProbe.IDEMPOTENCE_GUARD]),閲嶅娉ㄥ叆闆跺壇浣滅敤銆? */
class WebViewHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.WEB_CRYPTO) {

    private val bridge by lazy { WebViewJsBridge(config, sink, packageName, processName) }

    /** 宸叉敞鍐屾ˉ鐨?WebView 寮卞紩鐢ㄩ泦鍚?骞傜瓑,涓嶉樆姝?GC)銆?*/
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

    /** 娉ㄥ唽 JS 妗?寮卞紩鐢ㄥ箓绛?澶辫触闈欓粯闄嶇骇鈥斺€旈〉闈粛鍙甯镐娇鐢?銆?*/
    private fun ensureBridge(view: WebView) {
        if (bridgedViews.contains(view)) return
        runCatching { view.addJavascriptInterface(bridge, WebviewProbe.BRIDGE_NAME) }
        bridgedViews.add(view)
    }

    /** 娉ㄥ叆鎺㈤拡:妗ユ敞鍐屽箓绛?+ evaluateJavascript 澶辫触闈欓粯(椤甸潰鏃?JS 鐜鏃堕浂褰卞搷)銆?*/
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
