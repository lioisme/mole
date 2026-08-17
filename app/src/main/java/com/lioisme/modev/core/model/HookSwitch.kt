package com.lioisme.modev.core.model

/**
 * 算法捕获配置。注入进程按包名持有各自配置,宿主 App 通过通道推送更新。
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
    val logStack: Boolean = true,
    val maxStackDepth: Int = 24,
    /** 单字段字节上限(全量捕获保险丝);0 表示无限。 */
    val maxBytesPerField: Int = 1_048_576,
    /** 调用栈前缀 denylist:任一帧命中即跳过该记录;默认空 = 全捕获。 */
    val ignoreStackPrefixes: List<String> = emptyList(),
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxHexChars: Int = 1_048_576 * 2,
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxTextChars: Int = 1_048_576,
) {
    /** 是否存在任何需要 Hook 的类别。 */
    val anyEnabled: Boolean
        get() = messageDigest || cipher || mac || signature || keyParam || keyGeneration ||
            keyFactory || keyStore || keyAgreement || random || base64

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
    }

    companion object {
        val DEFAULT = HookSwitch()
    }
}