package com.lioisme.modev.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.utils.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 状态色对比度门禁(2026-08-18 V14 R4,仿 KindColorTokensTest 同款算法):
 * 6 语义色在浅/深两模式下,承载背景 = M3 scheme surface(浅 ≈ #FFFBFE、
 * 深 ≈ #1C1B1F),断言全部满足 WCAG 2.x 相对亮度 AA 严格档 ≥ 4.5:1:
 * - ONLINE/OFFLINE/CONNECTING/UNSCOPED:自定义 token(statusColorForMode);
 * - SCOPED/ERROR:派生自 M3 方案 primary/error(测试以 [SchemeTonalSpot]
 *   同款算法构造,与 Theme.kt generateScheme 同源校验)。
 * seed 单一事实源 = [ThemeConfig].customSeedColor,与 Theme.kt 同源。
 */
class StatusColorTokensTest {
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

    private fun assertAa(
        label: String,
        fg: Int,
        bg: Int,
    ) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label ratio=$ratio 低于 AA 严格档 4.5:1", ratio >= 4.5)
    }

    @Test
    fun customTokensMeetAaOnSurfaceForBothModes() {
        for (kind in listOf(StatusKind.ONLINE, StatusKind.OFFLINE, StatusKind.CONNECTING, StatusKind.UNSCOPED)) {
            for (isDark in listOf(false, true)) {
                val surface = schemeFor(isDark).surface
                val fg = statusColorForMode(kind, isDark).toArgb()
                assertAa("status=$kind dark=$isDark", fg, surface)
            }
        }
    }

    @Test
    fun schemeDerivedKindsMeetAaForBothModes() {
        // SCOPED/ERROR 生产取值 = M3 primary/error(StatusColorTokens 的
        // [statusColor] 派生规则),对比度由 Material3 官方规范保证;
        // 测试以 [SchemeTonalSpot] 同款算法(Theme.kt generateScheme 映射同源)校验。
        for (isDark in listOf(false, true)) {
            val scheme = schemeFor(isDark)
            val surface = scheme.surface
            assertAa("SCOPED dark=$isDark", scheme.primary, surface)
            assertAa("ERROR dark=$isDark", scheme.error, surface)
        }
    }
}
