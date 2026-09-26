package com.lioisme.modev.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.utils.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置界面配色契约门禁(V21 T1 数据驱动定档,E1/R1 修订,与 LIDE 同款
 * materialkolor SchemeTonalSpot 算法):
 *
 * 设置页配色契约:
 * - 页面背景 = `surface`(Scaffold 显式 containerColor,MainActivity 窗口同源);
 * - 分区标题 = `primary`(titleSmall + SemiBold);
 * - 内容卡 = `surfaceContainerLow`(V21 最终决策,与 LIDE SettingsSection 逐字一致;
 *   T1 曾升 surfaceContainerHighest 应对雷电 ListItem 渲染缺陷,经 E1 探针定位根因 =
 *   ListItem 内部 Surface 默认 Unspecified 容器色嵌套卡内触发雷电转译 bug,
 *   显式 ListItemDefaults.colors(containerColor=…) 修复后回退 Low,R1 重开记录见 docs);
 * - 行标题 = `onSurface` / 副标题+图标 = `onSurfaceVariant`;
 * - 弹窗行(ListItem)= `surfaceContainerHigh`(sheet 容器同源)。
 *
 * 本门禁对 [ThemeSeeds.OPTIONS] 全 20 seed × 亮/暗生成生产同源色板
 * ([generateLightScheme]/[generateDarkScheme]),断言:
 * 1. 卡片可辨识:surfaceContainerLow vs surface 亮 ≥ 1.046:1、
 *    暗 ≥ 1.078:1(2026-08-20 实测全 seed 区间:亮 1.046~1.085 /
 *    暗 1.078~1.15;对比度偏低属 M3 Low 家族固有,靠圆角/分隔/内边距
 *    与 LIDE 同款视觉线索区分,雷电实测可辨);
 * 2. 行正文:onSurface vs surfaceContainerLow ≥ 4.5:1(AA 严格档);
 * 3. 分区标题:primary vs surface ≥ 4.5:1(AA 严格档)。
 */
class SettingsThemeContrastTest {

    private fun contrastRatio(
        fg: Color,
        bg: Color,
    ): Double {
        val l1 = ColorUtils.calculateLuminance(fg.toArgb())
        val l2 = ColorUtils.calculateLuminance(bg.toArgb())
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 卡片容器(surfaceContainerLow)vs 页面背景(surface)的最弱对比。 */
    private fun cardVsSurfaceRatio(isDark: Boolean): Double {
        var min = Double.MAX_VALUE
        for (seed in ThemeSeeds.OPTIONS) {
            val scheme = if (isDark) generateDarkScheme(Color(seed)) else generateLightScheme(Color(seed))
            min = minOf(min, contrastRatio(scheme.surfaceContainerLow, scheme.surface))
        }
        return min
    }

    @Test
    fun cardDistinguishableFromBackground_forAllSeeds() {
        val lightMin = cardVsSurfaceRatio(isDark = false)
        val darkMin = cardVsSurfaceRatio(isDark = true)
        assertTrue("亮色卡片/背景最小对比 $lightMin 低于 1.046:1(LIDE 同款下限)", lightMin >= 1.046)
        assertTrue("暗色卡片/背景最小对比 $darkMin 低于 1.078:1(LIDE 同款下限)", darkMin >= 1.078)
    }

    @Test
    fun onSurfaceMeetsAaOnCard_forAllSeeds() {
        for (seed in ThemeSeeds.OPTIONS) {
            for (isDark in listOf(false, true)) {
                val scheme = if (isDark) generateDarkScheme(Color(seed)) else generateLightScheme(Color(seed))
                val ratio = contrastRatio(scheme.onSurface, scheme.surfaceContainerLow)
                assertTrue(
                    "seed=#%06X dark=$isDark onSurface on 卡片 ratio=$ratio 低于 AA 严格档 4.5:1".format(seed and 0xFFFFFF),
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun primaryMeetsAaOnSurface_forAllSeeds() {
        for (seed in ThemeSeeds.OPTIONS) {
            for (isDark in listOf(false, true)) {
                val scheme = if (isDark) generateDarkScheme(Color(seed)) else generateLightScheme(Color(seed))
                val ratio = contrastRatio(scheme.primary, scheme.surface)
                assertTrue(
                    "seed=#%06X dark=$isDark primary on surface ratio=$ratio 低于 AA 严格档 4.5:1".format(seed and 0xFFFFFF),
                    ratio >= 4.5,
                )
            }
        }
    }
}
