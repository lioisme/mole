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

    /** SSL 目标类惰性安装幂等集(D-9 同模式:安装期未加载的类经 loadClass 监视器首装)。 */
    private val installedClasses = HashSet<String>()

    override fun install() {
        // SSLContext 为启动即载的 boot 类,直接安装。
        hookSSLContext()
        // 其余目标类(conscrypt / okhttp / webview / network config)安装期多未加载,
        // 先即时尝试(已加载即命中),再挂单一 loadClass 监视器惰性补装。
        SSL_LAZY_CLASSES.forEach { installSslHooksFor(it) }
        armLazySslHooks()
    }

    /** 单一 loadClass 监视器:任一 SSL 目标类首次被加载即惰性安装(V38 收敛,替代逐类监视器)。 */
    private fun armLazySslHooks() {
        val loadClass = runCatching {
            ClassLoader::class.java.getMethod("loadClass", String::class.java)
        }.getOrNull() ?: return
        engine.hook(loadClass, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val name = invocation.getArg(0) as? String ?: return
                if (name in SSL_LAZY_CLASSES) installSslHooksFor(name)
            }
        })
    }

    /** 类名 → 安装动作分派(幂等,已装即跳过)。 */
    @Synchronized
    private fun installSslHooksFor(className: String) {
        if (className in installedClasses) return
        // V38:okhttp 等 app dex 类不经过 java 层 loadClass(ART 同 loader 引用解析
        // 走 native fast path,监视器不可见),且模块委派链 forName 不可见——必须经
        // app classloader 显式装载(initialize=false 零副作用);失败(类尚不存在)
        // 不登记,等下一触发点重试。
        val loader = contextRef.appClassLoader()
        val viaAppLoader: Class<*>? = if (loader != null) {
            runCatching { Class.forName(className, false, loader) }.getOrNull()
        } else {
            null
        }
        val clazz = viaAppLoader ?: runCatching { Class.forName(className) }.getOrNull() ?: return
        installedClasses += className
        android.util.Log.i("HookInstaller", "SSL class installed: $className via=${if (viaAppLoader != null) "app-loader" else "boot"}")
        when (className) {
            CONSCRYPT_PLATFORM -> installPlatformHooksFor(clazz)
            CONSCRYPT_TMI -> installTrustManagerHooksFor(clazz)
            OKHTTP_PINNER, OKHTTP_CHAIN_CLEANER -> installPinnerHooksFor(clazz)
            OKHTTP_HOSTNAME_VERIFIER, OKHTTP_ANDROID_PLATFORM -> installVerifierHooksFor(clazz)
            WEBVIEW_CLIENT -> installWebViewSslHooks()
            NETCFG_TRUST_MANAGER, NETCFG_CONFIG -> installNetworkConfigHooksFor(clazz)
            CONSCRYPT_SOCKET_FACTORY -> installObfuscatedHooks()
        }
    }

    /**
     * Platform.checkServerTrusted 桥(V38,dexdump 实锤签名):native 握手验证经
     * 两个 static 桥进入 Java——AbstractConscryptSocket / ConscryptEngine 参数版
     * (X509TrustManager, X509Certificate[], String, Conscrypt*);TrustManagerImpl
     * 公开重载不在 native 握手路径上。before setResult(null) 短路 = 验证放行。
     */
    private fun installPlatformHooksFor(clazz: Class<*>) {
        // 参数类型为 hide 类(com.android.org.conscrypt.*),编译期不可见,反射解析
        // (本 hooker 即干预型 hide-API 反射范式;TrustAll/CustomX509TrustManager 警告
        // 同理,均为既有的 intent-bypass 实现载体)
        @Suppress("PrivateApi", "TrustAllX509TrustManager", "CustomX509TrustManager")
        fun resolve(name: String): Class<*>? = runCatching { Class.forName(name) }.getOrNull()
        val socketParam = resolve("com.android.org.conscrypt.AbstractConscryptSocket")
        val engineParam = resolve("com.android.org.conscrypt.ConscryptEngine")
        socketParam?.let { hookMethodSafe(clazz, "checkServerTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, javax.net.ssl.X509TrustManager::class.java, Array<X509Certificate>::class.java, String::class.java, it) }
        engineParam?.let { hookMethodSafe(clazz, "checkServerTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, javax.net.ssl.X509TrustManager::class.java, Array<X509Certificate>::class.java, String::class.java, it) }
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
            override fun before(invocation: Invocation) {
                // V38:okhttp 等 app dex 类此刻必然已加载(握手前调用方已构造),
                // 以 init 为补装触发点;幂等,已装即跳过。
                SSL_LAZY_CLASSES.forEach { installSslHooksFor(it) }
                if (!featureOn(NetworkKind.SSL_CONTEXT)) return
                // V38 修复:原 after 反射改写 impl.trustManager 字段在 Android 14
                // conscrypt 字段布局下 getDeclaredField 失败 → 静默失效(二态与记录
                // 面全失,E-SSL-01 矩阵实锤)。改 setArg(1) 入参改写(与 D-11 录屏
                // 解限同已证路径),零反射、版本无关。
                invocation.setArg(1, arrayOf<TrustManager>(TrustAllTrustManager))
            }

            override fun after(invocation: Invocation) {
                if (!featureOn(NetworkKind.SSL_CONTEXT)) return
                emit(
                    kind = NetworkKind.SSL_CONTEXT,
                    operation = NetworkOperation.SSL_INIT,
                    params = mapOf("api" to "SSLContext.init"),
                )
            }
        })
    }

    /**
     * Conscrypt TrustManagerImpl / Platform 拦截:Android 默认 TLS 提供者的验证决策点。
     * 覆盖 checkServerTrusted 全家族(3/4/5 参数)+ checkTrusted(5/6 参数)。
     */
    private fun installTrustManagerHooksFor(clazz: Class<*>) {
        // checkServerTrusted(X509Certificate[], String)
        hookMethodSafe(clazz, "checkServerTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, Array<X509Certificate>::class.java, String::class.java)
        // checkServerTrusted(X509Certificate[], String, String)
        hookMethodSafe(clazz, "checkServerTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, Array<X509Certificate>::class.java, String::class.java, String::class.java)
        // checkServerTrusted(X509Certificate[], String, SSLSession)
        hookMethodSafe(clazz, "checkServerTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, Array<X509Certificate>::class.java, String::class.java, SSLSession::class.java)
        // V38:HttpsURLConnection(SSLSocket)实际走 Socket 重载;SSLEngine 覆盖引擎路径
        hookMethodSafe(clazz, "checkServerTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, Array<X509Certificate>::class.java, String::class.java, java.net.Socket::class.java)
        hookMethodSafe(clazz, "checkServerTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, Array<X509Certificate>::class.java, String::class.java, javax.net.ssl.SSLEngine::class.java)
        // checkTrusted(X509Certificate[], String, SSLSession, SSLParameters, boolean)
        hookMethodSafe(clazz, "checkTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, Array<X509Certificate>::class.java, String::class.java, SSLSession::class.java, javax.net.ssl.SSLParameters::class.java, Boolean::class.javaPrimitiveType!!)
        // checkTrusted(X509Certificate[], byte[], byte[], String, String, boolean)
        hookMethodSafe(clazz, "checkTrusted", NetworkKind.SSL_TRUST_MANAGER, NetworkOperation.SSL_CHECK, Array<X509Certificate>::class.java, ByteArray::class.java, ByteArray::class.java, String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!)
    }

    private fun hookMethodSafe(
        clazz: Class<*>,
        methodName: String,
        kind: NetworkKind,
        operation: NetworkOperation,
        vararg paramTypes: Class<*>,
    ) {
        // V38:getMethod 仅公开方法;conscrypt Platform 桥为包私有静态(dexdump 实锤
        // access=0x0008 无 PUBLIC 位),需要 getDeclaredMethod + setAccessible。
        val method = runCatching { clazz.getMethod(methodName, *paramTypes) }
            .recoverCatching { clazz.getDeclaredMethod(methodName, *paramTypes).also { it.isAccessible = true } }
            .onFailure { android.util.Log.i("HookInstaller", "SSL lookup fail: ${clazz.name}.$methodName ex=${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull()
        if (method == null) {
            // V38 诊断:conscrypt/okhttp 签名漂移时静默跳过难归因(E-SSL-02 实锤),
            // 记一条可观测行(含可用同名重载),便于矩阵失败时定位签名差异。
            val overloads = runCatching {
                (clazz.methods + clazz.declaredMethods).filter { it.name == methodName }
                    .joinToString("|") { m -> m.parameterTypes.joinToString(",") { p -> p.simpleName } }
            }.getOrNull()
            android.util.Log.i("HookInstaller", "SSL hook skip: ${clazz.name}.$methodName(${paramTypes.joinToString(",") { it.simpleName }}) not found; declaredOverloads=[$overloads]")
            return
        }
        android.util.Log.i("HookInstaller", "SSL hook ok: ${clazz.name}.$methodName")
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(kind)) return
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
                    kind = kind,
                    operation = operation,
                    params = mapOf("target" to "${clazz.simpleName}.$methodName"),
                )
            }
        })
    }

    /**
     * OkHttp CertificatePinner.check 拦截:跳过 pin 验证。
     * 覆盖 OkHttp 3.x(check(String, List)) 和 4.2+(check$okhttp(String, Function0))。
     */
    private fun installPinnerHooksFor(clazz: Class<*>) {
        // V38:check$okhttp 第二参 Function0 在模块与 app 双份 stdlib 下是不同 Class,
        // getDeclaredMethod 按引用相等比较必失败(NoSuchMethodException 实锤)——
        // 参数类型经目标 app loader 解析。
        val fn0 = runCatching {
            Class.forName("kotlin.jvm.functions.Function0", false, contextRef.appClassLoader())
        }.getOrNull() ?: kotlin.jvm.functions.Function0::class.java
        // check(String, List) - OkHttp 3.x
        hookMethodSafe(clazz, "check", NetworkKind.SSL_OKHTTP_PINNER, NetworkOperation.SSL_PIN, String::class.java, List::class.java)
        // check$okhttp(String, Function0) - OkHttp 4.2+
        hookMethodSafe(clazz, "check\$okhttp", NetworkKind.SSL_OKHTTP_PINNER, NetworkOperation.SSL_PIN, String::class.java, fn0)
        // check(String, List) - any remaining
        hookMethodSafe(clazz, "check", NetworkKind.SSL_OKHTTP_PINNER, NetworkOperation.SSL_PIN, String::class.java, java.util.List::class.java)
    }

    /**
     * OkHttp OkHostnameVerifier.verify 拦截:始终返回 true。
     * 覆盖 verify(String, SSLSession) 和 verify(String, X509Certificate)。
     */
    private fun installVerifierHooksFor(clazz: Class<*>) {
        // verify(String, SSLSession)
        val verifySession = runCatching {
            clazz.getMethod("verify", String::class.java, SSLSession::class.java)
        }.getOrNull()
        if (verifySession != null) {
            engine.hook(verifySession, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(NetworkKind.SSL_OKHTTP_VERIFIER)) return
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
                    if (!featureOn(NetworkKind.SSL_OKHTTP_VERIFIER)) return
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

    /**
     * WebView SSL 错误处理:自动 proceed 接受证书。
     * 覆盖:android.webkit.WebViewClient.onReceivedSslError(WebView, SslErrorHandler, SslError)
     */
    private fun installWebViewSslHooks() {
        val clazz = runCatching { Class.forName(WEBVIEW_CLIENT) }.getOrNull() ?: return
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
                if (!featureOn(NetworkKind.SSL_WEBVIEW)) return
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
     *
     * V38 修复:原实现误发 SSL_CONTEXT/SSL_INIT 记录(E-SSL-06 记录面 count=0 根因,
     * 记录落在错误 kind 下),改发 SSL_NETWORK_CONFIG/SSL_CONFIG_CHECK。
     */
    private fun installNetworkConfigHooksFor(clazz: Class<*>) {
        val method = runCatching { clazz.getMethod("checkPins", List::class.java) }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(NetworkKind.SSL_NETWORK_CONFIG)) return
                invocation.setResult(null)
                emit(
                    kind = NetworkKind.SSL_NETWORK_CONFIG,
                    operation = NetworkOperation.SSL_CONFIG_CHECK,
                    params = mapOf("api" to "NetworkSecurityTrustManager.checkPins"),
                )
            }
        })
    }

    /**
     * 混淆 OkHttp 堆栈指纹发现(JustTrustMe++ 核心创新):
     * hook OpenSSLSocketFactoryImpl.createSocket(永不混淆的系统类),
     * 通过调用栈中的字段结构指纹定位混淆后的 RealConnection → Address → CertificatePinner/OkHostnameVerifier。
     */
    private fun installObfuscatedHooks() {
        val clazz = runCatching { Class.forName(CONSCRYPT_SOCKET_FACTORY) }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("createSocket")
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(NetworkKind.SSL_OKHTTP_PINNER) || !featureOn(NetworkKind.SSL_OKHTTP_VERIFIER)) return
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
                        if (!featureOn(NetworkKind.SSL_OKHTTP_PINNER)) return
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
                        if (!featureOn(NetworkKind.SSL_OKHTTP_VERIFIER)) return
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

    companion object {
        private const val CONSCRYPT_TMI = "com.android.org.conscrypt.TrustManagerImpl"
        private const val CONSCRYPT_PLATFORM = "com.android.org.conscrypt.Platform"
        private const val CONSCRYPT_SOCKET_FACTORY = "com.android.org.conscrypt.OpenSSLSocketFactoryImpl"
        private const val OKHTTP_PINNER = "okhttp3.CertificatePinner"
        private const val OKHTTP_CHAIN_CLEANER = "okhttp3.internal.tls.CertificateChainCleaner"
        private const val OKHTTP_HOSTNAME_VERIFIER = "okhttp3.internal.tls.OkHostnameVerifier"
        private const val OKHTTP_ANDROID_PLATFORM = "okhttp3.internal.tls.ApplicationAndroidPlatform"
        private const val WEBVIEW_CLIENT = "android.webkit.WebViewClient"
        private const val NETCFG_TRUST_MANAGER = "android.security.net.config.NetworkSecurityTrustManager"
        private const val NETCFG_CONFIG = "android.security.net.config.NetworkSecurityConfig"

        /** 安装期可能未加载、需 loadClass 监视器惰性补装的 SSL 目标类全集。 */
        private val SSL_LAZY_CLASSES = setOf(
            CONSCRYPT_TMI, CONSCRYPT_PLATFORM, CONSCRYPT_SOCKET_FACTORY,
            OKHTTP_PINNER, OKHTTP_CHAIN_CLEANER,
            OKHTTP_HOSTNAME_VERIFIER, OKHTTP_ANDROID_PLATFORM,
            WEBVIEW_CLIENT, NETCFG_TRUST_MANAGER, NETCFG_CONFIG,
        )
    }
}

/** Trust-all TrustManager:空操作验证,接受所有证书。 */
private object TrustAllTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
