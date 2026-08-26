package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.BehaviorKind

/*
 * 行为类别色 token(V27):与算法/SQL 色板同规——浅/深两套,基于 Material
 * 扩展色板选取,保证类别色文本与淡底徽章在对应表面上的对比度 ≥ AA(≥4.5:1)。
 * 用法:behaviorKindColor(kind) 经 LocalIsDarkTheme 自动取浅/深;
 * 徽章底 = 语义色 @13% alpha(V27 RecordRow/微徽章统一透明度)。
 */

/** 浅色模式行为类别色。 */
private val behaviorLightPalette: Map<BehaviorKind, Color> =
    mapOf(
        BehaviorKind.FILE_READ to Color(0xFF4A4400),
        BehaviorKind.FILE_WRITE to Color(0xFF256A1F),
        BehaviorKind.FILE_DELETE to Color(0xFF9C4230),
        BehaviorKind.FILE_RENAME to Color(0xFF00687A),
        BehaviorKind.FILE_LIST to Color(0xFF49589E),
        BehaviorKind.ASSET_READ to Color(0xFF6B4E00),
        BehaviorKind.SHELL_EXEC to Color(0xFF8B3A62),
    )

/** 深色模式行为类别色。 */
private val behaviorDarkPalette: Map<BehaviorKind, Color> =
    mapOf(
        BehaviorKind.FILE_READ to Color(0xFFC9C258),
        BehaviorKind.FILE_WRITE to Color(0xFFA2D58F),
        BehaviorKind.FILE_DELETE to Color(0xFFFFB5A3),
        BehaviorKind.FILE_RENAME to Color(0xFF86D2E5),
        BehaviorKind.FILE_LIST to Color(0xFFBCC2FF),
        BehaviorKind.ASSET_READ to Color(0xFFE5C36D),
        BehaviorKind.SHELL_EXEC to Color(0xFFFFAFD2),
    )

/** 行为类别语义色(非 Composable 访问器,UI 与对比度门禁测试同源)。 */
internal fun behaviorKindColorForMode(kind: BehaviorKind, isDark: Boolean): Color =
    if (isDark) behaviorDarkPalette.getValue(kind) else behaviorLightPalette.getValue(kind)

/** 行为类别语义色(随外观模式自动切换)。 */
@Composable
fun behaviorKindColor(kind: BehaviorKind): Color =
    behaviorKindColorForMode(kind, LocalIsDarkTheme.current)
