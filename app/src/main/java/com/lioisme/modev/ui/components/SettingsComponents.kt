package com.lioisme.modev.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * V19 T6 矩阵 M2/M3/M4:全部行组件收敛为 M3 [ListItem]):
 * 分区标题 + 卡片(SurfaceContainerLow)/ 可点击项 / 开关项 / 只读信息行。
 * 视觉 token 与 LIDE 逐项一致:icon 24dp、间距 16dp、ChevronRight@50% alpha、
 * 禁用态 onSurface@38%。
 */

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        shape = MaterialTheme.shapes.medium,
    ) { Column { content() } }
}

/** 可点击项(M3 ListItem 承载):icon + 标题(+ 副标题)+ 尾部 chevron。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    ListItem(
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
            Modifier
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
 * 只读信息行(M3 ListItem 承载):icon + 标题 + 值(无点击、无 chevron,
 * 服务状态等静态信息)。值以 [MAX_VALUE_WIDTH] 上限有界测量
 * (V11 修复延续:无界时 Row 按内容全宽测量长值,标题被挤压逐字折行;
 * 值最多两行,超界省略,Key 位置恒定)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String,
) {
    ListItem(
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
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = MAX_VALUE_WIDTH),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 只读值上限宽度(约行宽 50%,V11 长值挤压标题回归防线)。 */
private val MAX_VALUE_WIDTH = 160.dp