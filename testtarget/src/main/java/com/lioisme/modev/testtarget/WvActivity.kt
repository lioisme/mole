package com.lioisme.modev.testtarget

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lioisme.modev.testtarget.TriggerActivity.Companion.EXTRA_CASE

/**
 * WebView 用例宿主(L-WV-01..07 / E-SSL-05):单实例按用例驱动加载/JS/桥/调试/JS 密码学/SSL 错误路径。
 */
class WvActivity : Activity() {

    private lateinit var web: WebView
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        val case = intent.getStringExtra(EXTRA_CASE) ?: "L-WV-01"

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // 必须调 super:WebViewHooker 钩的是基类 onPageFinished,
                // 不调 super 则基类方法不执行 → JS 探针注入永不触发(D-14)
                super.onPageFinished(view, url)
                if (case == "L-WV-01" || case == "L-WV-06") {
                    Tt.result(case, "finished" to "true", "url" to (url ?: "null"))
                    finishLater()
                } else if (case == "L-WV-07") {
                    main.postDelayed({
                        Tt.result(case, "fired" to "true")
                        finishLater()
                    }, 3600)
                } else if (case == "E-SSL-05") {
                    Tt.result(case, "loaded" to "true")
                    finishLater()
                }
            }
        }

        when (case) {
            "L-WV-01", "L-WV-06" -> web.loadUrl("file:///android_asset/wv_basic.html")
            "L-WV-02" -> {
                web.loadData("<html><body>tt-loadData</body></html>", "text/html", "utf-8")
                Tt.result(case, "loadData" to "issued")
                finishLater()
            }
            "L-WV-03" -> {
                web.loadUrl("file:///android_asset/wv_basic.html")
                main.postDelayed({
                    web.evaluateJavascript("1+1") { v ->
                        Tt.result(case, "evalResult" to v)
                        finishLater()
                    }
                }, 600)
            }
            "L-WV-04" -> {
                web.addJavascriptInterface(TtBridge(), "ttBridge")
                web.loadUrl("file:///android_asset/wv_bridge.html")
                Tt.result(case, "bridge" to "registered")
                finishLater()
            }
            "L-WV-05" -> {
                WebView.setWebContentsDebuggingEnabled(true)
                Tt.result(case, "debug" to "enabled")
                finishLater()
            }
            "L-WV-07" -> web.loadUrl("file:///android_asset/wv_crypto.html")
            "E-SSL-05" -> {
                val port = NetTargets.tlsIpPort(applicationContext)
                web.webViewClient = WebViewClient()
                web.loadUrl("https://localhost:$port/ssl")
                main.postDelayed({
                    Tt.result(case, "timeout" to "true")
                    finishLater()
                }, 6000)
            }
            else -> {
                Tt.result(case, "error" to "unknown-case")
                finishLater()
            }
        }
    }

    @Volatile private var jsDone = false

    inner class TtBridge {
        @android.webkit.JavascriptInterface
        fun report(payload: String): String {
            jsDone = true
            Tt.result("L-WV-07", "bridgeReport" to payload.take(64))
            return "ok"
        }
    }

    private fun finishLater() {
        main.postDelayed({ finish() }, 300)
    }
}
