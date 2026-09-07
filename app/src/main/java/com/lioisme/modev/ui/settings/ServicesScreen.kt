package com.lioisme.modev.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.ModevPortSheet
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.SettingsToggleItem

/**
 * 服务页(V39 拆分自「服务与诊断」;V40 T2 注入侧分区标注):两分区 = 通信服务(注入侧 wire)
 * + MCP 服务。只承载**配置动作**(开关/端口/令牌入口);运行时观测(框架/作用域/统计/快照)
 * 归诊断页。端口编辑统一走 [ModevPortSheet](此前 wire/MCP 两份逐行重复实现收编)。
 *
 * 认证语义(V40 D-4 解耦):MCP 外部访问与令牌认证**互相独立**——外部 0.0.0.0
 * 是否可无认证访问由用户显式选择(外部访问行副标题承载风险提示)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    viewModel: ServicesViewModel,
    onBack: () -> Unit,
    onOpenTokens: () -> Unit,
) {
    val wireExternalAccess by viewModel.wireExternalAccess.collectAsStateWithLifecycle()
    val wirePort by viewModel.wirePort.collectAsStateWithLifecycle()
    val wireAddress by viewModel.wireListeningAddress.collectAsStateWithLifecycle()
    val mcpEnabled by viewModel.mcpEnabled.collectAsStateWithLifecycle()
    val mcpPort by viewModel.mcpPort.collectAsStateWithLifecycle()
    val mcpAddress by viewModel.mcpListeningAddress.collectAsStateWithLifecycle()
    val mcpBindError by viewModel.mcpBindError.collectAsStateWithLifecycle()
    val mcpExternalAccess by viewModel.mcpExternalAccess.collectAsStateWithLifecycle()
    val mcpTokenAuth by viewModel.mcpTokenAuth.collectAsStateWithLifecycle()
    val mcpTokenCount by viewModel.mcpTokenCount.collectAsStateWithLifecycle()

    var wirePortSheetOpen by rememberSaveable { mutableStateOf(false) }
    var mcpPortSheetOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.services_settings_title),
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
            Spacer(Modifier.height(8.dp))
            // V40 T2:注入侧标注(与诊断页共用 settings_server_section 语义解耦,
            // 明示 wire 通道 = 注入侧 TCP,非 HTTP)。
            SettingsSection(stringResource(R.string.settings_server_section_injection)) {
                SettingsToggleItem(
                    icon = Icons.Default.Public,
                    title = stringResource(R.string.settings_external_access),
                    subtitle = stringResource(R.string.settings_external_access_hint),
                    checked = wireExternalAccess,
                    modifier = Modifier.testTag("services-wire-external"),
                    onCheckedChange = viewModel::setWireExternalAccess,
                )
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_port),
                    value = stringResource(R.string.settings_port_value, wireAddress, wirePort),
                    onClick = { wirePortSheetOpen = true },
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.mcp_section)) {
                SettingsToggleItem(
                    icon = Icons.Default.Hub,
                    title = stringResource(R.string.mcp_enabled),
                    subtitle = stringResource(R.string.mcp_enabled_hint),
                    checked = mcpEnabled,
                    modifier = Modifier.testTag("services-mcp-enabled"),
                    onCheckedChange = viewModel::setMcpEnabled,
                )
                SettingsToggleItem(
                    icon = Icons.Default.Public,
                    title = stringResource(R.string.mcp_external_access),
                    subtitle = stringResource(R.string.mcp_external_access_hint),
                    checked = mcpExternalAccess,
                    enabled = mcpEnabled,
                    modifier = Modifier.testTag("services-mcp-external"),
                    onCheckedChange = viewModel::setMcpExternalAccess,
                )
                SettingsToggleItem(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.mcp_token_auth),
                    subtitle = stringResource(R.string.mcp_token_auth_hint),
                    checked = mcpTokenAuth,
                    enabled = mcpEnabled,
                    modifier = Modifier.testTag("services-mcp-token-auth"),
                    onCheckedChange = viewModel::setMcpTokenAuth,
                )
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.mcp_port),
                    value = mcpAddress?.let {
                        stringResource(R.string.settings_port_value, it, mcpPort)
                    } ?: mcpPort.toString(),
                    onClick = { mcpPortSheetOpen = true },
                )
                Box(Modifier.testTag("services-tokens-entry")) {
                    SettingsItem(
                        icon = Icons.Default.Key,
                        title = stringResource(R.string.mcp_tokens_entry),
                        subtitle = pluralStringResource(
                            R.plurals.mcp_tokens_count,
                            mcpTokenCount,
                            mcpTokenCount,
                        ),
                        onClick = onOpenTokens,
                    )
                }
                mcpBindError?.let { error ->
                    SettingsInfoItem(
                        icon = Icons.Default.Warning,
                        title = stringResource(R.string.mcp_enabled),
                        value = stringResource(R.string.mcp_bind_error, error),
                        valueMaxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (wirePortSheetOpen) {
        ModevPortSheet(
            label = stringResource(R.string.settings_port),
            currentPort = wirePort,
            onConfirm = {
                viewModel.setWirePort(it)
                wirePortSheetOpen = false
            },
            onDismiss = { wirePortSheetOpen = false },
            modifier = Modifier.testTag("port-setting-sheet"),
        )
    }
    if (mcpPortSheetOpen) {
        ModevPortSheet(
            label = stringResource(R.string.mcp_port),
            currentPort = mcpPort,
            onConfirm = {
                viewModel.setMcpPort(it)
                mcpPortSheetOpen = false
            },
            onDismiss = { mcpPortSheetOpen = false },
            modifier = Modifier.testTag("mcp-port-setting-sheet"),
        )
    }
}
