package com.lioisme.modev.hook.hooks

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import com.lioisme.modev.core.model.WebViewRecord
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.webview.WebViewJsBridge
import com.lioisme.modev.hook.webview.WebviewProbe
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * WebView 全生命周期监听(V29 一等公民):捕获页面加载/JS 执行/桥注册/调试开关,
 * 经 [WebViewJsBridge] 回传 JS Crypto 事件(V12 迁移)。
 *
 * 不继承 [BaseHooker](后者绑定 [CryptoKind])——本类完全使用 [WebViewKind] 体系,
 * 自行管理 enabled() 判断与 sink.emit(WebViewRecord) 路径。
 *
 * 注入面:
 * 1. WebViewClient.onPageFinished —— 页面加载完成(PAGE_LOAD/PAGE_FINISHED);
 * 2. WebView.<init> —— 构造器(JS_BRIDGE 桥注册);
 * 3. WebView.loadUrl/loadData/loadDataWithBaseURL —— 页面加载(PAGE_LOAD);
 * 4. WebView.evaluateJavascript —— JS 执行(EVALUATE_JS);
 * 5. WebView.setWebContentsDebuggingEnabled —— 调试开关(DEBUG);
 *
 * 全部 hook 经 [MethodFinder] 空安全 + runCatching 包裹,零异常外泄漏。
 * 探针幂等守卫在 JS 侧([WebviewProbe.IDEMPOTENCE_GUARD]),重复注入零副作用。
 */
