package com.lioisme.modev.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.lioisme.modev.core.model.CryptoKind
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.utils.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 类别色对比度门禁(2026-08-16 LIDE 风格对齐配套,镜像 LIDE ThemeContrastTest 同款算法)。
 *
 * 生产实现 [KindColorTokens] 为纯计算常量,LIDE 基准 [Theme.kt] 的
 * [SchemeTonalSpot](Google material-color-utilities Kotlin 移植,Compose
 * dynamicColorScheme 同款算法)确定性无平台差异,测试直接以同款调用构造
 * scheme,断言全部「类别色承载」对满足 WCAG 2.x 相对亮度 AA 严格档 ≥ 4.5:1:
 * - 类别字母 vs 主题 surface(记录行 / 空态等背景);
 * - 类别字母 vs 15% 淡底合成色(RecordRow 徽章,KIND_BADGE_ALPHA 同源)。
 *
 * seed 单一事实源 = [ThemeConfig].customSeedColor(0xFF1976D2),与 Theme.kt 同源。
 */
class KindColorTokensTest {
    private val seed = ThemeConfig().customSeedColor

    private fun schemeFor(isDark: Boolean): SchemeTonalSpot =
        SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(seed.toInt()),
            isDark = isDark,
            contrastLevel = 0.0,
        )

    private fun contrastRatio(
        fg: Int,
        bg: Int,
    ): Double {
        val l1 = ColorUtils.calculateLuminance(fg)
        val l2 = ColorUtils.calculateLuminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 类别色 15% alpha 叠 surface(与 RecordRow 徽章渲染同式)。 */
    private fun blendAlpha(
        fg: Int,
        bg: Int,
        alpha: Float,
    ): Int {
        fun ch(f: Int, b: Int): Int = (f * alpha + b * (1 - alpha)).toInt()
        return (ch(fg shr 24 and 0xFF, bg shr 24 and 0xFF) shl 24) or
            (ch(fg shr 16 and 0xFF, bg shr 16 and 0xFF) shl 16) or
            (ch(fg shr 8 and 0xFF, bg shr 8 and 0xFF) shl 8) or
            ch(fg and 0xFF, bg and 0xFF)
    }

    @Test
    fun kindTextMeetsAaOnSurfaceForBothModes() {
        for (kind in CryptoKind.entries) {
            for (isDark in listOf(false, true)) {
                val surface = schemeFor(isDark).surface
                val fg = kindColorForMode(kind, isDark).toArgb()
                val ratio = contrastRatio(fg, surface)
                assertTrue(
                    "kind=$kind dark=$isDark ratio=$ratio 低于 AA 严格档 4.5:1",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun kindBadgeLetterMeetsAaOnTintedBadgeForBothModes() {
        for (kind in CryptoKind.entries) {
            for (isDark in listOf(false, true)) {
                val s = schemeFor(isDark)
                val surface = s.surface
                val fg = kindColorForMode(kind, isDark).toArgb()
                val badgeBg = blendAlpha(fg, surface, 0.15f)
                val ratio = contrastRatio(fg, badgeBg)
                assertTrue(
                    "badge kind=$kind dark=$isDark ratio=$ratio 低于 AA 严格档 4.5:1",
                    ratio >= 4.5,
                )
            }
        }
    }
}