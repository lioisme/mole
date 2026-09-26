package com.lioisme.modev.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * 状态语义色 token(2026-08-18 V14,状态角标化):
 * - ONLINE/OFFLINE/CONNECTING/UNSCOPED 为自定义语义色(浅/深两套),取自
 *   Material 扩展色板,保证在对应表面(浅色 surface≈#FFFBFE、
 *   深色 surface≈#1C1B1F)上的对比度 ≥ AA(4.5:1),由 StatusColorTokensTest
 *   门禁校验;UNSCOPED 用独立灰阶(与 OFFLINE 区分,outline token 仅 4.27:1
 *   不达严格档,故不派生);
 * - SCOPED/ERROR 派生 M3 颜色方案(primary/error),跟随主题种子色与深色模式,
 *   对比度由 Material3 官方规范保证(测试同源校验)。
 * 用法:statusColor(kind) 经 LocalIsDarkTheme / MaterialTheme 自动取对应值;
 * 仅作装饰点/图标色,完整语义由 contentDescription / TooltipBox 承载。
 */

/** 状态类别(角标语义 + 色彩取值的单一事实源)。 */
enum class StatusKind { ONLINE, OFFLINE, CONNECTING, ERROR, SCOPED, UNSCOPED }

/** 浅色模式自定义状态色(前景为浅色 surface 上的深色文本,对比度 ≥ 4.5:1)。 */
private val lightStatusPalette: Map<StatusKind, Color> =
    mapOf(
        StatusKind.ONLINE to Color(0xFF137333),
        StatusKind.OFFLINE to Color(0xFF5F6368),
        StatusKind.CONNECTING to Color(0xFFB45309),
        StatusKind.UNSCOPED to Color(0xFF6E6E6E),
    )

/** 深色模式自定义状态色(前景为深色 surface 上的浅色文本,对比度 ≥ 4.5:1)。 */
private val darkStatusPalette: Map<StatusKind, Color> =
    mapOf(
        StatusKind.ONLINE to Color(0xFF81C995),
        StatusKind.OFFLINE to Color(0xFF9AA0A6),
        StatusKind.CONNECTING to Color(0xFFFFD666),
        StatusKind.UNSCOPED to Color(0xFFC4C7C5),
    )

/** 自定义状态色(非 Composable 访问器,UI 与对比度门禁测试同源)。 */
internal fun statusColorForMode(kind: StatusKind, isDark: Boolean): Color =
    if (isDark) darkStatusPalette.getValue(kind) else lightStatusPalette.getValue(kind)

/** 状态语义色(SCOPED/ERROR 派生 M3 方案,随主题与外观模式自动切换)。 */
@Composable
fun statusColor(kind: StatusKind): Color = when (kind) {
    StatusKind.SCOPED -> MaterialTheme.colorScheme.primary
    StatusKind.ERROR -> MaterialTheme.colorScheme.error
    else -> statusColorForMode(kind, LocalIsDarkTheme.current)
}
