package com.lioisme.modev.core.model

/**
 * 全局算法捕获配置(V10:全应用共享一份,与包名解耦)。
 * 宿主 App 持久化于 `switch_global` 并经通道推送,注入进程实时读取更新。
 */
data class HookSwitch(
    val messageDigest: Boolean = true,
    val cipher: Boolean = true,
    val mac: Boolean = true,
    val signature: Boolean = true,
    val keyParam: Boolean = true,
    val keyGeneration: Boolean = true,
    val keyFactory: Boolean = true,
    val keyStore: Boolean = true,
    val keyAgreement: Boolean = true,
    val random: Boolean = true,
    val base64: Boolean = false,
    /** WebView JS 加解密监听(WebCrypto / CryptoJS / JSEncrypt,V12 起;默认关,同 base64 先例)。 */
    val webView: Boolean = false,
    val logStack: Boolean = true,
    val maxStackDepth: Int = 24,
    /** 单字段字节上限(全量捕获保险丝);0 表示无限。 */
    val maxBytesPerField: Int = RecordLimits.DEFAULT_MAX_BYTES_PER_FIELD,
    /** 调用栈前缀 denylist:任一帧命中即跳过该记录;默认空 = 全捕获。 */
    val ignoreStackPrefixes: List<String> = emptyList(),
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxHexChars: Int = RecordLimits.DEFAULT_MAX_HEX_CHARS,
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxTextChars: Int = RecordLimits.DEFAULT_MAX_TEXT_CHARS,
) {
    /** 是否存在任何需要 Hook 的类别。 */
    val anyEnabled: Boolean
        get() = messageDigest || cipher || mac || signature || keyParam || keyGeneration ||
            keyFactory || keyStore || keyAgreement || random || base64 || webView

    fun enabled(kind: CryptoKind): Boolean = when (kind) {
        CryptoKind.MESSAGE_DIGEST -> messageDigest
        CryptoKind.CIPHER -> cipher
        CryptoKind.MAC -> mac
        CryptoKind.SIGNATURE -> signature
        CryptoKind.KEY_PARAM -> keyParam
        CryptoKind.KEY_GENERATION -> keyGeneration
        CryptoKind.KEY_FACTORY -> keyFactory
        CryptoKind.KEY_STORE -> keyStore
        CryptoKind.KEY_AGREEMENT -> keyAgreement
        CryptoKind.RANDOM -> random
        CryptoKind.BASE64 -> base64
        CryptoKind.WEB_CRYPTO -> webView
    }

    companion object {
        val DEFAULT = HookSwitch()
    }
}