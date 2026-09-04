package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 网络请求监控 Hooker(V30;V36 D-8/D-9 重写):捕获 HTTP 请求/响应元数据。
 *
 * 设计原则:
 * - OkHttp newCall():D-9 修复 —— 安装期 okhttp3 类尚未加载(应用侧类加载器),
 *   直接 Class.forName 必失败静默零安装;改为 loadClass 监视器,目标类首次加载时
 *   惰性安装(幂等);
 * - HttpURLConnection:D-8 修复 —— 抽象类的 getInputStream/getOutputStream 无方法体,
 *   虚分派直达具体实现(com.android.okhttp.internal.huc.*),抽象钩子永不触发;
 *   改挂具体实现类(boot classpath 可解析);
 * - 仅 hook 公开 API,不触碰 socket 句柄(被动红线);
 * - 流量捕获由独立工具 PinProxy 完成,Modev 不负责请求/响应体的完整捕获。
 */
class NetworkRequestHooker(
    context: ProbeContext,
) : NetworkBaseHooker(context) {

    /** 已安装钩子的类名(幂等)。 */
    private val installed = HashSet<String>()

    override fun install() {
        hookHttpUrlConnectionImpl()
        armLazyOkHttpHooks()
    }

    // ------------------------------------------------------------------
    // OkHttp:loadClass 监视器 + 首次加载惰性安装(D-9)
    // ------------------------------------------------------------------

    private fun armLazyOkHttpHooks() {
        val loadClass = runCatching {
            ClassLoader::class.java.getMethod("loadClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(loadClass, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val name = invocation.getArg(0) as? String ?: return
                when (name) {
                    OKHTTP_CLIENT -> installOkHttpCallHook()
                }
            }
        })
    }

    @Synchronized
    private fun installOkHttpCallHook() {
        if ("newCall" in installed) return
        val clazz = runCatching { Class.forName(OKHTTP_CLIENT) }.getOrNull() ?: return
        val requestClass = runCatching { Class.forName(OKHTTP_REQUEST) }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("newCall", requestClass) }.getOrNull() ?: return
        installed += "newCall"
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().networkRequest) return
                val request = invocation.getArg(0) ?: return
                val url = runCatching {
                    requestClass.getMethod("url").invoke(request).toString()
                }.getOrNull()
                val httpMethod = runCatching {
                    requestClass.getMethod("method").invoke(request) as? String
                }.getOrNull()
                val headers = runCatching {
                    val headerMap = HashMap<String, String>()
                    val headersObj = requestClass.getMethod("headers").invoke(request)
                    val headersClass = headersObj?.javaClass ?: return@runCatching headerMap
                    val size = headersClass.getMethod("size").invoke(headersObj) as? Int ?: 0
                    for (i in 0 until size) {
                        val hName = headersClass.getMethod("name", Int::class.javaPrimitiveType).invoke(headersObj, i) as? String ?: continue
                        val hValue = headersClass.getMethod("value", Int::class.javaPrimitiveType).invoke(headersObj, i) as? String ?: continue
                        headerMap[hName] = hValue
                    }
                    headerMap
                }.getOrNull()
                emit(
                    kind = NetworkKind.REQUEST,
                    operation = NetworkOperation.REQUEST_START,
                    url = url,
                    method = httpMethod,
                    requestHeaders = headers,
                )
            }
        })
    }

    // ------------------------------------------------------------------
    // HttpURLConnection:具体实现类(D-8)
    // ------------------------------------------------------------------

    private fun hookHttpUrlConnectionImpl() {
        for (cn in HUC_IMPLS) {
            val clazz = runCatching {
                try {
                    Class.forName(cn, false, null)
                } catch (e: ClassNotFoundException) {
                    Class.forName(cn)
                }
            }.getOrNull() ?: continue
            hookHucMethods(clazz)
        }
    }

    @Synchronized
    private fun hookHucMethods(clazz: Class<*>) {
        if (clazz.name in installed) return
        installed += clazz.name
        val getOutputStream = runCatching { clazz.getMethod("getOutputStream") }.getOrNull()
        if (getOutputStream != null) {
            engine.hook(getOutputStream, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().networkRequest) return
                    val conn = invocation.thisObject ?: return
                    val url = runCatching { conn.javaClass.getMethod("getURL").invoke(conn).toString() }.getOrNull()
                    val method = runCatching { conn.javaClass.getMethod("getRequestMethod").invoke(conn) as? String }.getOrNull()
                    emit(
                        kind = NetworkKind.REQUEST,
                        operation = NetworkOperation.REQUEST_START,
                        url = url,
                        method = method,
                    )
                }
            })
        }
        val getInputStream = runCatching { clazz.getMethod("getInputStream") }.getOrNull()
        if (getInputStream != null) {
            engine.hook(getInputStream, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!config().networkRequest) return
                    val conn = invocation.thisObject ?: return
                    val url = runCatching { conn.javaClass.getMethod("getURL").invoke(conn).toString() }.getOrNull()
                    val statusCode = runCatching {
                        conn.javaClass.getMethod("getResponseCode").invoke(conn) as? Int
                    }.getOrNull()
                    emit(
                        kind = NetworkKind.REQUEST,
                        operation = NetworkOperation.REQUEST_END,
                        url = url,
                        statusCode = statusCode,
                    )
                }
            })
        }
    }

    companion object {
        private const val OKHTTP_CLIENT = "okhttp3.OkHttpClient"
        private const val OKHTTP_REQUEST = "okhttp3.Request"

        /** Android 平台 HttpURLConnection 具体实现(boot classpath)。 */
        private val HUC_IMPLS = listOf(
            "com.android.okhttp.internal.huc.HttpURLConnectionImpl",
            "com.android.okhttp.internal.huc.HttpsURLConnectionImpl",
        )
    }
}
