package com.lioisme.modev.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.ModevNavHost
import com.lioisme.modev.ui.theme.AppearanceMode
import com.lioisme.modev.ui.theme.LocalIsDarkTheme
import com.lioisme.modev.ui.theme.ModevTheme
import com.lioisme.modev.ui.theme.ThemeSettingsManager
import com.lioisme.modev.ui.theme.resolveColorSchemeNow

/**
 * 宿主 App 唯一 Activity:承载单栈导航与各页面(2026-08-15 UI 重构,对齐 LIDE)。
 */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        // 语言包装(<33 生效路径;33+ 由系统 per-app locale 提供资源)
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // 系统栏图标明暗按应用外观模式显式指定而非系统深浅色——应用强制
            // LIGHT/DARK 时 auto 模式会按系统 uiMode 给出相反图标色(浅底深色
            // 图标不可见)。外观模式切换不重建 Activity,setContent 内
            // LaunchedEffect(isDark) 再同步一次;scrim 均透明(纯 edge-to-edge)。
            statusBarStyle = systemBarStyleFor(currentIsDark()),
            navigationBarStyle = systemBarStyleFor(currentIsDark()),
        )

        applyWindowBackground()

        setContent {
            val config by ThemeSettingsManager.themeConfig.collectAsState()
            ModevTheme(config = config) {
                val isDarkTheme = LocalIsDarkTheme.current
                // 兜底:外观模式在应用内切换(设置页)不重建 Activity,
                // 此效应在 isDark 变化时同步系统栏图标明暗(幂等,重建后亦执行一次)。
                LaunchedEffect(isDarkTheme) {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !isDarkTheme
                    controller.isAppearanceLightNavigationBars = !isDarkTheme
                }
                // 语言热切换(V9):语言变更 = 更新 localeFlow → 此处以包装 Context
                // 覆写 LocalContext,全站 stringResource 即时重组,零 Activity 重建。
                val localeTag by LocaleManager.localeFlow.collectAsState()
                val localizedContext = remember(localeTag) { LocaleManager.wrap(this) }
                CompositionLocalProvider(LocalContext provides localizedContext) {
                    val navController = rememberNavController()
                    ModevNavHost(navController = navController)
                }
            }
        }
    }

    /**
     * 当前明暗(与 Compose 侧 [ModevTheme] 判定同源):SYSTEM 跟随系统 uiMode,
     * LIGHT/DARK 强制。MainActivity 非 Composable 上下文用,供窗口背景与
     * 系统栏样式计算。
     */
    private fun currentIsDark(): Boolean =
        when (ThemeSettingsManager.themeConfig.value.appearanceMode) {
            AppearanceMode.SYSTEM -> {
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }

            AppearanceMode.LIGHT -> {
                false
            }

            AppearanceMode.DARK -> {
                true
            }
        }

    /**
     * 窗口背景对齐主题 surface 色(HCT 同源计算,见 Theme.kt resolveColorSchemeNow)。
     * Activity 重建(切换语言 per-app locale / 外观模式)期间,窗口正文区域以本
     * 背景绘制,不闪默认白/黑帧;随后 Compose 内容同色接管。
     */
    private fun applyWindowBackground() {
        try {
            val config = ThemeSettingsManager.themeConfig.value
            val scheme = resolveColorSchemeNow(config, currentIsDark(), this)
            window.setBackgroundDrawable(scheme.surface.toArgb().toDrawable())
        } catch (_: Exception) {
            // 降级:背景设置失败保持系统默认(无害,仅 recreate 帧底色差异)。
        }
    }

    /** 系统栏样式映射(透明 scrim;浅色 = 深色图标,深色 = 浅色图标)。 */
    private fun systemBarStyleFor(isDark: Boolean): SystemBarStyle =
        if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
}
