package com.lioisme.modev.core.model

/**
 * WebView 监听类别(V29 一等公民):覆盖 WebView 全生命周期行为。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致:
 * - 高信号默认开:PAGE_LOAD/EVALUATE_JS/JS_BRIDGE/DEBUG(低频高价值);
 * - 高噪声默认关:JS_CRYPTO(JS 密码学包装,App 启动即可能触发大量调用)。
 */
enum class WebViewKind {
    /** 页面加载(loadUrl/loadData/loadDataWithBaseURL)。 */
    PAGE_LOAD,

    /** JS 执行(evaluateJavascript)。 */
    EVALUATE_JS,

    /** JS 桥注册(addJavascriptInterface)。 */
    JS_BRIDGE,

    /** 调试开关(setWebContentsDebuggingEnabled)。 */
    DEBUG,

    /** JS 密码学包装(WebCrypto/CryptoJS/JSEncrypt,迁移自 WEB_CRYPTO)。 */
    JS_CRYPTO,
}
