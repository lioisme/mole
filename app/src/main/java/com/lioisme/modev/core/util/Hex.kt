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
}