class WebViewHooker(
    context: ProbeContext,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val sink = context.sink
    protected val packageName = context.packageName

    /**
     * 目标进程名(V44 T2.6):经 [com.lioisme.modev.hook.ProcessIdentity] 单例读取——
     * 旧形态每个 Hooker 基类各自 `by lazy` 读一次 `/proc/self/cmdline`,冷启动期
     * 约 50 个实例 = 50 次文件读 + 字节解码,而进程身份在进程内恒定不变。
     */
    protected val processName: String get() = com.lioisme.modev.hook.ProcessIdentity.processName

    private val bridge by lazy {
        WebViewJsBridge(
            config = config,
            sink = sink,
            packageName = packageName,
            processName = processName,
        )
    }

    /** 已注册桥的 WebView 弱引用集合(幂等,不阻止 GC)。 */
    private val bridgedViews = Collections.newSetFromMap(
        Collections.synchronizedMap(WeakHashMap<WebView, Boolean>()),
    )

    fun install() {
        val f = MethodFinder

        // PAGE_FINISHED: onPageFinished 回调
        hookAfter(f.find("android.webkit.WebViewClient", "onPageFinished", WebView::class.java, String::class.java)) { inv ->
            val view = inv.getArg(0) as? WebView ?: return@hookAfter
            val url = inv.getArg(1) as? String
            emitPageFinished(view, url)
            injectProbe(view)
        }

        // WebView 构造器:确保 JS 桥注册(D-16:改 after —— 构造器 before 阶段
        // provider 未就绪,addJavascriptInterface 静默失败致页面 JS 永远看不到桥)
        hookCtorAfter(f.findCtor("android.webkit.WebView", Context::class.java)) { inv ->
            (inv.thisObject as? WebView)?.let(::ensureBridge)
        }

        // loadUrl(String):确保桥 + 记录
        hookBefore(f.find("android.webkit.WebView", "loadUrl", String::class.java)) { inv ->
            val view = inv.thisObject as? WebView ?: return@hookBefore
            ensureBridge(view)
            emitLoadUrl(view, inv.getArg(0) as? String)
        }

        // loadUrl(String, Map):确保桥 + 记录
        hookBefore(f.find("android.webkit.WebView", "loadUrl", String::class.java, Map::class.java)) { inv ->
            val view = inv.thisObject as? WebView ?: return@hookBefore
            ensureBridge(view)
            emitLoadUrl(view, inv.getArg(0) as? String, WebViewOperation.LOAD_URL_WITH_HEADERS)
        }

        // loadData:确保桥 + 记录
        hookBefore(f.find("android.webkit.WebView", "loadData", String::class.java, String::class.java, String::class.java)) { inv ->
            val view = inv.thisObject as? WebView ?: return@hookBefore
            ensureBridge(view)
            emitGeneric(view, WebViewOperation.LOAD_DATA)
        }

        // loadDataWithBaseURL:确保桥 + 记录
        hookBefore(f.find("android.webkit.WebView", "loadDataWithBaseURL", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)) { inv ->
            val view = inv.thisObject as? WebView ?: return@hookBefore
            ensureBridge(view)
            emitGeneric(view, WebViewOperation.LOAD_DATA_WITH_BASE_URL)
        }

        // evaluateJavascript(String, ValueCallback):确保桥 + 记录
        hookBefore(f.find("android.webkit.WebView", "evaluateJavascript", String::class.java, ValueCallback::class.java)) { inv ->
            val view = inv.thisObject as? WebView ?: return@hookBefore
            ensureBridge(view)
            emitEvaluateJs(view, inv.getArg(0) as? String)
        }

        // evaluateJavascript(String) 旧 API:确保桥 + 记录
        hookBefore(f.find("android.webkit.WebView", "evaluateJavascript", String::class.java)) { inv ->
            val view = inv.thisObject as? WebView ?: return@hookBefore
            ensureBridge(view)
            emitEvaluateJs(view, inv.getArg(0) as? String)
        }

        // setWebContentsDebuggingEnabled:调试开关记录
        hookBefore(f.find("android.webkit.WebView", "setWebContentsDebuggingEnabled", Boolean::class.javaPrimitiveType!!)) { inv ->
            emitDebug(inv)
        }
    }

    /**
     * 注册 JS 桥:弱引用幂等,失败静默降级——页面仍可正常使用。
     * V43 T2:JS_BRIDGE kind 门控前置——开关关闭 = 宿主 WebView 命名空间零污染
     * (旧实现每个页面永远携带 `window.modevJsBridge`,既可被反检测方枚举,
     * 又构成对宿主的功能副作用)。
     */
    private fun ensureBridge(view: WebView) {
        // 桥是 JS_CRYPTO 探针与 JS_BRIDGE 记录的共同载体:任一开启才注入,全关零污染
        if (!enabled(WebViewKind.JS_BRIDGE) && !enabled(WebViewKind.JS_CRYPTO)) return
        if (bridgedViews.contains(view)) return
        runCatching { view.addJavascriptInterface(bridge, WebviewProbe.BRIDGE_NAME) }
        bridgedViews.add(view)
        emitJsBridge(view)
    }

    /** 注入探针:桥注册幂等 + evaluateJavascript 失败静默。 */
    private fun injectProbe(view: WebView) {
        if (!enabled(WebViewKind.JS_CRYPTO)) return
        ensureBridge(view)
        runCatching { view.evaluateJavascript(WebviewProbe.PROBE_JS, null) }
    }

    private fun emitPageFinished(view: WebView, url: String?) {
        if (!enabled(WebViewKind.PAGE_LOAD)) return
        emit(view, WebViewKind.PAGE_LOAD, WebViewOperation.PAGE_FINISHED, url = url)
    }

    private fun emitLoadUrl(view: WebView, url: String?, op: WebViewOperation = WebViewOperation.LOAD_URL) {
        if (!enabled(WebViewKind.PAGE_LOAD)) return
        emit(view, WebViewKind.PAGE_LOAD, op, url = url)
    }

    private fun emitEvaluateJs(view: WebView, script: String?) {
        if (!enabled(WebViewKind.EVALUATE_JS)) return
        emit(view, WebViewKind.EVALUATE_JS, WebViewOperation.EVALUATE_JS, url = null, scriptSnippet = script)
    }

    private fun emitJsBridge(view: WebView) {
        if (!enabled(WebViewKind.JS_BRIDGE)) return
        emit(view, WebViewKind.JS_BRIDGE, WebViewOperation.ADD_JS_INTERFACE)
    }

    private fun emitDebug(inv: Invocation) {
        if (!enabled(WebViewKind.DEBUG)) return
        val debugEnabled = inv.getArg(0) as? Boolean ?: return
        val record = WebViewRecord(
            packageName = packageName,
            processName = processName,
            kind = WebViewKind.DEBUG,
            operation = WebViewOperation.SET_DEBUGGING,
            debugEnabled = debugEnabled,
            threadName = Thread.currentThread().name,
            stack = if (config().logStack) com.lioisme.modev.core.util.StackTraceFilter.capture(config().maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }

    private fun emitGeneric(view: WebView, op: WebViewOperation) {
        // V38 修复:loadData/loadDataWithBaseURL 路径曾绕过 kind 闸门,
        // webviewListen=false 或 PAGE_LOAD 关时仍产出记录(NEG 零断言实锤);
        // 与其余 emit* 路径同构补闸。
        if (!enabled(WebViewKind.PAGE_LOAD)) return
        emit(view, WebViewKind.PAGE_LOAD, op)
    }

    private fun emit(
        view: WebView,
        kind: WebViewKind,
        op: WebViewOperation,
        url: String? = null,
        scriptSnippet: String? = null,
    ) {
        val record = WebViewRecord(
            packageName = packageName,
            processName = processName,
            kind = kind,
            operation = op,
            url = url,
            urlHost = WebViewRecord.extractHost(url),
            script = scriptSnippet?.toByteArray(Charsets.UTF_8)?.let { bytes ->
                val max = config().maxBytesPerField
                if (max <= 0) com.lioisme.modev.core.util.BytesView.of(bytes)
                else com.lioisme.modev.core.util.BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
            },
            threadName = Thread.currentThread().name,
            stack = if (config().logStack) com.lioisme.modev.core.util.StackTraceFilter.capture(config().maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }

    private fun hookBefore(method: Method?, before: (Invocation) -> Unit) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                before(invocation)
            }
        })
    }

    private fun hookCtorAfter(ctor: Constructor<*>?, after: (Invocation) -> Unit) {
        if (ctor == null) return
        engine.hook(ctor, object : MethodHook() {
            override fun after(invocation: Invocation) {
                after(invocation)
            }
        })
    }

    private fun hookAfter(method: Method?, after: (Invocation) -> Unit) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                after(invocation)
            }
        })
    }

    private fun enabled(kind: WebViewKind = WebViewKind.JS_CRYPTO): Boolean = config().webviewEnabled(kind)
}
