package com.lioisme.modev.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.theme.Dimens
import com.lioisme.modev.ui.theme.Tokens

/** 待确认的清理目标(按类型 / 全部,危险 AlertDialog 确认;V37 T3.6 全 10 类型)。 */
private sealed interface ClearTarget {
    data class ByType(val type: RecordType) : ClearTarget
    data object All : ClearTarget
}

/**
 * 数据管理页(V10 设置分级;V19 T7 扩展;V37 T3.6 枚举驱动全 10 记录类型):
 * 记录分类计数 + 存储占用 + 按类型清理 / 清空全部(危险操作,确认后无回收站)。
 * 新增 RecordType 零改动(RecordType.entries 驱动)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    viewModel: DataSettingsViewModel,
    onBack: () -> Unit,
) {
    val totalRecords by viewModel.totalRecords.collectAsStateWithLifecycle()
    val typeCounts by viewModel.typeCounts.collectAsStateWithLifecycle()
    // 存储占用:仓库唯一出口(V23 R4,DB+wal+shm;清理动作后 VM 自动重取)
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    var pendingClear by remember { mutableStateOf<ClearTarget?>(null) }

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
            SettingsSection(stringResource(R.string.settings_type_counts_section)) {
                RecordType.entries.forEach { type ->
                    SettingsInfoItem(
                        icon = recordTypeIcon(type),
                        title = stringResource(recordTypeCountLabelRes(type)),
                        value = stringResource(
                            R.string.settings_total_records_value,
                            typeCounts[type.wire] ?: 0L,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(Dimens.pageBottom))
            SettingsSection(stringResource(R.string.settings_storage_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_storage_db),
                    value = StorageFormat.formatBytes(storageBytes),
                )
            }
            Spacer(Modifier.height(Dimens.pageBottom))
            SettingsSection(stringResource(R.string.settings_data_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_total_records),
                    value = stringResource(R.string.settings_total_records_value, totalRecords),
                )
                RecordType.entries.forEach { type ->
                    DangerRow(
                        text = stringResource(recordTypeClearLabelRes(type)),
                        onClick = { pendingClear = ClearTarget.ByType(type) },
                        testTag = "clear-type-${type.wire}",
                    )
                }
                DangerRow(
                    text = stringResource(R.string.settings_clear_all_records),
                    onClick = { pendingClear = ClearTarget.All },
                    testTag = "clear-all",
                )
            }
            Spacer(Modifier.height(Dimens.pageBottom))
        }
    }

    pendingClear?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = {
                Text(
                    stringResource(
                        when (target) {
                            is ClearTarget.ByType -> recordTypeClearMsgRes(target.type)
                            ClearTarget.All -> R.string.danger_clear_all_msg
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        is ClearTarget.ByType -> viewModel.clearByType(target.type)
                        ClearTarget.All -> viewModel.clearAll()
                    }
                    pendingClear = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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

/** 计数行标题(V37 T3.6:全 10 类型;既有 5 键保留,新增 5 键同构命名)。 */
private fun recordTypeCountLabelRes(type: RecordType): Int = when (type) {
    RecordType.CRYPTO -> R.string.settings_records_algorithm
    RecordType.DATABASE -> R.string.settings_records_database
    RecordType.BEHAVIOR -> R.string.settings_records_behavior
    RecordType.PREFS -> R.string.settings_records_prefs
    RecordType.WEBVIEW -> R.string.settings_records_webview
    RecordType.NETWORK -> R.string.settings_records_network
    RecordType.DEVICE_ENV -> R.string.settings_records_device_env
    RecordType.UI_INTERACTION -> R.string.settings_records_ui_interaction
    RecordType.SYSTEM -> R.string.settings_records_system
    RecordType.PROCESS_CONTROL -> R.string.settings_records_process_control
}

/** 清理行标题。 */
private fun recordTypeClearLabelRes(type: RecordType): Int = when (type) {
    RecordType.CRYPTO -> R.string.settings_clear_algorithm_records
    RecordType.DATABASE -> R.string.settings_clear_database_records
    RecordType.BEHAVIOR -> R.string.settings_clear_behavior_records
    RecordType.PREFS -> R.string.settings_clear_prefs_records
    RecordType.WEBVIEW -> R.string.settings_clear_webview_records
    RecordType.NETWORK -> R.string.settings_clear_network_records
    RecordType.DEVICE_ENV -> R.string.settings_clear_device_env_records
    RecordType.UI_INTERACTION -> R.string.settings_clear_ui_interaction_records
    RecordType.SYSTEM -> R.string.settings_clear_system_records
    RecordType.PROCESS_CONTROL -> R.string.settings_clear_process_control_records
}

/** 清理确认文案。 */
private fun recordTypeClearMsgRes(type: RecordType): Int = when (type) {
    RecordType.CRYPTO -> R.string.danger_clear_algorithm_msg
    RecordType.DATABASE -> R.string.danger_clear_database_msg
    RecordType.BEHAVIOR -> R.string.danger_clear_behavior_msg
    RecordType.PREFS -> R.string.danger_clear_prefs_msg
    RecordType.WEBVIEW -> R.string.danger_clear_webview_msg
    RecordType.NETWORK -> R.string.danger_clear_network_msg
    RecordType.DEVICE_ENV -> R.string.danger_clear_device_env_msg
    RecordType.UI_INTERACTION -> R.string.danger_clear_ui_interaction_msg
    RecordType.SYSTEM -> R.string.danger_clear_system_msg
    RecordType.PROCESS_CONTROL -> R.string.danger_clear_process_control_msg
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
