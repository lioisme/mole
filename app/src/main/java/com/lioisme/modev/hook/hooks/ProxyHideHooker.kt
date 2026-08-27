package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * WiFi 代理隐藏 Hooker(V30):隐藏系统/应用层代理配置,防止应用检测到 MITM 代理。
 *
 * 设计原则(第一性原则):
 * - 每个检测通道独立 hook,不可遗漏;
 * - 返回 null/空(表示「未配置代理」),不抛异常;
 * - 覆盖:System.getProperty + ConnectivityManager + Settings.Global + OkHttp ProxySelector。
 */
class ProxyHideHooker(
    context: ProbeContext,
) : NetworkBaseHooker(context) {

    override fun install() {
        hookSystemProperty()
        hookConnectivityManager()
        hookSettingsGlobal()
        hookOkHttpProxy()
    }

    /**
     * System.getProperty 代理属性隐藏:
     * http.proxyHost / http.proxyPort / https.proxyHost / https.proxyPort /
     * http.nonProxyHosts / socksProxyHost / socksProxyPort
     * 返回 null 表示未配置。
     */
    private fun hookSystemProperty() {
        val proxyKeys = setOf(
            "http.proxyHost", "http.proxyPort",
            "https.proxyHost", "https.proxyPort",
            "http.nonProxyHosts",
            "socksProxyHost", "socksProxyPort",
        )
        val method = runCatching {
            System::class.java.getMethod("getProperty", String::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().proxySystemProp) return
                val key = invocation.getArg(0) as? String ?: return
                if (key in proxyKeys) {
                    invocation.setResult(null)
                    emit(
                        kind = NetworkKind.PROXY_SYSTEM_PROP,
                        operation = NetworkOperation.PROP_GET,
                        params = mapOf("key" to key),
                    )
                }
            }
        })
    }

    /**
     * ConnectivityManager 代理查询隐藏:
     * - getDefaultProxy(): 返回 null
     * - getLinkProperties().getHttpProxy(): 返回 null
     */
    private fun hookConnectivityManager() {
        val cmClass = runCatching { Class.forName("android.net.ConnectivityManager") }.getOrNull() ?: return
        val getDefaultProxy = runCatching {
            cmClass.getMethod("getDefaultProxy")
        }.getOrNull()
        if (getDefaultProxy != null) {
            engine.hook(getDefaultProxy, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().proxyConnectivity) return
                    invocation.setResult(null)
                    emit(
                        kind = NetworkKind.PROXY_CONNECTIVITY,
                        operation = NetworkOperation.PROP_DEFAULT,
                    )
                }
            })
        }

        val lpClass = runCatching { Class.forName("android.net.LinkProperties") }.getOrNull() ?: return
        val getHttpProxy = runCatching { lpClass.getMethod("getHttpProxy") }.getOrNull()
        if (getHttpProxy != null) {
            engine.hook(getHttpProxy, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().proxyConnectivity) return
                    invocation.setResult(null)
                    emit(
                        kind = NetworkKind.PROXY_CONNECTIVITY,
                        operation = NetworkOperation.PROP_LINK,
                    )
                }
            })
        }
    }

    /**
     * Settings.Global 代理隐藏:
     * http_proxy / global_http_proxy_host / global_http_proxy_port
     * 返回 null 表示未配置。
     */
    private fun hookSettingsGlobal() {
        val proxyKeys = setOf(
            "http_proxy",
            "global_http_proxy_host",
            "global_http_proxy_port",
            "global_http_proxy_pac",
        )
        val clazz = runCatching { Class.forName("android.provider.Settings\$Global") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("getString", android.content.ContentResolver::class.java, String::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().proxySettings) return
                val key = invocation.getArg(1) as? String ?: return
                if (key in proxyKeys) {
                    invocation.setResult(null)
                    emit(
                        kind = NetworkKind.PROXY_SETTINGS,
                        operation = NetworkOperation.PROP_GLOBAL,
                        params = mapOf("key" to key),
                    )
                }
            }
        })
    }

    /**
     * OkHttp ProxySelector 替换:返回空列表(无代理)。
     * 拦截 java.net.ProxySelector.getDefault().select(URI)。
     */
    private fun hookOkHttpProxy() {
        val clazz = runCatching { Class.forName("java.net.ProxySelector") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("select", java.net.URI::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().proxyOkhttp) return
                val thisClass = invocation.thisObject?.javaClass?.name ?: return
                if (thisClass.contains("ProxySelector") || thisClass.contains("Default")) {
                    invocation.setResult(emptyList<java.net.Proxy>())
                    emit(
                        kind = NetworkKind.PROXY_OKHTTP,
                        operation = NetworkOperation.PROP_OKHTTP,
                    )
                }
            }
        })
    }
}
