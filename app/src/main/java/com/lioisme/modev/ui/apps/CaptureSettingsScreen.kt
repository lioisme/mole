package com.lioisme.modev.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsToggleItem

/**
 * 捕获设置页(2026-08-16 V5,全量捕获对齐):
 * 从算法记录列表页的「捕获设置」入口进入,单一职责 = 该应用的算法捕获开关
 * (11 类算法 + 记录堆栈 + 单字段上限 + 忽略栈前缀)+ 危险操作(移出作用域 / 清空该应用日志,均带确认)。
 * 与算法记录列表页共享同一 [AlgorithmListViewModel] 实例(ModevNav 同 key),
 * 开关状态 / 作用域 / 操作消息单一数据源;返回 = 弹栈(过导航门控)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(
    viewModel: AlgorithmListViewModel,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scopeState by viewModel.scopeState.collectAsStateWithLifecycle()
    val switch by viewModel.switchState.collectAsStateWithLifecycle()
    val scoped = (scopeState as? ScopeState.Ready)?.isScoped(viewModel.packageName) == true

    var showMaxBytesDialog by remember { mutableStateOf(false) }
    var showIgnoreStackDialog by remember { mutableStateOf(false) }
    var pendingDanger by remember { mutableStateOf<DangerAction?>(null) }

    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray())
            )
        }
    }

    Scaffold(
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.detail_tab_settings),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "switches") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SwitchRowItem(
                            icon = Icons.Filled.Lock,
                            title = stringResource(R.string.switch_cipher),
                            checked = switch.enabled(CryptoKind.CIPHER),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(cipher = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.Psychology,
                            title = stringResource(R.string.switch_message_digest),
                            checked = switch.enabled(CryptoKind.MESSAGE_DIGEST),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(messageDigest = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.SwapHoriz,
                            title = stringResource(R.string.switch_mac),
                            checked = switch.enabled(CryptoKind.MAC),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(mac = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.GppBad,
                            title = stringResource(R.string.switch_signature),
                            checked = switch.enabled(CryptoKind.SIGNATURE),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(signature = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.Key,
                            title = stringResource(R.string.switch_key_param),
                            checked = switch.enabled(CryptoKind.KEY_PARAM),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyParam = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.Star,
                            title = stringResource(R.string.switch_key_generation),
                            checked = switch.enabled(CryptoKind.KEY_GENERATION),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyGeneration = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.Key,
                            title = stringResource(R.string.switch_key_factory),
                            checked = switch.enabled(CryptoKind.KEY_FACTORY),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyFactory = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.VpnKey,
                            title = stringResource(R.string.switch_key_store),
                            checked = switch.enabled(CryptoKind.KEY_STORE),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyStore = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.SwapHoriz,
                            title = stringResource(R.string.switch_key_agreement),
                            checked = switch.enabled(CryptoKind.KEY_AGREEMENT),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(keyAgreement = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.Psychology,
                            title = stringResource(R.string.switch_random),
                            checked = switch.enabled(CryptoKind.RANDOM),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(random = on) } },
                        )
                        SwitchRowItem(
                            icon = Icons.Filled.TextFields,
                            title = stringResource(R.string.switch_base64),
                            checked = switch.enabled(CryptoKind.BASE64),
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(base64 = on) } },
                        )
                        HorizontalDivider()
                        SwitchRowItem(
                            icon = Icons.Filled.Psychology,
                            title = stringResource(R.string.switch_log_stack),
                            checked = switch.logStack,
                            onCheckedChange = { on -> viewModel.setSwitch { it.copy(logStack = on) } },
                        )
                    }
                }
            }
            item(key = "max-bytes") {
                Surface(
                    onClick = { showMaxBytesDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.capture_max_bytes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (switch.maxBytesPerField <= 0) "∞" else "${switch.maxBytesPerField}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.capture_max_bytes_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "ignore-stack") {
                Surface(
                    onClick = { showIgnoreStackDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.GppBad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.capture_ignore_stack),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                switch.ignoreStackPrefixes.size.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            stringResource(R.string.capture_ignore_stack_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (scoped) {
                item(key = "remove-scope") {
                    DangerBlock(
                        icon = Icons.Filled.GppBad,
                        text = stringResource(R.string.detail_remove_scope),
                        onClick = { pendingDanger = DangerAction.REMOVE_SCOPE },
                    )
                }
            }
            item(key = "clear-logs") {
                DangerBlock(
                    icon = Icons.Filled.Delete,
                    text = stringResource(R.string.detail_clear_logs),
                    onClick = { pendingDanger = DangerAction.CLEAR_LOGS },
                )
            }
        }
    }

    if (showMaxBytesDialog) {
        MaxBytesDialog(
            initial = switch.maxBytesPerField,
            onConfirm = { value ->
                viewModel.setSwitch { it.copy(maxBytesPerField = value) }
                showMaxBytesDialog = false
            },
            onDismiss = { showMaxBytesDialog = false },
        )
    }

    if (showIgnoreStackDialog) {
        IgnoreStackDialog(
            initial = switch.ignoreStackPrefixes,
            onConfirm = { prefixes ->
                viewModel.setSwitch { it.copy(ignoreStackPrefixes = prefixes) }
                showIgnoreStackDialog = false
            },
            onDismiss = { showIgnoreStackDialog = false },
        )
    }

    pendingDanger?.let { danger ->
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            icon = { Icon(Icons.Filled.GppBad, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = {
                Text(
                    stringResource(
                        when (danger) {
                            DangerAction.REMOVE_SCOPE -> R.string.danger_remove_scope_msg
                            DangerAction.CLEAR_LOGS -> R.string.danger_clear_logs_msg
                        },
                        viewModel.packageName,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (danger) {
                        DangerAction.REMOVE_SCOPE -> viewModel.removeScope()
                        DangerAction.CLEAR_LOGS -> viewModel.clearLogs()
                    }
                    pendingDanger = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDanger = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private enum class DangerAction { REMOVE_SCOPE, CLEAR_LOGS }

/** 单字段字节上限编辑(数字键盘,非负整数,0=无限)。 */
@Composable
private fun MaxBytesDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(if (initial <= 0) "0" else initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_max_bytes_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    if (input.all { it.isDigit() } && input.length <= 9) text = input
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(stringResource(R.string.capture_max_bytes_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toIntOrNull() ?: 0) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** 忽略栈前缀编辑(每行一个,去空行去重)。 */
@Composable
private fun IgnoreStackDialog(
    initial: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_ignore_stack_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 4,
                supportingText = { Text(stringResource(R.string.capture_ignore_stack_dialog_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                )
            }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** 开关行(对齐 LIDE SettingsToggleItem 语义,参数名本地化)。 */
@Composable
private fun SwitchRowItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsToggleItem(
        icon = icon,
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

/** 危险操作块(errorContainer 底 + onErrorContainer 文字,清空/移出作用域惯例)。 */
@Composable
private fun DangerBlock(
    icon: ImageVector,
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                text,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}