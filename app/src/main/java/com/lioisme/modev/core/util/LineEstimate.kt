package com.lioisme.modev.core.util

/**
 * 行数阈值折叠估算(2026-08-17 V11 新增,纯函数可单测):
 * 详情页字节区块「只有超出行数的才默认收起」的判定与预览截断依据。
 * 每行字符数按容器宽度与字号估算(sp 与 dp 在 fontScale=1 下数值等价,
 * 阈值是近似语义,不引入密度换算);等宽 hex 取 0.6em、常规文本取 0.5em 保守字距。
 */
object LineEstimate {

    /** 折叠判定行数阈值:超过该行数默认收起,否则默认展开全量展示。 */
    const val COLLAPSED_MAX_LINES = 8

    /** 每行字符数下限兜底(防止极端宽屏/字号下阈值过小产生空预览)。 */
    const val MIN_CHARS_PER_LINE = 16

    /** 等宽字体字距系数(advance ≈ 0.6em)。 */
    private const val MONO_ADVANCE = 0.6f

    /** 常规字体字距系数(中文/拉丁混合,保守取值)。 */
    private const val TEXT_ADVANCE = 0.5f

    /** 估算容器内单行可容纳字符数(floor + 下限兜底)。 */
    fun charsPerLine(containerWidthDp: Float, fontSizeSp: Float, monospace: Boolean): Int {
        val advance = if (monospace) MONO_ADVANCE else TEXT_ADVANCE
        return (containerWidthDp / (fontSizeSp * advance)).toInt().coerceAtLeast(MIN_CHARS_PER_LINE)
    }

    /** 是否超出行数阈值(hex 长度驱动;text ≤ bytes ≤ hex/2,hex 超限即折叠)。 */
    fun needsFold(hexLength: Int, charsPerLine: Int): Boolean =
        hexLength > charsPerLine * COLLAPSED_MAX_LINES

    /** 折叠态预览字符上限(= 阈值行数对应字符数,永不超全量)。 */
    fun collapsedPreviewChars(hexLength: Int, charsPerLine: Int): Int =
        minOf(hexLength, charsPerLine * COLLAPSED_MAX_LINES)
}
