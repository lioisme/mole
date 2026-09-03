package com.lioisme.modev.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 16dp 水平内边距分割线(Card 内行间分隔标准):
 * SettingsSection 内 ListItem 之间、AppMonitorScreen 入口行之间等场景共用。
 */
@Composable
fun HorizontalDivider16(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(horizontal = 16.dp))
}
