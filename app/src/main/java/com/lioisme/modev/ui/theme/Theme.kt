package com.lioisme.modev.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme as platformIsDark
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

val LocalIsDarkTheme = compositionLocalOf { false }

val AppShapes =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
    )

@Composable
fun resolveIsDark(appearanceMode: AppearanceMode): Boolean =
    when (appearanceMode) {
        AppearanceMode.SYSTEM -> platformIsDark()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

@Composable
fun resolveColorScheme(
    config: ThemeConfig,
    isDark: Boolean,
): ColorScheme {
    val context = LocalContext.current
    return if (config.useWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // 壁纸模式走官方 dynamicColorScheme(系统全图量化调色板,覆盖全部 60+ token),
        // 由系统配置驱动,无需自建缓存。
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // 种子色模式:remember 缓存(重组不重复计算 tonal 色板)。
        val seed = config.customSeedColor
        remember(seed, isDark) { resolveColorSchemeNow(config, isDark, context) }
    }
}

/**
 * 非 Composable 色板计算(2026-08-15 UI 重构 Phase 1,对齐 LIDE):
 * 与 [resolveColorScheme] 同一事实源,供 MainActivity 在 setContent 之前同步
 * 窗口背景色(recreate 首帧底色对齐主题,消除切换语言/外观时系统重建的白/黑闪帧)。
 *
 * 取舍说明:本函数为非 Composable 路径,无法调用官方 dynamicColorScheme;
 * 壁纸模式用壁纸主色 + materialkolor 同算法生成近似色板——二者同一 TONAL_SPOT
 * 色系,recreate 空档帧差异肉眼不可见(仅动态壁纸模式,随后组合帧立即由
 * [resolveColorScheme] 官方色板接管);种子色模式两路径完全同源零色差。
 */
fun resolveColorSchemeNow(
    config: ThemeConfig,
    isDark: Boolean,
    context: Context?,
): ColorScheme =
    when {
        config.useWallpaper &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context != null -> {
            val seed = wallpaperPrimaryColor(context) ?: Color(config.customSeedColor)
            if (isDark) generateDarkScheme(seed) else generateLightScheme(seed)
        }

        else -> {
            val seed = Color(config.customSeedColor)
            if (isDark) generateDarkScheme(seed) else generateLightScheme(seed)
        }
    }

/** 壁纸主色(非 Composable,API 27+ `getWallpaperColors` 可用;更早恒 null 回落种子色)。 */
@Suppress("NewApi", "DEPRECATION")
private fun wallpaperPrimaryColor(context: Context): Color? =
    try {
        val manager = context.getSystemService(Context.WALLPAPER_SERVICE) as? android.app.WallpaperManager ?: return null
        val colors =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            } else {
                null
            }
        // getPrimaryColor 与 android.graphics.Color.toArgb 的 API 门槛(27/26)由
        // 外层 SDK_INT >= S 分支保证;lint 静态分析无法跟踪,显式 O_MR1 guard 消除。
        val primary =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) colors?.primaryColor else null
        primary?.let { Color(it.toArgb()) }
    } catch (_: Throwable) {
        // 降级:部分定制 ROM getWallpaperColors 抛异常 → 回落种子色。
        null
    }

@Composable
fun ModevTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit,
) {
    val isDark = resolveIsDark(config.appearanceMode)
    val colorScheme = resolveColorScheme(config, isDark)

    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Material3 官方 HCT 色板(materialkolor Kotlin 移植,与 LIDE 同源):
 * Google material-color-utilities 官方 TONAL_SPOT 动态色板(Compose
 * dynamicColorScheme 同款算法):任意 seed 生成的 token 对保证 Material3
 * 官方对比度规范。亮/暗各生成一份 [SchemeTonalSpot](tonal 色板相同、tone
 * 目标不同),映射到 Compose [ColorScheme] 全量 60+ token。
 */
internal fun generateLightScheme(primary: Color): ColorScheme = generateScheme(primary, isDark = false)

internal fun generateDarkScheme(primary: Color): ColorScheme = generateScheme(primary, isDark = true)

private fun generateScheme(
    primary: Color,
    isDark: Boolean,
): ColorScheme {
    val scheme =
        SchemeTonalSpot(
            sourceColorHct = Hct.fromInt(primary.toArgb()),
            isDark = isDark,
            contrastLevel = 0.0,
        )
    return if (isDark) {
        darkColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceTint = Color(scheme.surfaceTint),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        )
    } else {
        lightColorScheme(
            primary = Color(scheme.primary),
            onPrimary = Color(scheme.onPrimary),
            primaryContainer = Color(scheme.primaryContainer),
            onPrimaryContainer = Color(scheme.onPrimaryContainer),
            secondary = Color(scheme.secondary),
            onSecondary = Color(scheme.onSecondary),
            secondaryContainer = Color(scheme.secondaryContainer),
            onSecondaryContainer = Color(scheme.onSecondaryContainer),
            tertiary = Color(scheme.tertiary),
            onTertiary = Color(scheme.onTertiary),
            tertiaryContainer = Color(scheme.tertiaryContainer),
            onTertiaryContainer = Color(scheme.onTertiaryContainer),
            error = Color(scheme.error),
            onError = Color(scheme.onError),
            errorContainer = Color(scheme.errorContainer),
            onErrorContainer = Color(scheme.onErrorContainer),
            background = Color(scheme.background),
            onBackground = Color(scheme.onBackground),
            surface = Color(scheme.surface),
            onSurface = Color(scheme.onSurface),
            surfaceVariant = Color(scheme.surfaceVariant),
            onSurfaceVariant = Color(scheme.onSurfaceVariant),
            outline = Color(scheme.outline),
            outlineVariant = Color(scheme.outlineVariant),
            inverseSurface = Color(scheme.inverseSurface),
            inverseOnSurface = Color(scheme.inverseOnSurface),
            inversePrimary = Color(scheme.inversePrimary),
            surfaceTint = Color(scheme.surfaceTint),
            surfaceDim = Color(scheme.surfaceDim),
            surfaceBright = Color(scheme.surfaceBright),
            surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
            surfaceContainerLow = Color(scheme.surfaceContainerLow),
            surfaceContainer = Color(scheme.surfaceContainer),
            surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
            surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        )
    }
}
