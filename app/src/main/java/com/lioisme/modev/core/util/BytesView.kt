package com.lioisme.modev.core.util

/**
 * 字节数组的双态视图:hex 与 UTF-8 文本,均支持超限截断。
 * 设计为不可变值对象,可直接跨进程序列化。
 */
data class BytesView(
    val hex: String,
    val text: String?,
    val totalLength: Int,
    val truncated: Boolean,
) {
    companion object {
        /**
         * 将字节数组转为双态视图。
         *
         * @param maxHexChars hex 输出上限(字符数),超出截断并标记 truncated
         * @param maxTextChars 文本输出上限;文本仅在全部字节可打印时生成
         */
        fun of(bytes: ByteArray?, maxHexChars: Int = 1024, maxTextChars: Int = 512): BytesView? {
            if (bytes == null || bytes.isEmpty()) return null
            val total = bytes.size
            val hexLen = maxHexChars.coerceAtLeast(32)
            val truncated = total * 2 > hexLen
            val hex = if (truncated) {
                Hex.encode(bytes, 0, hexLen / 2) + "…(+${total - hexLen / 2} bytes)"
            } else {
                Hex.encode(bytes)
            }
            val text = if (total <= maxTextChars && TextUtil.isPrintable(bytes)) {
                String(bytes, Charsets.UTF_8)
            } else {
                null
            }
            return BytesView(hex = hex, text = text, totalLength = total, truncated = truncated)
        }
    }
}

/**
 * 文本可打印性判定与工具。
 */
object TextUtil {
    private val MAX_CHARS = 4096

    /** 全部字节均为可打印 UTF-8 序列(允许 \n \r \t 与常见符号,不含控制字符)。 */
    fun isPrintable(bytes: ByteArray, maxChars: Int = MAX_CHARS): Boolean {
        if (bytes.size > maxChars * 4) return false
        var i = 0
        var chars = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == 0x09 || b == 0x0A || b == 0x0D -> { chars++; i++ }
                b in 0x20..0x7E -> { chars++; i++ }
                b in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size) return false
                    val c = bytes[i + 1].toInt() and 0xFF
                    if (c !in 0x80..0xBF) return false
                    chars++; i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return false
                    val c1 = bytes[i + 1].toInt() and 0xFF
                    val c2 = bytes[i + 2].toInt() and 0xFF
                    if (c1 !in 0x80..0xBF || c2 !in 0x80..0xBF) return false
                    if (b == 0xE0 && c1 < 0xA0) return false
                    if (b == 0xED && c1 > 0x9F) return false
                    chars++; i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= bytes.size) return false
                    val c1 = bytes[i + 1].toInt() and 0xFF
                    val c2 = bytes[i + 2].toInt() and 0xFF
                    val c3 = bytes[i + 3].toInt() and 0xFF
                    if (c1 !in 0x80..0xBF || c2 !in 0x80..0xBF || c3 !in 0x80..0xBF) return false
                    if (b == 0xF0 && c1 < 0x90) return false
                    if (b == 0xF4 && c1 > 0x8F) return false
                    chars++; i += 4
                }
                else -> return false
            }
        }
        return chars > 0
    }
}