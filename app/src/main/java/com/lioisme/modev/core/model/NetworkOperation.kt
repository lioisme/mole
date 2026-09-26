package com.lioisme.modev.core.model

/**
 * 网络环境监听操作:具体 API 动作,[kind] 归属到类别。
 * kind = 用户开关粒度([HookSwitch.networkEnabled] 门控),
 * operation = 列表元行/详情页展示粒度。
 */
enum class NetworkOperation(
    val kind: NetworkKind,
) {
    // ---- SSL Pinning 绕过 ----

    /** SSLContext.init TrustManager 替换。 */
    SSL_INIT(NetworkKind.SSL_CONTEXT),

    /** TrustManagerImpl.checkServerTrusted 拦截。 */
    SSL_CHECK(NetworkKind.SSL_TRUST_MANAGER),

    /** CertificatePinner.check 拦截。 */
    SSL_PIN(NetworkKind.SSL_OKHTTP_PINNER),

    /** OkHostnameVerifier.verify 拦截。 */
    SSL_VERIFY(NetworkKind.SSL_OKHTTP_VERIFIER),

    /** WebView onReceivedSslError proceed。 */
    SSL_WEBVIEW_ERROR(NetworkKind.SSL_WEBVIEW),

    /** NetworkSecurityConfig pin 绕过。 */
    SSL_CONFIG_CHECK(NetworkKind.SSL_NETWORK_CONFIG),

    // ---- 代理隐藏 ----

    /** System.getProperty 代理属性读取拦截。 */
    PROP_GET(NetworkKind.PROXY_SYSTEM_PROP),

    /** ConnectivityManager.getDefaultProxy 拦截。 */
    PROP_DEFAULT(NetworkKind.PROXY_CONNECTIVITY),

    /** LinkProperties.getHttpProxy 拦截。 */
    PROP_LINK(NetworkKind.PROXY_CONNECTIVITY),

    /** Settings.Global 代理读取拦截。 */
    PROP_GLOBAL(NetworkKind.PROXY_SETTINGS),

    /** OkHttp ProxySelector.getDefault 拦截。 */
    PROP_OKHTTP(NetworkKind.PROXY_OKHTTP),

    // ---- VPN 隐藏 ----

    /** NetworkCapabilities.hasTransport(TRANSPORT_VPN) 拦截。 */
    VPN_HAS_TRANSPORT(NetworkKind.VPN_CAPABILITIES),

    /** NetworkCapabilities.getCapabilities 追加 NOT_VPN。 */
    VPN_GET_CAPS(NetworkKind.VPN_CAPABILITIES),

    /** NetworkCapabilities.getTransportInfo 返回 null。 */
    VPN_TRANSPORT_INFO(NetworkKind.VPN_CAPABILITIES),

    /** NetworkInterface.isVirtual 拦截。 */
    VPN_IS_VIRTUAL(NetworkKind.VPN_INTERFACE),

    /** NetworkInterface.getName 重命名 VPN 接口。 */
    VPN_IFACE_NAME(NetworkKind.VPN_INTERFACE),

    /** NetworkInterface.getByName 返回 null。 */
    VPN_IFACE_BY_NAME(NetworkKind.VPN_INTERFACE),

    /** NetworkInterface.getNetworkInterfaces 过滤 VPN 接口。 */
    VPN_IFACE_ENUM(NetworkKind.VPN_INTERFACE),

    /** NetworkInfo.getType 返回 TYPE_WIFI。 */
    VPN_TYPE(NetworkKind.VPN_NETWORK_INFO),

    /** NetworkInfo.getTypeName 返回 "WIFI"。 */
    VPN_TYPE_NAME(NetworkKind.VPN_NETWORK_INFO),

    /** LinkProperties.getInterfaceName 返回 null。 */
    VPN_IFACE_PROP(NetworkKind.VPN_LINK_PROPERTIES),

    /** LinkProperties.getRoutes 过滤 VPN 路由。 */
    VPN_ROUTES(NetworkKind.VPN_LINK_PROPERTIES),

    // ---- 请求监控 ----

    /** HTTP 请求发起(OkHttp/HttpURLConnection)。 */
    REQUEST_START(NetworkKind.REQUEST),

    /** HTTP 响应接收。 */
    REQUEST_END(NetworkKind.REQUEST),
}
