package com.lioisme.modev.core.util

/**
 * Base64 编解码工具(RFC 4648 标准字母表,含填充,无换行)。
 *
 * 设计动机:BytesView 三态视图需在捕获侧(注入进程)与宿主侧同步生成/还原
 * Base64 表示;android.util.Base64 在纯 JVM 单测下不可用,且与 Hex 对称的
 * 纯 Kotlin 实现零框架依赖、可穷举测试 —— 与 [Hex] 同为 core/util 纯函数面。
 */
object Base64Util {

    private const val PAD = '='

    private val ENCODE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()

    private val DECODE = IntArray(128) { -1 }.also { table ->
        for (i in ENCODE.indices) table[ENCODE[i].code] = i
    }

    fun encode(bytes: ByteArray): String = encode(bytes, 0, bytes.size)

    /** 编码 [offset, offset+length) 区间字节;返回带填充的标准 Base64,无换行。 */
    fun encode(bytes: ByteArray, offset: Int, length: Int): String {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "invalid range" }
        if (length == 0) return ""
        val sb = StringBuilder(((length + 2) / 3) * 4)
        var i = offset
        val end = offset + length
        while (i + 2 < end) {
            val n = ((bytes[i].toInt() and 0xFF) shl 16) or
                ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                (bytes[i + 2].toInt() and 0xFF)
            sb.append(ENCODE[(n ushr 18) and 0x3F])
            sb.append(ENCODE[(n ushr 12) and 0x3F])
            sb.append(ENCODE[(n ushr 6) and 0x3F])
            sb.append(ENCODE[n and 0x3F])
            i += 3
        }
        when (end - i) {
            1 -> {
                val n = (bytes[i].toInt() and 0xFF) shl 16
                sb.append(ENCODE[(n ushr 18) and 0x3F])
                sb.append(ENCODE[(n ushr 12) and 0x3F])
                sb.append(PAD)
                sb.append(PAD)
            }
            2 -> {
                val n = ((bytes[i].toInt() and 0xFF) shl 16) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
                sb.append(ENCODE[(n ushr 18) and 0x3F])
                sb.append(ENCODE[(n ushr 12) and 0x3F])
                sb.append(ENCODE[(n ushr 6) and 0x3F])
                sb.append(PAD)
            }
        }
        return sb.toString()
    }

    /**
     * 解码标准 Base64(容忍无填充输入,与 RFC 4648 §3.2 一致);
     * 非法字符、填充位错误或长度 % 4 == 1 返回 null。
     */
    fun decode(s: String): ByteArray? {
        if (s.isEmpty()) return ByteArray(0)
        if (s.length % 4 == 1) return null
        var padCount = 0
        val chars = s.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            when {
                c == PAD -> {
                    padCount++
                    if (i < chars.size - 2) return null
                }
                padCount > 0 -> return null
                c.code >= DECODE.size || DECODE[c.code] < 0 -> return null
            }
        }
        if (padCount > 2) return null
        val fullGroups = chars.size / 4
        val tail = chars.size % 4
        val outLen = fullGroups * 3 + when (tail) {
            2 -> 1
            3 -> 2
            else -> 0
        } - padCount
        val out = ByteArray(outLen)
        var o = 0
        var i = 0
        while (i + 4 <= chars.size) {
            val a = value(chars[i])
            val b = value(chars[i + 1])
            if (a < 0 || b < 0) return null
            val n = (a shl 18) or (b shl 12)
            if (chars[i + 2] != PAD) {
                val c = value(chars[i + 2])
                if (c < 0) return null
                out[o++] = ((n or (c shl 6)) ushr 16).toByte()
                if (chars[i + 3] != PAD) {
                    val d = value(chars[i + 3])
                    if (d < 0) return null
                    out[o++] = ((n or (c shl 6) or d) ushr 8).toByte()
                    out[o++] = (n or (c shl 6) or d).toByte()
                } else {
                    out[o++] = ((n or (c shl 6)) ushr 8).toByte()
                }
            } else {
                out[o++] = (n ushr 16).toByte()
            }
            i += 4
        }
        if (tail == 2) {
            val a = value(chars[chars.size - 2])
            val b = value(chars[chars.size - 1])
            if (a < 0 || b < 0) return null
            out[o++] = (((a shl 18) or (b shl 12)) ushr 16).toByte()
        } else if (tail == 3) {
            val a = value(chars[chars.size - 3])
            val b = value(chars[chars.size - 2])
            val c = value(chars[chars.size - 1])
            if (a < 0 || b < 0 || c < 0) return null
            val n = (a shl 18) or (b shl 12) or (c shl 6)
            out[o++] = (n ushr 16).toByte()
            out[o++] = (n ushr 8).toByte()
        }
        return out
    }

    private fun value(c: Char): Int = if (c.code < DECODE.size) DECODE[c.code] else -1
}
