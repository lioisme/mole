package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 网络请求监控 Hooker(V30):捕获 HTTP 请求/响应,附带 Reqable 堆栈跟踪。
 *
 * 设计原则:
 * - 拦截 OkHttp newCall() 和 HttpURLConnection getOutputStream()/getInputStream();
 * - 捕获请求发起时的 Java 调用栈(写入 NetworkRecord.stack),供 Reqable 追溯;
 * - 仅 hook 公开 API,不触碰 socket 句柄(被动红线)。
 */
class NetworkRequestHooker(
    context: ProbeContext,
) : NetworkBaseHooker(context) {

    override fun install() {
        hookOkHttpClient()
        hookHttpURLConnection()
    }

    /**
     * OkHttp newCall(Request) 拦截:捕获请求 URL/方法/请求头。
     * 覆盖:okhttp3.OkHttpClient.newCall(Request)
     */
    private fun hookOkHttpClient() {
        val clazz = runCatching { Class.forName("okhttp3.OkHttpClient") }.getOrNull() ?: return
        val requestClass = runCatching { Class.forName("okhttp3.Request") }.getOrNull() ?: return
        val method = runCatching { clazz.getMethod("newCall", requestClass) }.getOrNull() ?: return

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
                        val name = headersClass.getMethod("name", Int::class.javaPrimitiveType).invoke(headersObj, i) as? String ?: continue
                        val value = headersClass.getMethod("value", Int::class.javaPrimitiveType).invoke(headersObj, i) as? String ?: continue
                        headerMap[name] = value
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

    /**
     * HttpURLConnection 请求监控:
     * - getOutputStream(): 捕获请求写出(POST/PUT)
     * - getInputStream(): 捕获响应读入
     */
    private fun hookHttpURLConnection() {
        val clazz = runCatching { Class.forName("java.net.HttpURLConnection") }.getOrNull() ?: return

        // getOutputStream()
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

        // getInputStream()
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
}
