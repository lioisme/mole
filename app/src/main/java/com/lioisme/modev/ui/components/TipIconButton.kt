package com.lioisme.modev.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 带长按提示(tooltip)的图标按钮(2026-08-15 UI 重构 Phase 2,移植自 LIDE)。
 * 长按按钮即显示 [tooltip] 文本,并触发一次原生长按振动
 * (HapticFeedbackType.LongPress → 系统 LONG_PRESS,非自定义振动事件;
 * M3 1.4.0 TooltipBox 自身不执行 haptic,此处补齐)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val tooltipState = rememberTooltipState()
    // 长按展示 tooltip 的同时执行原生振动(isVisible 仅由长按置 true,
    // 点击收起/超时隐藏走 false 分支,不触发振动——触发面恒等于「tooltip
    // 出现」这一事件,每次长按至多一次,无重复过振)。
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    TooltipBox(
        // 1.4.0 新 API:显式锚点方向(Above = 旧 plain 行为),替代已弃用的
        // rememberPlainTooltipPositionProvider / 无参 rememberTooltipPositionProvider。
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
            ),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            content()
        }
    }
}
