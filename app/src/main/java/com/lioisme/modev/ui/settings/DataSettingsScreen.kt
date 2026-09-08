package com.lioisme.modev.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.ModevConfirmDialog
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.records.recordTypeCountLabelRes
import com.lioisme.modev.ui.theme.Dimens
import com.lioisme.modev.ui.theme.Tokens

/**
 * 数据管理页(V10 设置分级;V19 T7 扩展;V37 T3.6 枚举驱动全 10 记录类型;
 * V40 T4 双维度记录浏览;V41 T5 收敛):两区 = 类型计数(每行可点 → 按类型
 * 全局列表)+ 存储占用与「清空全部」。V40 变更:按类型危险清理行退役——清理
 * 并入各记录列表(搜索条动作槽,单应用/全局语境各有确认文案),本页仅保留
 * 「清空全部」兜底。V41 变更:「按应用浏览」区退役——应用维度浏览唯一入口 =
 * 主界面应用列表(全量目录 + 作用域管理 + 启动,目标路由原本就相同)。
 * 新增 RecordType 零改动(RecordType.entries 驱动)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    viewModel: DataSettingsViewModel,
    onBack: () -> Unit,
    onOpenTypeRecords: (RecordType) -> Unit,
) {
    val totalRecords by viewModel.totalRecords.collectAsStateWithLifecycle()
    val typeCounts by viewModel.typeCounts.collectAsStateWithLifecycle()
    // 存储占用:仓库唯一出口(V23 R4,DB+wal+shm;清理动作后 VM 自动重取)
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    var pendingClearAll by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.data_settings_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.pagePadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            // 类型计数 → 按类型全局列表(V40 T4:行可点,testTag 携带 wire 值)。
            SettingsSection(stringResource(R.string.settings_type_counts_section)) {
                RecordType.entries.forEach { type ->
                    SettingsInfoItem(
                        icon = recordTypeIcon(type),
                        title = stringResource(recordTypeCountLabelRes(type)),
                        value = stringResource(
                            R.string.settings_total_records_value,
                            typeCounts[type.wire] ?: 0L,
                        ),
                        modifier = Modifier.testTag("data-type-${type.wire}"),
                        onClick = { onOpenTypeRecords(type) },
                    )
                }
            }
            Spacer(Modifier.height(Dimens.pageBottom))
            // V41 T5:「按应用浏览」区退役——应用维度浏览唯一入口 = 主界面应用列表。
            SettingsSection(stringResource(R.string.settings_storage_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_storage_db),
                    value = StorageFormat.formatBytes(storageBytes),
                )
            }
            Spacer(Modifier.height(Dimens.pageBottom))
            // V40 T4:按类型清理退役(并入各记录列表),仅保留「清空全部」。
            SettingsSection(stringResource(R.string.settings_data_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_total_records),
                    value = stringResource(R.string.settings_total_records_value, totalRecords),
                )
                DangerRow(
                    text = stringResource(R.string.settings_clear_all_records),
                    onClick = { pendingClearAll = true },
                    testTag = "clear-all",
                )
            }
            Spacer(Modifier.height(Dimens.pageBottom))
        }
    }

    if (pendingClearAll) {
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(R.string.danger_clear_all_msg),
            onConfirm = {
                viewModel.clearAll()
                pendingClearAll = false
            },
            onDismiss = { pendingClearAll = false },
        )
    }
}

/** 记录类型图标(与监控入口页同语义色系)。 */
private fun recordTypeIcon(type: RecordType): ImageVector = when (type) {
    RecordType.CRYPTO -> Icons.Default.Sensors
    RecordType.DATABASE -> Icons.Default.Storage
    RecordType.BEHAVIOR -> Icons.Outlined.FolderOpen
    RecordType.PREFS -> Icons.Default.Settings
    RecordType.WEBVIEW -> Icons.Default.Language
    RecordType.NETWORK -> Icons.Default.Dns
    RecordType.DEVICE_ENV -> Icons.Default.Memory
    RecordType.UI_INTERACTION -> Icons.Default.Warning
    RecordType.SYSTEM -> Icons.Default.PhoneAndroid
    RecordType.PROCESS_CONTROL -> Icons.Default.GppBad
}

/**
 * 危险操作行(V21 T4:标准 M3 [ListItem],leading `Delete` 图标 error 色 +
 * 标题 onSurface + chevron@50%,点击唤起居中 AlertDialog 确认,契约 13 保留)。
 */
@Composable
private fun DangerRow(
    text: String,
    onClick: () -> Unit,
    testTag: String,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        },
        leadingContent = {
            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Tokens.chevronAlpha),
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
    )
}
