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
    val base64: Boolean = false,
    val logStack: Boolean = true,
    val maxStackDepth: Int = 24,
    val maxHexChars: Int = 1024,
    val maxTextChars: Int = 512,
) {
    /** 是否存在任何需要 Hook 的类别。 */
    val anyEnabled: Boolean
        get() = messageDigest || cipher || mac || signature || keyParam || keyGeneration || keyFactory || base64

    fun enabled(kind: CryptoKind): Boolean = when (kind) {
        CryptoKind.MESSAGE_DIGEST -> messageDigest
        CryptoKind.CIPHER -> cipher
        CryptoKind.MAC -> mac
        CryptoKind.SIGNATURE -> signature
        CryptoKind.KEY_PARAM -> keyParam
        CryptoKind.KEY_GENERATION -> keyGeneration
        CryptoKind.KEY_FACTORY -> keyFactory
        CryptoKind.BASE64 -> base64
    }

    companion object {
        val DEFAULT = HookSwitch()
    }
}