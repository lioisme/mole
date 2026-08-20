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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import kotlinx.coroutines.launch

/**
 * 监听设置页(V19 T4,合并自算法监听 / 数据库监听两页,单一入口):
 * ①算法监听(总开关 + 12 类别)②记录选项(记录堆栈 / 单字段字节上限 / 忽略栈前缀)
 * ③数据库监听(总开关 + 5 类别)④结果闸门(行数上限 / 字节上限,纯被动采集护栏)。
 * 总开关停用各自类别组,分开关保留原态;编辑一律底部 sheet(ime 上推不遮挡)。
 * 单一事实源 = SettingsRepository.globalSwitch,变更即时广播注入侧。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorSettingsScreen(
    viewModel: MonitorSettingsViewModel,
    onBack: () -> Unit,
) {
    val switch by viewModel.switchState.collectAsStateWithLifecycle()

    var showMaxBytesSheet by remember { mutableStateOf(false) }
    var showIgnoreStackSheet by remember { mutableStateOf(false) }
    var showMaxRowsSheet by remember { mutableStateOf(false) }
    var showMaxCharsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.monitor_settings_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "algorithm-kinds") {
                SettingsSection(stringResource(R.string.algorithm_monitor_title)) {
                    SwitchRowItem(
                        icon = Icons.Filled.PowerSettingsNew,
                        title = stringResource(R.string.algorithm_monitor_master),
                        subtitle = stringResource(R.string.algorithm_monitor_master_hint),
                        checked = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(cryptoListen = on) } },
                    )
                    HorizontalDivider()
                    SwitchRowItem(
                        icon = Icons.Filled.Lock,
                        title = stringResource(R.string.switch_cipher),
                        checked = switch.enabled(CryptoKind.CIPHER),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(cipher = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Fingerprint,
                        title = stringResource(R.string.switch_message_digest),
                        checked = switch.enabled(CryptoKind.MESSAGE_DIGEST),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(messageDigest = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.VerifiedUser,
                        title = stringResource(R.string.switch_mac),
                        checked = switch.enabled(CryptoKind.MAC),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(mac = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Draw,
                        title = stringResource(R.string.switch_signature),
                        checked = switch.enabled(CryptoKind.SIGNATURE),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(signature = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.switch_key_param),
                        checked = switch.enabled(CryptoKind.KEY_PARAM),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyParam = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.AutoAwesome,
                        title = stringResource(R.string.switch_key_generation),
                        checked = switch.enabled(CryptoKind.KEY_GENERATION),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyGeneration = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Key,
                        title = stringResource(R.string.switch_key_factory),
                        checked = switch.enabled(CryptoKind.KEY_FACTORY),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyFactory = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.VpnKey,
                        title = stringResource(R.string.switch_key_store),
                        checked = switch.enabled(CryptoKind.KEY_STORE),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyStore = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        title = stringResource(R.string.switch_key_agreement),
                        checked = switch.enabled(CryptoKind.KEY_AGREEMENT),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyAgreement = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Casino,
                        title = stringResource(R.string.switch_random),
                        checked = switch.enabled(CryptoKind.RANDOM),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(random = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Abc,
                        title = stringResource(R.string.switch_base64),
                        checked = switch.enabled(CryptoKind.BASE64),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(base64 = on) } },
                    )
                    SwitchRowItem(
                        icon = Icons.Filled.Web,
                        title = stringResource(R.string.switch_webview),
                        checked = switch.enabled(CryptoKind.WEB_CRYPTO),
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(webView = on) } },
                    )
                }
            }
            item(key = "record-options") {
                SettingsSection(stringResource(R.string.section_record_options)) {
                    SwitchRowItem(
                        icon = Icons.Filled.AccountTree,
                        title = stringResource(R.string.switch_log_stack),
                        checked = switch.logStack,
                        enabled = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(logStack = on) } },
                    )
                    HorizontalDivider()
                    OptionRowItem(
                        icon = Icons.Filled.DataUsage,
                        title = stringResource(R.string.capture_max_bytes),
                        value = if (switch.maxBytesPerField <= 0) "∞" else "${switch.maxBytesPerField}",
                        hint = stringResource(R.string.capture_max_bytes_hint),
                        onClick = { showMaxBytesSheet = true },
                    )
                    OptionRowItem(
                        icon = Icons.Filled.FilterAlt,
                        title = stringResource(R.string.capture_ignore_stack),
                        value = switch.ignoreStackPrefixes.size.toString(),
                        hint = stringResource(R.string.capture_ignore_stack_hint),
                        onClick = { showIgnoreStackSheet = true },
                    )
                }
            }
            item(key = "db-kinds") {
                SettingsSection(stringResource(R.string.database_monitor_title)) {
                    SwitchRowItem(
                        icon = Icons.Filled.PowerSettingsNew,
                        title = stringResource(R.string.database_monitor_master),
                        subtitle = stringResource(R.string.database_monitor_master_hint),
                        checked = switch.dbListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(dbListen = on) } },
                    )
                    HorizontalDivider()
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

    if (showMaxBytesSheet) {
        MaxBytesSheet(
            initial = switch.maxBytesPerField,
            onConfirm = { value ->
                viewModel.setSwitch { it.copy(maxBytesPerField = value) }
                showMaxBytesSheet = false
            },
            onDismiss = { showMaxBytesSheet = false },
        )
    }

    if (showIgnoreStackSheet) {
        IgnoreStackSheet(
            initial = switch.ignoreStackPrefixes,
            onConfirm = { prefixes ->
                viewModel.setSwitch { it.copy(ignoreStackPrefixes = prefixes) }
                showIgnoreStackSheet = false
            },
            onDismiss = { showIgnoreStackSheet = false },
        )
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

/** 单字段字节上限编辑(底部 sheet,V15 规范:键盘输入一律底部化,ime 上推不遮挡)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaxBytesSheet(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(if (initial <= 0) "0" else initial.toString()) }
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
                stringResource(R.string.capture_max_bytes_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 9) text = input
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(stringResource(R.string.capture_max_bytes_hint)) },
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

/** 忽略栈前缀编辑(底部 sheet,每行一个,去空行去重)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IgnoreStackSheet(
    initial: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun confirm() {
        val prefixes = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm(prefixes) }
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
                stringResource(R.string.capture_ignore_stack_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 4,
                supportingText = { Text(stringResource(R.string.capture_ignore_stack_dialog_hint)) },
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
                fontWeight = FontWeight.SemiBold,
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