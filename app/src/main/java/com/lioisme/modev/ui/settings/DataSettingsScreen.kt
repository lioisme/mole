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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection

/** 待确认的清理目标(按类型 / 全部,危险 AlertDialog 确认)。*/
private enum class ClearTarget {
    ALGORITHM,
    DATABASE,
    ALL,
}

/**
 * 数据管理页(V10 设置分级;V19 T7 扩展):
 * 记录分类(算法/数据库计数)+ 存储占用(数据库文件大小)+
 * 按类型清理 / 清空全部(危险操作,确认后无回收站)。
 * 应用级日志清空已归位应用记录页「更多」菜单。
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.padding(top = 8.dp))
            SettingsSection(stringResource(R.string.settings_type_counts_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Terminal,
                    title = stringResource(R.string.settings_records_algorithm),
                    value = stringResource(R.string.settings_total_records_value, typeCounts[RecordType.CRYPTO.wire] ?: 0L),
                )
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_records_database),
                    value = stringResource(R.string.settings_total_records_value, typeCounts[RecordType.DATABASE.wire] ?: 0L),
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_storage_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_storage_db),
                    value = StorageFormat.formatBytes(storageBytes),
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_data_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_total_records),
                    value = stringResource(R.string.settings_total_records_value, totalRecords),
                )
                DangerRow(
                    text = stringResource(R.string.settings_clear_algorithm_records),
                    onClick = { pendingClear = ClearTarget.ALGORITHM },
                )
                DangerRow(
                    text = stringResource(R.string.settings_clear_database_records),
                    onClick = { pendingClear = ClearTarget.DATABASE },
                )
                DangerRow(
                    text = stringResource(R.string.settings_clear_all_records),
                    onClick = { pendingClear = ClearTarget.ALL },
                )
            }
            Spacer(Modifier.height(24.dp))
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
                            ClearTarget.ALGORITHM -> R.string.danger_clear_algorithm_msg
                            ClearTarget.DATABASE -> R.string.danger_clear_database_msg
                            ClearTarget.ALL -> R.string.danger_clear_all_msg
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (target) {
                        ClearTarget.ALGORITHM -> viewModel.clearByType(RecordType.CRYPTO)
                        ClearTarget.DATABASE -> viewModel.clearByType(RecordType.DATABASE)
                        ClearTarget.ALL -> viewModel.clearAll()
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

/**
 * 危险操作行(V21 T4:整块 errorContainer 色块 → 标准 M3 [ListItem],
 * leading `Delete` 图标 error 色 + 标题 onSurface + chevron@50%,
 * 点击唤起居中 AlertDialog 确认,契约 13 保留)。
 */
@Composable
private fun DangerRow(
    text: String,
    onClick: () -> Unit,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}