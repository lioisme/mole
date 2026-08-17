package com.lioisme.modev.ui.theme

/** 外观模式:跟随系统 / 强制浅色 / 强制深色。 */
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 主题色预设单一事实源(2026-08-15 UI 重构 Phase 1,对齐 LIDE):
 * SettingsScreen 色板与持久化共用,新增/调整预设只改这一处。
 */
object ThemeSeeds {
    /** Material 官方经典 seed 色板(含黑/白灰阶,HCT 对任意 seed 生成合法 scheme)。 */
    val OPTIONS: List<Long> =
        listOf(
            0xFFF44336,
            0xFFE91E63,
            0xFF9C27B0,
            0xFF673AB7,
            0xFF3F51B5,
            0xFF2196F3,
            0xFF03A9F4,
            0xFF00BCD4,
            0xFF009688,
            0xFF4CAF50,
            0xFF8BC34A,
            0xFFCDDC39,
            0xFFFFEB3B,
            0xFFFFC107,
            0xFFFF9800,
            0xFFFF5722,
            0xFF795548,
            0xFF607D8B,
            0xFF9E9E9E,
            0xFF000000,
        )
}

data class ThemeConfig(
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val useWallpaper: Boolean = true,
    val customSeedColor: Long = 0xFF1976D2,
)
