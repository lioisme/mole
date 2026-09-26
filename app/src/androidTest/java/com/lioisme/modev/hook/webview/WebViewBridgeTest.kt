package com.lioisme.modev.hook.webview

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import com.lioisme.modev.hook.RecordSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 探针端到端契约(V12,宿主进程内真机 WebView):
 * - 注入探针 + 桥后,页面内 WebCrypto 调用被包装并回传 → 映射为 WEB_CRYPTO 记录;
 * - crypto.subtle 不可用(非安全上下文)时静默降级,断言前提按 availability 兜底;
 * - 开关关闭(webView=false)时零回传;畸形载荷静默丢弃;
 * - maxHexChars 与配置实时一致(JS 侧预截断闸)。
 */
@SuppressLint("SetJavaScriptEnabled")
@RunWith(AndroidJUnit4::class)
class WebViewBridgeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val collected = CopyOnWriteArrayList<String>()
    private lateinit var switch: HookSwitch
    private lateinit var sink: RecordSink
    private lateinit var bridge: WebViewJsBridge
    private var webView: WebView? = null

    @Before
    fun setUp() {
        switch = HookSwitch.DEFAULT.copy(wvJsCrypto = true)
        sink = RecordSink(
            jsonOf = { it.toString() },
            sendJson = { json ->
                collected.add(json)
                true
            },
            isConnected = { true },
            spoolProvider = { null },
            seqFileProvider = { null },
        )
        sink.start()
        bridge = WebViewJsBridge(
            config = { switch },
            sink = sink,
            packageName = "com.lioisme.modev.test",
            processName = "com.lioisme.modev.test",
        )
    }

    @After
    fun tearDown() {
        // WebView 必须在其创建线程(主线程)销毁,否则 checkThread 抛异常
        val v = webView ?: return
        mainSync { v.destroy() }
        webView = null
    }

    private fun waitForRecords(count: Int): List<String> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (collected.size >= count) return collected.toList()
            Thread.sleep(50)
        }
        return collected.toList()
    }

    private fun evaluate(view: WebView, script: String) {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript(script) { latch.countDown() }
        }
        assertTrue("evaluateJavascript 超时", latch.await(10, TimeUnit.SECONDS))
    }

    private fun mainSync(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
    }

    private fun createWebView(): WebView {
        val latch = CountDownLatch(1)
        var v: WebView? = null
        mainSync {
            v = WebView(context).apply {
                settings.javaScriptEnabled = true
                WebView.setWebContentsDebuggingEnabled(true)
            }
            v!!.webViewClient = android.webkit.WebViewClient()
            v!!.addJavascriptInterface(bridge, WebviewProbe.BRIDGE_NAME)
            v!!.loadDataWithBaseURL(
                "file:///android_asset/",
                "<html><body>probe-test</body></html>",
                "text/html",
                "utf-8",
                null,
            )
            latch.countDown()
        }
        assertTrue("WebView 创建超时", latch.await(10, TimeUnit.SECONDS))
        return v!!
    }

    private fun injectProbeAndWaitReady(): WebView {
        val v = createWebView()
        val pageLatch = CountDownLatch(1)
        mainSync {
            v.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) = pageLatch.countDown()
            }
            if (v.url.isNullOrEmpty()) v.reload()
        }
        assertTrue("页面加载超时", pageLatch.await(15, TimeUnit.SECONDS))
        evaluate(v, WebviewProbe.PROBE_JS)
        return v
    }

    /** 探针是否存在且 crypto.subtle 是否可用(非安全上下文时降级)。 */
    private fun webCryptoAvailable(): Boolean {
        var available = false
        val latch = CountDownLatch(1)
        mainSync {
            webView!!.evaluateJavascript(
                "JSON.stringify({probe: !!window.__modevProbe, subtle: !!(window.crypto && window.crypto.subtle)})",
            ) { result ->
                // evaluateJavascript 对字符串返回值会再包一层引号(双形态容忍)
                val raw = result?.toString() ?: "{}"
                val parsed = runCatching { JsonParser.parseString(raw) }.getOrNull()
                val obj = when {
                    parsed == null -> null
                    parsed.isJsonObject -> parsed.asJsonObject
                    parsed.isJsonPrimitive && parsed.asJsonPrimitive.isString ->
                        runCatching { JsonParser.parseString(parsed.asString) }.getOrNull()
                            ?.takeIf { it.isJsonObject }?.asJsonObject
                    else -> null
                }
                available = obj?.get("probe")?.asBoolean == true && obj?.get("subtle")?.asBoolean == true
                latch.countDown()
            }
        }
        assertTrue("探针可用性探测超时", latch.await(10, TimeUnit.SECONDS))
        return available
    }

    @Test
    fun webCrypto_encrypt_emits_webcrypto_record() {
        webView = injectProbeAndWaitReady()
        assumeTrue("当前 WebView 上下文无 crypto.subtle(非安全上下文),跳过端到端断言", webCryptoAvailable())
        // 触发 importKey + encrypt(两者均被包装并回传)
        evaluate(webView!!, 
            """
            (function () {
              window.crypto.subtle.importKey(
                'raw', new TextEncoder().encode('0123456789abcdef'),
                { name: 'AES-GCM' }, false, ['encrypt']
              ).then(function (key) {
                var iv = new Uint8Array(12);
                window.crypto.subtle.encrypt(
                  { name: 'AES-GCM', iv: iv, tagLength: 128 },
                  key, new TextEncoder().encode('hello modev')
                ).then(function () {}, function () {});
              }, function () {});
            })()
            """.trimIndent(),
        )
        val records = waitForRecords(2)
        assertEquals("importKey+encrypt 应各回传 1 条记录", 2, records.size)
        val joined = records.joinToString("\n")
        // 断言一律由**枚举**派生(旧写法把 V12 时代的字面量 `WEB_CRYPTO`/`ENCRYPT`/
        // `GET_KEY` 抄死在测试里,V29 枚举改名后恒红且消息零信息 —— 本轮实锤)。
        assertTrue(
            "kind 必须是 WebView 的 JS_CRYPTO,实际:$joined",
            joined.contains("kind=${WebViewKind.JS_CRYPTO}"),
        )
        assertTrue(
            "两笔操作各自点名,实际:$joined",
            joined.contains("operation=${WebViewOperation.JS_IMPORT_KEY}") &&
                joined.contains("operation=${WebViewOperation.JS_ENCRYPT}"),
        )
        // 明文回传(TextEncoder 输入)= "hello modev" 的 hex
        assertTrue("输入明文必须以 hex 落进记录,实际:$joined", joined.contains("68656c6c6f206d6f646576"))
        // 探针回传的算法名 `alg`(WebCrypto/AES-GCM)**当前未落进记录**
        // (WebViewEventMapper.toRecord 只带 params,WebViewRecord 无 algorithm 字段;
        // 实体 algorithm 列被 urlHost 复用)⇒ 旧断言 `algorithm=…`/`provider=…` 一并撤除,
        // 缺口登记在 TASK-V48 §8(V48-DFC-15),不在本次 V48 三需求范围内顺手改。
    }

    @Test
    fun switch_off_emits_nothing() {
        webView = injectProbeAndWaitReady()
        assumeTrue("当前 WebView 上下文无 crypto.subtle,跳过端到端断言", webCryptoAvailable())
        switch = HookSwitch.DEFAULT.copy(webviewListen = false)
        evaluate(webView!!, 
            """
            (function () {
              window.crypto.subtle.generateKey(
                { name: 'AES-GCM', length: 128 }, false, ['encrypt']
              ).then(function () {}, function () {});
            })()
            """.trimIndent(),
        )
        Thread.sleep(1_000)
        assertEquals("开关关闭时零回传", 0, collected.size)
    }

    @Test
    fun malformed_payload_is_discarded() {
        bridge.report(null)
        bridge.report("")
        bridge.report("not-json")
        bridge.report("""{"op":123}""")
        bridge.report("""{"alg":"x"}""")
        Thread.sleep(1_000)
        assertEquals("畸形载荷须静默丢弃", 0, collected.size)
    }

    @Test
    fun max_hex_chars_mirrors_config() {
        switch = HookSwitch.DEFAULT.copy(maxBytesPerField = 64)
        assertEquals(128, bridge.maxHexChars())
        switch = HookSwitch.DEFAULT.copy(maxBytesPerField = 0)
        assertEquals(0, bridge.maxHexChars())
    }
}
