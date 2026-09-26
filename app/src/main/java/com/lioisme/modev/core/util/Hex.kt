package com.lioisme.modev.core.util

/**
 * 十六进制编码工具。
 */
object Hex {
    private val CHARS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String = encode(bytes, 0, bytes.size)

    fun encode(bytes: ByteArray, offset: Int, length: Int): String {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "invalid range" }
        val sb = StringBuilder(length * 2)
        for (i in offset until offset + length) {
            val v = bytes[i].toInt() and 0xFF
            sb.append(CHARS[v ushr 4])
            sb.append(CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    /**
     * 解码 hex 字符串(大小写均接受);空串返回空数组,非法字符或奇数长度返回 null。
     * 用于 WebView 探针回传的 hex 数据落地([com.lioisme.modev.hook.webview.WebViewEventMapper])。
     */
    fun decode(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = digitOf(hex[i * 2]) ?: return null
            val lo = digitOf(hex[i * 2 + 1]) ?: return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun digitOf(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }
}
