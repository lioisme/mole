package com.lioisme.modev.ui.components

import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * 中缀省略:路径/命令等「保头掐腰」文本的单一事实源。
 *
 * 纯函数层 [MiddleEllipsis.ellipsize] 字符预算截断(JVM 直测,确定性);
 * 组合层 [MiddleEllipsisText] 在单行溢出时经 `TextUtils.ellipsize(MIDDLE)`
 * 按像素宽度二次收敛(字体度量近似,留 2% 余量),一次截断后不再回摆,
 * 容器变宽时恢复原文由下一帧重新判定(旋转/分栏自适应)。
 */
object MiddleEllipsis {

    /** 省略号字符(与平台 TruncateAt.MIDDLE 输出一致)。 */
    const val ELLIPSIS = "…"

    /**
     * 字符预算中缀省略(纯函数):
     * - maxChars ≤ 0 → 空串;预算不足以容纳「头+尾」各至少 1 字符(≤2)→ 仅省略号;
     * - 文本长度 ≤ maxChars 原样返回;
     * - 否则保留 head = ⌈keep/2⌉ + tail = ⌊keep/2⌋(keep = maxChars-1),中间替换为省略号,
     *   结果长度恒等于 maxChars。
     */
    fun ellipsize(
        text: String,
        maxChars: Int,
    ): String {
        if (maxChars <= 0) return ""
        if (text.length <= maxChars) return text
        val keep = maxChars - ELLIPSIS.length
        if (keep < 2) return ELLIPSIS
        val head = (keep + 1) / 2
        val tail = keep / 2
        return text.take(head) + ELLIPSIS + text.takeLast(tail)
    }
}

/**
 * 中缀省略文本(路径/命令专用;普通标题用原生尾部省略即可):
 * 单行、不换行;溢出时按容器像素宽做 MIDDLE 截断,极端窄容器下由
 * [MiddleEllipsis.ellipsize] 字符预算护栏兜底。
 */
@Composable
fun MiddleEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    var display by remember(text) { mutableStateOf(text) }
    val density = LocalDensity.current
    val resolvedColor =
        if (color == Color.Unspecified) {
            MaterialTheme.colorScheme.onSurface
        } else {
            color
        }
    Text(
        text = display,
        modifier = modifier,
        color = resolvedColor,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { result ->
            if (!result.hasVisualOverflow) {
                // 容器变宽:还原原文,下一帧按需重截(收敛于两帧)。
                if (display != text) display = text
                return@Text
            }
            // 已处于截断态仍溢出(度量残差):接受 Clip 兜底,防回摆循环。
            if (display != text) return@Text
            val availablePx = result.layoutInput.constraints.maxWidth.toFloat()
            if (availablePx <= 0f) return@Text
            val paint =
                TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = with(density) { style.fontSize.toPx() }
                    typeface = typefaceFor(style.fontFamily)
                    isFakeBoldText = (style.fontWeight ?: FontWeight.Normal).weight >= 600
                }
            val budgetPx = availablePx * MEASURE_MARGIN
            val pixelResult =
                TextUtils.ellipsize(text, paint, budgetPx, TextUtils.TruncateAt.MIDDLE).toString()
            // 像素度量残差护栏:若截断后仍可能溢出(结果未缩短到安全长度),
            // 回退字符预算硬钳制。
            display =
                if (pixelResult.length < text.length || MEASURE_MARGIN >= 1f) {
                    pixelResult
                } else {
                    MiddleEllipsis.ellipsize(text, MIN_CHAR_BUDGET)
                }
        },
    )
}

private fun typefaceFor(family: FontFamily?): android.graphics.Typeface =
    when (family) {
        FontFamily.Monospace -> android.graphics.Typeface.MONOSPACE
        FontFamily.Serif -> android.graphics.Typeface.SERIF
        FontFamily.SansSerif -> android.graphics.Typeface.SANS_SERIF
        else -> android.graphics.Typeface.DEFAULT
    }

private const val MEASURE_MARGIN = 0.98f

private const val MIN_CHAR_BUDGET = 8
