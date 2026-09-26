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

/** MCP 端点形态副标题(V41 D-4:开态动态有效值,非中文技术字面量)。 */
private const val MCP_ENDPOINT_SUBTITLE = "POST /mcp"

/**
 * 令牌认证开态副标题:鉴权机制一词承载,风险语义由令牌页状态行承载。
 * internal = 服务页/令牌页两处开关同源共用(同包跨文件)。
 */
internal const val TOKEN_AUTH_BEARER_SUBTITLE = "Bearer"

/**
 * 服务页(V39 拆分自「服务与诊断」;V40 T2 注入侧分区标注;V41 T2 统一外部访问):
 * 三分区 = 通用(统一外部访问)/ 通信服务(注入侧 wire)/ MCP 服务。
 * 只承载**配置动作**(开关/端口/令牌入口);运行时观测(框架/作用域/统计/快照)
 * 归诊断页。端口编辑统一走 [ModevPortSheet](此前 wire/MCP 两份逐行重复实现收编)。
 *
 * 外部访问(D-1 统一):单一开关同时控制 wire + MCP 绑定(启用 0.0.0.0 / 停用
 * 127.0.0.1,副标题经 [ServicesViewModel.externalAddressLabel] 动态承载),
 * 中性归属——既不属于通信服务也不属于 MCP。认证语义不变:令牌认证
 * 独立开关,启用后全接口强制 Bearer,与外部访问无级联。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    viewModel: ServicesViewModel,
    onBack: () -> Unit,
    onOpenTokens: () -> Unit,
) {
    val externalAccess by viewModel.externalAccess.collectAsStateWithLifecycle()
    val wirePort by viewModel.wirePort.collectAsStateWithLifecycle()
    val wireBindError by viewModel.wireBindError.collectAsStateWithLifecycle()
    val mcpEnabled by viewModel.mcpEnabled.collectAsStateWithLifecycle()
    val mcpPort by viewModel.mcpPort.collectAsStateWithLifecycle()
    val mcpBindError by viewModel.mcpBindError.collectAsStateWithLifecycle()
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
            // V41 T2:「通用」中性分区——统一外部访问开关(D-1:双服务消费,
            // 既不属于通信服务也不属于 MCP;副标题 = 动态绑定地址 0.0.0.0/127.0.0.1)。
            SettingsSection(stringResource(R.string.services_common_section)) {
                SettingsToggleItem(
                    icon = Icons.Default.Public,
                    title = stringResource(R.string.settings_external_access),
                    subtitle = viewModel.externalAddressLabel(externalAccess),
                    checked = externalAccess,
                    modifier = Modifier.testTag("services-external-access"),
                    onCheckedChange = viewModel::setExternalAccess,
                )
            }
            Spacer(Modifier.height(24.dp))
            // V40 T2:注入侧标注(与诊断页共用 settings_server_section 语义解耦,
            // 明示 wire 通道 = 注入侧 TCP,非 HTTP)。端口行 V41 起仅显示端口(IP 由
            // 统一外部访问开关唯一承载,D-2)。
            SettingsSection(stringResource(R.string.settings_server_section_injection)) {
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_port),
                    value = wirePort.toString(),
                    onClick = { wirePortSheetOpen = true },
                )
                // wire 绑定失败观测行(V42 T3 D-4,与 MCP 错误行形态对称):
                // 静默退避重试保持不变,本行仅承载「用户可见性」。
                wireBindError?.let { error ->
                    SettingsInfoItem(
                        icon = Icons.Default.Warning,
                        title = stringResource(R.string.wire_bind_error_title),
                        value = stringResource(R.string.wire_bind_error, error),
                        valueMaxLines = 1,
                        modifier = Modifier.testTag("wire-bind-error-row"),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.mcp_section)) {
                // 副标题动态有效值:开 = 端点形态(技术字面量);关 = 无副标题。
                SettingsToggleItem(
                    icon = Icons.Default.Hub,
                    title = stringResource(R.string.mcp_enabled),
                    subtitle = if (mcpEnabled) MCP_ENDPOINT_SUBTITLE else "",
                    checked = mcpEnabled,
                    modifier = Modifier.testTag("services-mcp-enabled"),
                    onCheckedChange = viewModel::setMcpEnabled,
                )
                // 令牌认证副标题动态化:开 = Bearer;关 = 简短「无需令牌」。
                SettingsToggleItem(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.mcp_token_auth),
                    subtitle = if (mcpTokenAuth) {
                        TOKEN_AUTH_BEARER_SUBTITLE
                    } else {
                        stringResource(R.string.mcp_token_auth_off_hint)
                    },
                    checked = mcpTokenAuth,
                    enabled = mcpEnabled,
                    modifier = Modifier.testTag("services-mcp-token-auth"),
                    onCheckedChange = viewModel::setMcpTokenAuth,
                )
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.mcp_port),
                    value = mcpPort.toString(),
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
                        title = stringResource(R.string.mcp_bind_error_title),
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
            validatePort = viewModel.validateWirePort(),
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
            validatePort = viewModel.validateMcpPort(),
            modifier = Modifier.testTag("mcp-port-setting-sheet"),
        )
    }
}
