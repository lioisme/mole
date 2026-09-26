package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.core.model.activeSpoofProfile
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * WiFi 代理隐藏 Hooker:隐藏系统/应用层代理配置,防止应用检测到 MITM 代理。
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
        hookWifiInfo()
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

        // V43 T3/D-4:System.getProperty 三 hooker 共享单一广播注册点
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    if (!featureOn(NetworkKind.PROXY_SYSTEM_PROP)) return
                    val key = invocation.getArg(0) as? String ?: return
                    if (key in proxyKeys) {
                        // 代理属性未配置 = null 是 JDK 契约内的正确返回值(非异常路径),
                        // setResult 语义安全;与 RootProperty/DeviceSysProp 键空间不相交
                        invocation.setResult(null)
                        emit(
                            kind = NetworkKind.PROXY_SYSTEM_PROP,
                            operation = NetworkOperation.PROP_GET,
                            params = mapOf("key" to key),
                        )
                    }
                }
            },
        )
    }

    /**
     * ConnectivityManager 代理查询隐藏:
     * - getDefaultProxy: 返回 null
     * - getLinkProperties.getHttpProxy: 返回 null
     */
    private fun hookConnectivityManager() {
        val cmClass = runCatching { Class.forName("android.net.ConnectivityManager") }.getOrNull() ?: return
        val getDefaultProxy = runCatching {
            cmClass.getMethod("getDefaultProxy")
        }.getOrNull()
        if (getDefaultProxy != null) {
            engine.hook(getDefaultProxy, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(NetworkKind.PROXY_CONNECTIVITY)) return
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
                    if (!featureOn(NetworkKind.PROXY_CONNECTIVITY)) return
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
                if (!featureOn(NetworkKind.PROXY_SETTINGS)) return
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
     * 拦截 java.net.ProxySelector.getDefault.select(URI)。
     */
    private fun hookOkHttpProxy() {
        val clazz = runCatching { Class.forName("java.net.ProxySelector") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("select", java.net.URI::class.java)
        }.getOrNull() ?: return
        if (java.lang.reflect.Modifier.isAbstract(method.modifiers)) return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!featureOn(NetworkKind.PROXY_OKHTTP)) return
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

    /**
     * Wi-Fi 信息隐藏:拦截 WifiInfo.getSSID/getBSSID/getNetworkId,
     * 返回假值防止通过 Wi-Fi 信息检测代理/VPN 环境。
     */
    private fun hookWifiInfo() {
        val clazz = runCatching { Class.forName("android.net.wifi.WifiInfo") }.getOrNull() ?: return

        // getSSID:返回假 SSID(带双引号格式)
        val getSSID = runCatching { clazz.getMethod("getSSID") }.getOrNull()
        if (getSSID != null) {
            engine.hook(getSSID, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!featureOn(NetworkKind.PROXY_SYSTEM_PROP)) return
                    val current = invocation.result as? String ?: return
                    // 仅替换模拟器/空值特征,保留真实设备值
                    if (current == "<unknown ssid>" || current == "null" || current.isEmpty()) {
                        invocation.setResult("\"${config().activeSpoofProfile().fakeSsid}\"")
                        emit(
                            kind = NetworkKind.PROXY_SYSTEM_PROP,
                            operation = NetworkOperation.PROP_GET,
                            params = mapOf("key" to "wifi_ssid"),
                        )
                    }
                }
            })
        }

        // getBSSID:返回假 BSSID
        val getBSSID = runCatching { clazz.getMethod("getBSSID") }.getOrNull()
        if (getBSSID != null) {
            engine.hook(getBSSID, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!featureOn(NetworkKind.PROXY_SYSTEM_PROP)) return
                    val current = invocation.result as? String ?: return
                    if (current == "02:00:00:00:00:00" || current.isEmpty()) {
                        invocation.setResult(config().activeSpoofProfile().fakeBssid)
                        emit(
                            kind = NetworkKind.PROXY_SYSTEM_PROP,
                            operation = NetworkOperation.PROP_GET,
                            params = mapOf("key" to "wifi_bssid"),
                        )
                    }
                }
            })
        }

        // getNetworkId:返回 0(模拟器通常为 -1 或 0)
        val getNetworkId = runCatching { clazz.getMethod("getNetworkId") }.getOrNull()
        if (getNetworkId != null) {
            engine.hook(getNetworkId, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!featureOn(NetworkKind.PROXY_SYSTEM_PROP)) return
                    val current = invocation.result as? Int ?: return
                    if (current == -1) {
                        invocation.setResult(1)
                        emit(
                            kind = NetworkKind.PROXY_SYSTEM_PROP,
                            operation = NetworkOperation.PROP_GET,
                            params = mapOf("key" to "wifi_network_id"),
                        )
                    }
                }
            })
        }
    }
}
