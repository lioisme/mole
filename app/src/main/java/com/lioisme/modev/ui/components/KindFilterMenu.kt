package com.lioisme.modev.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.lioisme.modev.R

/**
 * 类别单选菜单(V19 共享组件;V23 增补 [enabled] 联动置灰):
 * 固定记录类型的单一类别组:「全部类别」+ 类别列表(单选,只读 RadioButton)。
 * M3 DropdownMenuItem 整项 MergeDescendants 合并文本与 OnClick;只读
 * RadioButton 不暴露 ToggleableState —— 勾选态断言不可行,测试走行为级
 * (点击项 → 行现/行隐,AGENTS 契约 8)。
 *
 * @param active  有激活条件时筛选按钮 primary 着色(列表被过滤的感知指示)
 * @param enabled false = 入口禁用(全局页「全部类型」下类别不可用,D6)
 */
data class KindOption(
    val name: String,
    @StringRes val labelRes: Int,
)

@Composable
fun KindFilterMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedKindName: String?,
    onKindSelect: (String?) -> Unit,
    kinds: List<KindOption>,
    active: Boolean = selectedKindName != null,
    enabled: Boolean = true,
) {
    Box {
        TipIconButton(
            tooltip = stringResource(R.string.cd_filter),
            onClick = { onExpandedChange(true) },
            enabled = enabled,
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = stringResource(R.string.cd_filter),
                tint =
                    when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.record_types_all), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { RadioButton(selected = selectedKindName == null, onClick = null) },
                onClick = {
                    onKindSelect(null)
                    onExpandedChange(false)
                },
            )
            kinds.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { RadioButton(selected = selectedKindName == option.name, onClick = null) },
                    onClick = {
                        onKindSelect(option.name)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}