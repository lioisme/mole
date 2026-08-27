package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.lang.reflect.Method
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SSL Pinning 绕过 Hooker(V30):覆盖 Android 网络安全架构的每一层验证决策点。
 *
 * 设计原则(第一性原则):
 * - 不是 hook 所有 SSL 方法(过度且脆弱),而是精确拦截验证决策点;
 * - TrustManager 替换为 trust-all(空操作);
 * - OkHttp CertificatePinner.check 拦截(含混淆版堆栈指纹发现);
 * - WebView onReceivedSslError 自动 proceed;
 * - 多版本兼容通过方法签名匹配处理。
 *
 * 干预目标(由 [NetworkKind] 门控):
 * - SSL_CONTEXT: SSLContext.init TrustManager 替换
 * - SSL_TRUST_MANAGER: Conscrypt TrustManagerImpl 拦截
 * - SSL_OKHTTP_PINNER: CertificatePinner.check 拦截
 * - SSL_OKHTTP_VERIFIER: OkHostnameVerifier.verify 拦截
 * - SSL_WEBVIEW: WebView onReceivedSslError proceed
 * - SSL_NETWORK_CONFIG: NetworkSecurityConfig pin 绕过
 */
class SSLBypassHooker(
    context: ProbeContext,
) : NetworkBaseHooker(context) {

    /** 已发现的混淆 OkHttp 类(堆栈指纹法缓存)。 */
    private val discoveredClasses = HashMap<String, Any>()

    /** SSLContext 内部字段反射缓存(首次成功后复用;失败则置 null 跳过后续)。 */
    @Volatile
    private var contextFieldRef: java.lang.reflect.Field? = null

    @Volatile
    private var trustManagerFieldRef: java.lang.reflect.Field? = null

    /** 反射失败降级标志:首次反射失败后置 true,后续调用直接跳过。 */
    @Volatile
    private var sslReflectionDisabled = false

    override fun install() {
        hookSSLContext()
        hookTrustManager()
        hookOkHttpPinner()
        hookOkHttpVerifier()
        hookWebViewSsl()
        hookNetworkConfig()
        hookObfuscatedOkHttp()
    }

    /**
     * SSLContext.init 替换:拦截 TrustManager 数组注入 trust-all 实现。
     * 覆盖:javax.net.ssl.SSLContext.init(KeyManager[], TrustManager[], SecureRandom)
     */
    private fun hookSSLContext() {
        val clazz = runCatching { SSLContext::class.java }.getOrNull() ?: return
        val methods = runCatching {
            clazz.getMethod("init", Array<KeyManager>::class.java, Array<TrustManager>::class.java, java.security.SecureRandom::class.java)
        }.getOrNull() ?: return

        engine.hook(methods, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().sslContext) return
                if (sslReflectionDisabled) return
                // 替换 context 内的 TrustManager 为 trust-all
                try {
                    val ctx = invocation.thisObject as? SSLContext ?: return
                    // 首次反射后缓存 Field 对象,后续直接复用
                    val cf = contextFieldRef ?: runCatching {
                        ctx.javaClass.getDeclaredField("context").also { it.isAccessible = true }
                    }.getOrNull()
                    if (cf == null) {
                        sslReflectionDisabled = true
                        return
                    }
                    contextFieldRef = cf
                    val contextImpl = cf.get(ctx) ?: return
                    val tmf = trustManagerFieldRef ?: runCatching {
                        contextImpl.javaClass.getDeclaredField("trustManager").also { it.isAccessible = true }
                    }.getOrNull()
                    if (tmf == null) {
                        sslReflectionDisabled = true
                        return
                    }
                    trustManagerFieldRef = tmf
                    tmf.set(contextImpl, TrustAllTrustManager)
                } catch (_: Throwable) {
                    sslReflectionDisabled = true
                }
                emit(
                    kind = NetworkKind.SSL_CONTEXT,
                    operation = NetworkOperation.SSL_INIT,
                    params = mapOf("api" to "SSLContext.init"),
                )
            }
        })
    }

    /**
     * Conscrypt TrustManagerImpl 拦截:Android 默认 TLS 提供者的验证决策点。
     * 覆盖 checkServerTrusted 全家族(3 参数/4 参数/5 参数)。
     */
    private fun hookTrustManager() {
        val classNames = listOf(
            "com.android.org.conscrypt.TrustManagerImpl",
            "com.android.org.conscrypt.Platform",
        )
        for (className in classNames) {
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
            // checkServerTrusted(X509Certificate[], String)
            hookMethodSafe(clazz, "checkServerTrusted", Array<X509Certificate>::class.java, String::class.java)
            // checkServerTrusted(X509Certificate[], String, String)
            hookMethodSafe(clazz, "checkServerTrusted", Array<X509Certificate>::class.java, String::class.java, String::class.java)
            // checkServerTrusted(X509Certificate[], String, SSLSession)
            hookMethodSafe(clazz, "checkServerTrusted", Array<X509Certificate>::class.java, String::class.java, SSLSession::class.java)
            // checkTrusted(X509Certificate[], String, SSLSession, SSLParameters, boolean)
            hookMethodSafe(clazz, "checkTrusted", Array<X509Certificate>::class.java, String::class.java, SSLSession::class.java, javax.net.ssl.SSLParameters::class.java, Boolean::class.javaPrimitiveType!!)
            // checkTrusted(X509Certificate[], byte[], byte[], String, String, boolean)
            hookMethodSafe(clazz, "checkTrusted", Array<X509Certificate>::class.java, ByteArray::class.java, ByteArray::class.java, String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!)
        }
    }

    private fun hookMethodSafe(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>) {
        val method = runCatching { clazz.getMethod(methodName, *paramTypes) }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().sslTrustManager) return
                // 返回空集合/void:绕过验证
                val exec = invocation.executable
                val returnType = if (exec is java.lang.reflect.Method) exec.returnType else Void.TYPE
                if (returnType == Void.TYPE) {
                    invocation.setResult(null)
                } else if (List::class.java.isAssignableFrom(returnType)) {
                    invocation.setResult(emptyList<X509Certificate>())
                } else if (returnType == Int::class.javaPrimitiveType || returnType == Int::class.java) {
                    invocation.setResult(0)
                }
                emit(
                    kind = NetworkKind.SSL_TRUST_MANAGER,
                    operation = NetworkOperation.SSL_CHECK,
                    params = mapOf("target" to "${clazz.simpleName}.$methodName"),
                )
            }
        })
    }

    /**
     * OkHttp CertificatePinner.check 拦截:跳过 pin 验证。
     * 覆盖 OkHttp 3.x(check(String, List)) 和 4.2+(check$okhttp(String, Function0))。
     */
    private fun hookOkHttpPinner() {
        val classNames = listOf(
            "okhttp3.CertificatePinner",
            "okhttp3.internal.tls.CertificateChainCleaner",
        )
        for (className in classNames) {
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
            // check(String, List) - OkHttp 3.x
            hookMethodSafe(clazz, "check", String::class.java, List::class.java)
            // check$okhttp(String, Function0) - OkHttp 4.2+
            hookMethodSafe(clazz, "check\$okhttp", String::class.java, kotlin.jvm.functions.Function0::class.java)
            // check(String, List) - any remaining
            hookMethodSafe(clazz, "check", String::class.java, java.util.List::class.java)
        }
    }

    /**
     * OkHttp OkHostnameVerifier.verify 拦截:始终返回 true。
     * 覆盖 verify(String, SSLSession) 和 verify(String, X509Certificate)。
     */
    private fun hookOkHttpVerifier() {
        val classNames = listOf(
            "okhttp3.internal.tls.OkHostnameVerifier",
            "okhttp3.internal.tls.ApplicationAndroidPlatform",
        )
        for (className in classNames) {
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
            // verify(String, SSLSession)
            val verifySession = runCatching {
                clazz.getMethod("verify", String::class.java, SSLSession::class.java)
            }.getOrNull()
            if (verifySession != null) {
                engine.hook(verifySession, object : MethodHook() {
                    override fun before(invocation: Invocation) {
                        if (!config().sslOkhttpVerifier) return
                        invocation.setResult(true)
                        emit(
                            kind = NetworkKind.SSL_OKHTTP_VERIFIER,
                            operation = NetworkOperation.SSL_VERIFY,
                            params = mapOf("hostname" to (invocation.getArg(0) as? String ?: "")),
                        )
                    }
                })
            }
            // verify(String, X509Certificate)
            val verifyCert = runCatching {
                clazz.getMethod("verify", String::class.java, X509Certificate::class.java)
            }.getOrNull()
            if (verifyCert != null) {
                engine.hook(verifyCert, object : MethodHook() {
                    override fun before(invocation: Invocation) {
                        if (!config().sslOkhttpVerifier) return
                        invocation.setResult(true)
                        emit(
                            kind = NetworkKind.SSL_OKHTTP_VERIFIER,
                            operation = NetworkOperation.SSL_VERIFY,
                            params = mapOf("hostname" to (invocation.getArg(0) as? String ?: "")),
                        )
                    }
                })
            }
        }
    }

    /**
     * WebView SSL 错误处理:自动 proceed 接受证书。
     * 覆盖:android.webkit.WebViewClient.onReceivedSslError(WebView, SslErrorHandler, SslError)
     */
    private fun hookWebViewSsl() {
        val clazz = runCatching { Class.forName("android.webkit.WebViewClient") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod(
                "onReceivedSslError",
                Class.forName("android.webkit.WebView"),
                Class.forName("android.webkit.SslErrorHandler"),
                Class.forName("android.net.http.SslError"),
            )
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().sslWebview) return
                // handler.proceed()
                try {
                    val handler = invocation.getArg(1) ?: return
                    val proceedMethod = handler.javaClass.getMethod("proceed")
                    proceedMethod.invoke(handler)
                } catch (_: Throwable) { /* 降级 */ }
                emit(
                    kind = NetworkKind.SSL_WEBVIEW,
                    operation = NetworkOperation.SSL_WEBVIEW_ERROR,
                )
            }
        })
    }

    /**
     * NetworkSecurityConfig pin 绕过:拦截 checkPins 方法。
     * 覆盖:android.security.net.config.NetworkSecurityTrustManager.checkPins(List)
     */
    private fun hookNetworkConfig() {
        val classNames = listOf(
            "android.security.net.config.NetworkSecurityTrustManager",
            "android.security.net.config.NetworkSecurityConfig",
        )
        for (className in classNames) {
            val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
            val method = runCatching { clazz.getMethod("checkPins", List::class.java) }.getOrNull()
            if (method != null) {
                engine.hook(method, object : MethodHook() {
                    override fun before(invocation: Invocation) {
                        if (!config().sslNetworkConfig) return
                    invocation.setResult(null)
                    emit(
                        kind = NetworkKind.SSL_CONTEXT,
                        operation = NetworkOperation.SSL_INIT,
                        params = mapOf("api" to "SSLContext.init"),
                    )
                    }
                })
            }
        }
    }

    /**
     * 混淆 OkHttp 堆栈指纹发现(JustTrustMe++ 核心创新):
     * hook OpenSSLSocketFactoryImpl.createSocket(永不混淆的系统类),
     * 通过调用栈中的字段结构指纹定位混淆后的 RealConnection → Address → CertificatePinner/OkHostnameVerifier。
     */
    private fun hookObfuscatedOkHttp() {
        val clazz = runCatching {
            Class.forName("com.android.org.conscrypt.OpenSSLSocketFactoryImpl")
        }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("createSocket")
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().sslOkhttpPinner || !config().sslOkhttpVerifier) return
                // 每个 SSL 连接触发一次堆栈分析
                analyzeStackForObfuscatedClasses()
            }
        })
    }

    /** 分析当前堆栈,通过字段指纹发现混淆后的 OkHttp 类。 */
    private fun analyzeStackForObfuscatedClasses() {
        try {
            val stack = Thread.currentThread().stackTrace
            // 在堆栈中查找 RealConnection 的字段指纹
            for (frame in stack) {
                val className = frame.className
                if (discoveredClasses.containsKey(className)) continue
                val clazz = runCatching { Class.forName(className) }.getOrNull() ?: continue
                if (isRealConnectionFingerprint(clazz)) {
                    discoveredClasses[className] = clazz
                    // 从 RealConnection 导航到 Route → Address → CertificatePinner/OkHostnameVerifier
                    discoverFromRealConnection(clazz)
                }
            }
        } catch (_: Throwable) { /* 降级:不阻塞 */ }
    }

    /** 字段指纹匹配:RealConnection 有 4 int + 1 List(final) + 2 Socket + 1 boolean + 1 long 非静态字段。 */
    private fun isRealConnectionFingerprint(clazz: Class<*>): Boolean {
        val fields = clazz.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        var intCount = 0
        var listCount = 0
        var socketCount = 0
        var boolCount = 0
        var longCount = 0
        for (field in fields) {
            when (field.type) {
                Int::class.javaPrimitiveType, Integer::class.java -> intCount++
                List::class.java -> if (java.lang.reflect.Modifier.isFinal(field.modifiers)) listCount++
                java.net.Socket::class.java -> socketCount++
                Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java -> boolCount++
                Long::class.javaPrimitiveType, java.lang.Long::class.java -> longCount++
            }
        }
        return intCount == 4 && listCount == 1 && socketCount == 2 && boolCount == 1 && longCount == 1
    }

    /** 从 RealConnection 导航到 Route → Address → CertificatePinner + OkHostnameVerifier。 */
    private fun discoverFromRealConnection(realConnectionClass: Class<*>) {
        try {
            // RealConnection 构造器有 1 个参数(Route 类型)
            val constructors = realConnectionClass.declaredConstructors
            if (constructors.isEmpty()) return
            val routeParam = constructors[0].parameterTypes.firstOrNull() ?: return
            val routeClass = routeParam

            // Route 构造器有 3 个参数,第 1 个是 Address
            val routeConstructors = routeClass.declaredConstructors
            if (routeConstructors.isEmpty()) return
            val addressParam = routeConstructors[0].parameterTypes.firstOrNull() ?: return
            val addressClass = addressParam

            // Address 构造器有 12 个参数,参数 6 是 CertificatePinner,参数 5 是 HostnameVerifier
            val addressConstructors = addressClass.declaredConstructors
            if (addressConstructors.isEmpty()) return
            val ctor = addressConstructors[0]
            if (ctor.parameterTypes.size < 12) return

            val pinnerType = ctor.parameterTypes[6]
            val verifierType = ctor.parameterTypes[5]

            // hook CertificatePinner.check
            val pinnerMethod = runCatching {
                pinnerType.getMethod("check", String::class.java, List::class.java)
            }.getOrNull()
            if (pinnerMethod != null) {
                engine.hook(pinnerMethod, object : MethodHook() {
                    override fun before(invocation: Invocation) {
                        if (!config().sslOkhttpPinner) return
                        invocation.setResult(null)
                        emit(
                            kind = NetworkKind.SSL_OKHTTP_PINNER,
                            operation = NetworkOperation.SSL_PIN,
                            params = mapOf("host" to (invocation.getArg(0) as? String ?: ""), "source" to "obfuscated"),
                        )
                    }
                })
            }

            // hook HostnameVerifier.verify
            val verifierMethod = runCatching {
                verifierType.getMethod("verify", String::class.java, SSLSession::class.java)
            }.getOrNull()
            if (verifierMethod != null) {
                engine.hook(verifierMethod, object : MethodHook() {
                    override fun before(invocation: Invocation) {
                        if (!config().sslOkhttpVerifier) return
                        invocation.setResult(true)
                        emit(
                            kind = NetworkKind.SSL_OKHTTP_VERIFIER,
                            operation = NetworkOperation.SSL_VERIFY,
                            params = mapOf("hostname" to (invocation.getArg(0) as? String ?: ""), "source" to "obfuscated"),
                        )
                    }
                })
            }
        } catch (_: Throwable) { /* 降级 */ }
    }
}

/** Trust-all TrustManager:空操作验证,接受所有证书。 */
private object TrustAllTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
