package com.lioisme.modev.core.util

import com.lioisme.modev.core.model.RecordLimits

/**
 * 字节数组的三态视图:hex 与 UTF-8 文本与 Base64(V19 起),均支持超限截断。
 * 设计为不可变值对象,可直接跨进程序列化。
 *
 * V15 契约变更:截断不再向 hex 内烧入 `…(+N bytes)` 硬编码英文后缀 ——
 * hex 恒为纯十六进制前缀(数据侧零本地化耦合),截断语义由
 * [truncated]/[totalLength] 承载,UI 层以资源渲染截断标记。
 *
 * V19 契约变更:新增 [base64](NO_WRAP、带填充标准编码)。Base64 只能由
 * 捕获侧原始字节生成并随 hex 一并持久化 —— hex 为截断前缀,运行时反推
 * Base64 会静默截断,禁止。旧记录(无 base64)渲染降级双态。
 */
data class BytesView(
    val hex: String,
    val base64: String? = null,
    val text: String?,
    val totalLength: Int,
    val truncated: Boolean,
) {
    companion object {
        /**
         * 将字节数组转为三态视图。
         *
         * @param maxHexChars hex 输出上限(字符数),超出截断并标记 truncated;
         *                    受 [HARD_MAX_HEX_CHARS] 硬保险丝约束,无论调用方
         *                    传值多大(含 0 = 无限配置),单字段 hex 永不超限物化
         * @param maxB64Chars Base64 输出上限(字符数),与 hex 同字节预算
         *                    (Base64 膨胀率 4/3 字符每字节,hex 为 2 → ×2/3);
         *                    截断标记共享 [truncated](按字节预算判定)
         * @param maxTextChars 文本输出上限;文本仅在全部字节可打印时生成,
         *                     同样受 [HARD_MAX_TEXT_CHARS] 约束
         */
        fun of(
            bytes: ByteArray?,
            maxHexChars: Int = DEFAULT_MAX_HEX_CHARS,
            maxB64Chars: Int = DEFAULT_MAX_B64_CHARS,
            maxTextChars: Int = DEFAULT_MAX_TEXT_CHARS,
        ): BytesView? {
            if (bytes == null || bytes.isEmpty()) return null
            val total = bytes.size
            val hexLen = maxHexChars.coerceAtLeast(32).coerceAtMost(HARD_MAX_HEX_CHARS)
            val truncated = total * 2 > hexLen
            val hex = if (truncated) Hex.encode(bytes, 0, hexLen / 2) else Hex.encode(bytes)
            val b64Len = maxB64Chars.coerceAtLeast(32).coerceAtMost(HARD_MAX_B64_CHARS)
            val b64 = if (total * 4 / 3 > b64Len) {
                Base64Util.encode(bytes, 0, (b64Len / 4) * 3)
            } else {
                Base64Util.encode(bytes)
            }
            val textLimit = maxTextChars.coerceAtMost(HARD_MAX_TEXT_CHARS)
            val text = if (total <= textLimit && TextUtil.isPrintable(bytes, maxChars = textLimit)) {
                String(bytes, Charsets.UTF_8)
            } else {
                null
            }
            return BytesView(
                hex = hex,
                base64 = b64,
                text = text,
                totalLength = total,
                truncated = truncated,
            )
        }

        /** 默认 hex 字符上限(见 [RecordLimits])。 */
        const val DEFAULT_MAX_HEX_CHARS = RecordLimits.DEFAULT_MAX_HEX_CHARS

        /**
         * 默认 Base64 字符上限:与 [DEFAULT_MAX_HEX_CHARS] 同字节预算
         * (Base64 膨胀率 4/3 字符每字节,hex 为 2 字符每字节 → ×2/3)。
         */
        const val DEFAULT_MAX_B64_CHARS = RecordLimits.DEFAULT_MAX_HEX_CHARS * 2 / 3

        /** 默认文本字节上限(见 [RecordLimits])。 */
        const val DEFAULT_MAX_TEXT_CHARS = RecordLimits.DEFAULT_MAX_TEXT_CHARS

        /**
         * 硬保险丝(V15,不可配置):无论调用方上限多大(含 0 = 无限),
         * 单字段 hex 字符数 / 文本字节数不超此值 —— 详情页解码与渲染永不
         * 因超大字段 OOM/ANR。
         */
        const val HARD_MAX_HEX_CHARS = 16 * 1024 * 1024
        const val HARD_MAX_TEXT_CHARS = 8 * 1024 * 1024

        /** Base64 硬保险丝:与 [HARD_MAX_HEX_CHARS] 同字节预算(×2/3)。 */
        const val HARD_MAX_B64_CHARS = HARD_MAX_HEX_CHARS * 2 / 3
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