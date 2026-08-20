package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.data.db.ModevDatabase
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection

/** 待确认的清理目标(按类型 / 全部,危险 AlertDialog 确认)。 */
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
    var pendingClear by remember { mutableStateOf<ClearTarget?>(null) }
    val context = LocalContext.current
    // 存储占用:DB 文件大小(SQLite 清理后页不回收,静态值即可,remember 一次)。
    val dbSizeBytes = remember(context) { context.getDatabasePath(ModevDatabase.NAME).length().toLong() }

    Scaffold(
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
                    value = StorageFormat.formatBytes(dbSizeBytes),
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

/** 危险操作行(errorContainer 质感,点击唤起确认对话框)。 */
@Composable
private fun DangerRow(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}