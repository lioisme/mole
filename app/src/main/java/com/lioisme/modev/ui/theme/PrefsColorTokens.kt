package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.PrefsKind

/*
 * 偏好类别色 token(V28):与算法/SQL/行为色板同规——浅/深两套,基于 Material
 * 扩展色板选取,保证类别色文本与淡底徽章在对应表面上的对比度 ≥ AA(≥4.5:1)。
 * 用法:prefsKindColor(kind) 经 LocalIsDarkTheme 自动取浅/深;
 * 徽章底 = 语义色 @13% alpha(V27 RecordRow 统一透明度)。
 */

/** 浅色模式偏好类别色。 */
private val prefsLightPalette: Map<PrefsKind, Color> =
    mapOf(
        PrefsKind.OPEN to Color(0xFF55579B),
        PrefsKind.GET to Color(0xFF00696D),
        PrefsKind.PUT to Color(0xFF8A4A62),
        PrefsKind.REMOVE to Color(0xFF7A4E00),
        PrefsKind.CLEAR to Color(0xFF5B5E00),
    )

/** 深色模式偏好类别色。 */
private val prefsDarkPalette: Map<PrefsKind, Color> =
    mapOf(
        PrefsKind.OPEN to Color(0xFFBEC2FF),
        PrefsKind.GET to Color(0xFF9CF1EA),
        PrefsKind.PUT to Color(0xFFFFB0C8),
        PrefsKind.REMOVE to Color(0xFFE5C36D),
        PrefsKind.CLEAR to Color(0xFFE1C344),
    )

/** 偏好类别语义色(非 Composable 访问器,UI 与对比度门禁测试同源)。 */
internal fun prefsKindColorForMode(kind: PrefsKind, isDark: Boolean): Color =
    if (isDark) prefsDarkPalette.getValue(kind) else prefsLightPalette.getValue(kind)

/** 偏好类别语义色(随外观模式自动切换)。 */
@Composable
fun prefsKindColor(kind: PrefsKind): Color =
    prefsKindColorForMode(kind, LocalIsDarkTheme.current)
