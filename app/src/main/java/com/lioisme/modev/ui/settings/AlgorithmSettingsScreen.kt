package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Web
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
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection

/**
 * 算法监听设置页(V21 T3,自 V19 合并页拆分还原 V16 IA;路由 settings/algorithm):
 * ①算法监听(总开关 + 12 类别,总开关停用类别组、分开关保留原态)
 * ②记录选项(记录堆栈 / 单字段字节上限 / 忽略栈前缀,编辑一律底部 sheet)。
 * 单一事实源 = SettingsRepository.globalSwitch,变更即时广播注入侧。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlgorithmSettingsScreen(
    viewModel: AlgorithmSettingsViewModel,
    onBack: () -> Unit,
) {
    val switch by viewModel.switchState.collectAsStateWithLifecycle()

    var showMaxBytesSheet by remember { mutableStateOf(false) }
    var showIgnoreStackSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.algorithm_monitor_title),
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
                SettingsSection(stringResource(R.string.section_monitor_kinds)) {
                    SwitchRowItem(
                        icon = Icons.Filled.PowerSettingsNew,
                        title = stringResource(R.string.algorithm_monitor_master),
                        subtitle = stringResource(R.string.algorithm_monitor_master_hint),
                        checked = switch.cryptoListen,
                        onCheckedChange = { on -> viewModel.setSwitch { it.copy(cryptoListen = on) } },
                    )
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
}