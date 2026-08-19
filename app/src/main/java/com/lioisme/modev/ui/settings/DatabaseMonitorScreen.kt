package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import kotlinx.coroutines.launch

/**
 * 数据库监听页(V17 T12,自设置页「功能」区经能力注册表入口):
 * 5 类别开关(开库 / 查询 / 增删改 / SQL 执行 / 事务,默认全开)+
 * 查询结果集双闸门(行数上限 / 字节上限,纯被动采集护栏,ADR-6)。
 * 单一事实源 = SettingsRepository.globalSwitch,变更即时广播注入侧。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseMonitorScreen(
    viewModel: DatabaseMonitorViewModel,
    onBack: () -> Unit,
) {
    val switch by viewModel.switchState.collectAsStateWithLifecycle()

    var showMaxRowsDialog by remember { mutableStateOf(false) }
    var showMaxCharsDialog by remember { mutableStateOf(false) }

    Scaffold(
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
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.switch_db_open),
                        checked = switch.dbEnabled(SqlKind.OPEN),
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbOpen = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.TableChart,
                        title = stringResource(R.string.switch_db_query),
                        checked = switch.dbEnabled(SqlKind.QUERY),
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbQuery = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Edit,
                        title = stringResource(R.string.switch_db_dml),
                        checked = switch.dbEnabled(SqlKind.DML),
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbDml = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.switch_db_exec),
                        checked = switch.dbEnabled(SqlKind.EXEC),
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbExec = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Commit,
                        title = stringResource(R.string.switch_db_transaction),
                        checked = switch.dbEnabled(SqlKind.TRANSACTION),
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbTransaction = on) } },
                    )
                }
            }
            item(key = "db-result-limits") {
                SettingsSection(stringResource(R.string.section_record_options)) {
                    OptionRowItem(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.db_max_result_rows),
                        value = switch.maxResultRows.toString(),
                        hint = stringResource(R.string.db_max_result_rows_hint),
                        onClick = { showMaxRowsDialog = true },
                    )
                    OptionRowItem(
                        icon = Icons.Filled.GridOn,
                        title = stringResource(R.string.db_max_result_chars),
                        value = switch.maxResultChars.toString(),
                        hint = stringResource(R.string.db_max_result_chars_hint),
                        onClick = { showMaxCharsDialog = true },
                    )
                }
            }
        }
    }

    if (showMaxRowsDialog) {
        IntLimitSheet(
            titleRes = R.string.db_max_result_rows_dialog_title,
            hintRes = R.string.db_max_result_rows_hint,
            initial = switch.maxResultRows,
            onConfirm = { value ->
                viewModel.setSwitch { it.copy(maxResultRows = value) }
                showMaxRowsDialog = false
            },
            onDismiss = { showMaxRowsDialog = false },
        )
    }

    if (showMaxCharsDialog) {
        IntLimitSheet(
            titleRes = R.string.db_max_result_chars_dialog_title,
            hintRes = R.string.db_max_result_chars_hint,
            initial = switch.maxResultChars,
            onConfirm = { value ->
                viewModel.setSwitch { it.copy(maxResultChars = value) }
                showMaxCharsDialog = false
            },
            onDismiss = { showMaxCharsDialog = false },
        )
    }
}

/** 整数值编辑底部 sheet(V15 规范:键盘输入一律底部化,ime 上推不遮挡)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntLimitSheet(
    titleRes: Int,
    hintRes: Int,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun confirm() {
        val value = text.toIntOrNull() ?: 0
        scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm(value) }
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 9) text = input
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(stringResource(hintRes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { confirm() }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}