package com.lioisme.modev.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 设置页组件集(2026-08-15 UI 重构 Phase 2,移植自 LIDE SettingsScreen;
 * V19 T6 矩阵 M2/M3/M4:全部行组件收敛为 M3 [ListItem];
 * V21 T1 决策记录 + E1 修订:卡片容器 surfaceContainerLow(与 LIDE 逐字一致)):
 * 分区标题 + 卡片 / 可点击项 / 开关项 / 只读信息行。
 * 视觉 token 与 LIDE 逐项一致:icon 24dp、间距 16dp、ChevronRight@50% alpha、
 * 禁用态 onSurface@38%。
 *
 * 卡片容器决策(V21 修订,SettingsThemeContrastTest 数据驱动):materialkolor
 * TONAL_SPOT 下 surface 与 surfaceContainerLow 对比度实测仅 1.047~1.10:1
 * (亮/暗),「背景与卡片同色」不可辨;曾升为 surfaceContainerHighest(M3
 * Filled 卡语义,实测亮 1.222:1 / 暗 1.494:1)应对雷电转译渲染问题;
 * 后经 ListItem 显式容器色修复(雷电 Unspecified Surface 嵌套 bug),
 * 最终对齐 LIDE 契约回退 surfaceContainerLow——仍为 M3 tonal surface 家族 token。
 */

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        colors =
            CardDefaults.cardColors(
                // 与 LIDE SettingsSection 逐字一致:surfaceContainerLow(E1 决策)。
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        shape = MaterialTheme.shapes.medium,
    ) { Column { content() } }
}

/** 可点击项(M3 ListItem 承载):icon + 标题(+ 副标题)+ 尾部 chevron。*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent =
            if (subtitle.isNotEmpty()) {
                {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/**
 * 开关项(M3 ListItem 承载):行 = toggleable(role=Switch,整行语义即开关,
 * 一焦点、一动作、双击切换);内嵌 Switch clearAndSetSemantics 去重
 * (双节点各读各讲是 TalkBack 噪音)。互斥置灰:禁用时整体淡化且不可点击,
 * 禁用态 M3 惯例 = onSurface@38%。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
        },
        supportingContent =
            if (subtitle.isNotEmpty()) {
                {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        leadingContent = {
            Icon(
                icon,
                null,
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
    )
}

/**
 * 只读信息行(M3 ListItem 承载):icon + 标题 + 值;
 * 值以 [MAX_VALUE_WIDTH] 上限有界测量(V11 长值挤压标题回归防线)。
 * V26 扩展(可选参数,既有调用零波及):
 * - [valueMaxLines]:值行数上限(诊断页框架行单行省略语义);
 * - [onClick] 非 null 时整行可点击并追加尾部 chevron(配合底部弹层展示完整值)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueMaxLines: Int = 2,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = MAX_VALUE_WIDTH),
                )
                if (onClick != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .let { base -> onClick?.let { base.clickable(onClick = it) } ?: base },
    )
}

/** 只读值上限宽度(约行宽 50%,V11 长值挤压标题回归防线)。*/
private val MAX_VALUE_WIDTH = 160.dp
