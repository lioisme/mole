package com.lioisme.modev.core.model

/**
 * 网络环境监听类别:覆盖 SSL Pinning 绕过 / 代理隐藏 / VPN 隐藏 / 请求监控四大子域。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致:
 * - 干预类(SSL/代理/VPN)默认开:使绕过/隐藏生效;
 * - 观测类(REQUEST)默认开:捕获请求+堆栈供 Reqable 追溯。
 */
enum class NetworkKind {
    // ---- SSL Pinning 绕过(干预类) ----

    /** SSLContext.init TrustManager 替换。 */
    SSL_CONTEXT,

    /** Conscrypt TrustManagerImpl.checkServerTrusted 拦截。 */
    SSL_TRUST_MANAGER,

    /** OkHttp CertificatePinner.check 拦截(含混淆版堆栈指纹发现)。 */
    SSL_OKHTTP_PINNER,

    /** OkHttp OkHostnameVerifier.verify 拦截。 */
    SSL_OKHTTP_VERIFIER,

    /** WebView onReceivedSslError 自动 proceed。 */
    SSL_WEBVIEW,

    /** Android NetworkSecurityConfig pin 绕过。 */
    SSL_NETWORK_CONFIG,

    // ---- 代理隐藏(伪装类) ----

    /** System.getProperty 代理属性隐藏。 */
    PROXY_SYSTEM_PROP,

    /** ConnectivityManager 代理查询隐藏。 */
    PROXY_CONNECTIVITY,

    /** Settings.Global 代理隐藏。 */
    PROXY_SETTINGS,

    /** OkHttp ProxySelector 隐藏。 */
    PROXY_OKHTTP,

    // ---- VPN 隐藏(伪装类) ----

    /** NetworkCapabilities TRANSPORT_VPN 隐藏。 */
    VPN_CAPABILITIES,

    /** NetworkInterface VPN 接口隐藏(tun/wg/ppp 重命名)。 */
    VPN_INTERFACE,

    /** NetworkInfo TYPE_VPN 隐藏。 */
    VPN_NETWORK_INFO,

    /** LinkProperties VPN 路由隐藏。 */
    VPN_LINK_PROPERTIES,

    // ---- 请求监控(观测类) ----

    /** 网络请求/响应捕获(含 Reqable 堆栈)。 */
    REQUEST,
}
