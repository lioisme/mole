package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection

/**
 * 数据管理页(V10 设置分级,自设置页「数据与服务」入口):
 * 全库记录总数(只读)+ 清空全部记录(危险操作,确认后无回收站)。
 * 应用级日志清空已归位应用记录页「更多」菜单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    viewModel: DataSettingsViewModel,
    onBack: () -> Unit,
) {
    val totalRecords by viewModel.totalRecords.collectAsStateWithLifecycle()
    var showClearAllConfirm by remember { mutableStateOf(false) }

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
            SettingsSection(stringResource(R.string.settings_data_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_total_records),
                    value = stringResource(R.string.settings_total_records_value, totalRecords),
                )
                Surface(
                    onClick = { showClearAllConfirm = true },
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
                            stringResource(R.string.settings_clear_all_logs),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = { Text(stringResource(R.string.danger_clear_all_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearAllConfirm = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
