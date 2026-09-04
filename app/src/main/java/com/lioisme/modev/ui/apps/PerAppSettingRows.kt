package com.lioisme.modev.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.ui.components.SettingsToggleItem

/**
 * 每应用设置行组件(V25 自 ui/settings/SettingsRowItems 迁入,原全局两页退役;
 * V37 T3.4 监听/功能两页共享):
 * - [SwitchRowItem] 开关行(对齐 SettingsToggleItem 语义,参数名本地化);
 * - [OptionRowItem] 数值选项行(M3 ListItem 承载,尾值 primary,Dialog/sheet 型交互入口);
 * - [ToggleListItem] 整行 toggleable 开关行(契约 13:行内 Switch 去重语义)。
 */
@Composable
internal fun SwitchRowItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String = "",
    enabled: Boolean = true,
) {
    SettingsToggleItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

/**
 * 整行开关行(V37 T3.4 共享:监听页 ListenRow 与功能页类别行统一实现)。
 * label(+可选 supporting 副文)+ 尾部 M3 Switch;整行 toggleable(role=Switch)
 * 单击切换,内层 Switch onCheckedChange=null 并 clearAndSetSemantics 去重。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToggleListItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    supporting: String? = null,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent =
            if (supporting.isNullOrBlank()) {
                null
            } else {
                {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/**
 * 记录选项行(V9;V19 T6 矩阵 M5;V25 迁移:M3 [ListItem] 承载)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionRowItem(
    icon: ImageVector,
    title: String,
    value: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        },
        supportingContent = {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}