package com.lioisme.modev.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFF9CF2EA),
    secondary = Color(0xFFB0CCF0),
    onSecondary = Color(0xFF1A344C),
    secondaryContainer = Color(0xFF324A64),
    onSecondaryContainer = Color(0xFFD2E4FF),
    tertiary = Color(0xFFB8C8E8),
    background = Color(0xFF0B1929),
    onBackground = Color(0xFFDCE5ED),
    surface = Color(0xFF0B1929),
    onSurface = Color(0xFFDCE5ED),
    surfaceVariant = Color(0xFF1B2A3A),
    onSurfaceVariant = Color(0xFFB9C6D4),
    outline = Color(0xFF83909E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006962),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF2EA),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4B637C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E4FF),
    onSecondaryContainer = Color(0xFF041E35),
    tertiary = Color(0xFF57637F),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDCE4EA),
    onSurfaceVariant = Color(0xFF40484E),
    outline = Color(0xFF70787F),
)

/**
 * MD3 主题:Android 12+ 使用动态取色,其余使用固定的深/浅色方案(默认深色,与模块的
 * 捕获工具定位一致)。
 */
@Composable
fun ModevTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}