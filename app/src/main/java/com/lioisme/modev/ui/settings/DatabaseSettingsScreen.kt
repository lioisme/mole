package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection

/**
 * 数据库监听设置页(V21 T3,自 V19 合并页拆分还原 V16 IA;路由 settings/database):
 * ①数据库监听(总开关 + 5 类别,总开关停用类别组、分开关保留原态)
 * ②结果闸门(行数上限 / 字符上限,纯被动采集护栏,编辑一律底部 sheet)。
 * 单一事实源 = SettingsRepository.globalSwitch,变更即时广播注入侧。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseSettingsScreen(
    viewModel: DatabaseSettingsViewModel,
    onBack: () -> Unit,
) {
    val switch by viewModel.switchState.collectAsStateWithLifecycle()

    var showMaxRowsSheet by remember { mutableStateOf(false) }
    var showMaxCharsSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.database_monitor_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "db-kinds") {
                SettingsSection(stringResource(R.string.section_monitor_kinds)) {
                    SwitchRowItem(
                        icon = Icons.Filled.PowerSettingsNew,
                        title = stringResource(R.string.database_monitor_master),
                        subtitle = stringResource(R.string.database_monitor_master_hint),
                        checked = switch.dbListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbListen = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.FolderOpen,
                        title = stringResource(R.string.switch_db_open),
                        checked = switch.dbEnabled(SqlKind.OPEN),
                        enabled = switch.dbListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbOpen = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.TableChart,
                        title = stringResource(R.string.switch_db_query),
                        checked = switch.dbEnabled(SqlKind.QUERY),
                        enabled = switch.dbListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbQuery = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Edit,
                        title = stringResource(R.string.switch_db_dml),
                        checked = switch.dbEnabled(SqlKind.DML),
                        enabled = switch.dbListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbDml = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.switch_db_exec),
                        checked = switch.dbEnabled(SqlKind.EXEC),
                        enabled = switch.dbListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbExec = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Commit,
                        title = stringResource(R.string.switch_db_transaction),
                        checked = switch.dbEnabled(SqlKind.TRANSACTION),
                        enabled = switch.dbListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbTransaction = on) } },
                    )
                }
            }
            item(key = "db-result-limits") {
                SettingsSection(stringResource(R.string.section_db_result_limits)) {
                    OptionRowItem(
                        icon = Icons.Filled.TableRows,
                        title = stringResource(R.string.db_max_result_rows),
                        value = switch.maxResultRows.toString(),
                        hint = stringResource(R.string.db_max_result_rows_hint),
                        onClick = { showMaxRowsSheet = true },
                    )
                    OptionRowItem(
                        icon = Icons.Filled.TextFields,
                        title = stringResource(R.string.db_max_result_chars),
                        value = switch.maxResultChars.toString(),
                        hint = stringResource(R.string.db_max_result_chars_hint),
                        onClick = { showMaxCharsSheet = true },
                    )
                }
            }
        }
    }

    if (showMaxRowsSheet) {
        IntLimitSheet(
            titleRes = R.string.db_max_result_rows_dialog_title,
            hintRes = R.string.db_max_result_rows_hint,
            initial = switch.maxResultRows,
            onConfirm = { value ->
                viewModel.setSwitch { it.copy(maxResultRows = value) }
                showMaxRowsSheet = false
            },
            onDismiss = { showMaxRowsSheet = false },
        )
    }

    if (showMaxCharsSheet) {
        IntLimitSheet(
            titleRes = R.string.db_max_result_chars_dialog_title,
            hintRes = R.string.db_max_result_chars_hint,
            initial = switch.maxResultChars,
            onConfirm = { value ->
                viewModel.setSwitch { it.copy(maxResultChars = value) }
                showMaxCharsSheet = false
            },
            onDismiss = { showMaxCharsSheet = false },
        )
    }
}