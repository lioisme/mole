package com.lioisme.modev.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lioisme.modev.ui.theme.StatusKind
import com.lioisme.modev.ui.theme.statusColor

/**
 * 状态角标(2026-08-18 V14,状态角标化):
 * 8dp 语义色圆点,不渲染文本;完整状态语义经 [label] 进 contentDescription(无障碍),
 * 长按弹出 [label] tooltip(复用 TipIconButton 同款 M3 1.4.0 显式锚点 API)。
 * 色彩取值见 StatusColorTokens(对比度 ≥ AA 门禁测试同源)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDot(
    kind: StatusKind,
    label: String,
    modifier: Modifier = Modifier,
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
            ),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color = statusColor(kind), shape = CircleShape)
                    .semantics { contentDescription = label },
        )
    }
}