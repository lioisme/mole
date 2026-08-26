package com.lioisme.modev.core.model

/**
 * WebView 监听操作枚举(V29):映射到 wire 记录的 operation 字段。
 * 操作名与注入侧 Hooker 一一对应,新增操作必须同步 HookSwitch / EffectiveSwitch。
 */
enum class WebViewOperation {
    /** loadUrl(String)。 */
    LOAD_URL,

    /** loadUrl(String, Map)。 */
    LOAD_URL_WITH_HEADERS,

    /** loadData(String, String, String)。 */
    LOAD_DATA,

    /** loadDataWithBaseURL(String, String, String, String, String)。 */
    LOAD_DATA_WITH_BASE_URL,

    /** evaluateJavascript(String, ValueCallback)。 */
    EVALUATE_JS,

    /** evaluateJavascript(String) (旧 API)。 */
    EVALUATE_JS_LEGACY,

    /** addJavascriptInterface(Object, String)。 */
    ADD_JS_INTERFACE,

    /** setWebContentsDebuggingEnabled(Boolean)。 */
    SET_DEBUGGING,

    /** onPageFinished(WebViewClient 回调)。 */
    PAGE_FINISHED,

    /** JS Crypto:encrypt。 */
    JS_ENCRYPT,

    /** JS Crypto:decrypt。 */
    JS_DECRYPT,

    /** JS Crypto:digest。 */
    JS_DIGEST,

    /** JS Crypto:sign。 */
    JS_SIGN,

    /** JS Crypto:verify。 */
    JS_VERIFY,

    /** JS Crypto:importKey。 */
    JS_IMPORT_KEY,

    /** JS Crypto:generateKey。 */
    JS_GENERATE_KEY,
}
