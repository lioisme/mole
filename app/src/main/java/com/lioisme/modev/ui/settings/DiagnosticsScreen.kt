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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.domain.diagnostics.DiagnosticsReport
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.apps.AppCountFormat
import com.lioisme.modev.ui.components.ModevSheet
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.SheetContentPadding
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import kotlinx.coroutines.launch

/**
 * 诊断页(V10 设置分级;V19 T8 分区化;V21 T5 重新设计;V26 详情弹层;
 * V39 服务/诊断拆分:服务配置面迁 [ServicesScreen],本页收敛为纯运行时观测;
 * V40 T1:管道统计恒展开——运行时观测无折叠价值,直接三分区渲染):
 * 框架与作用域 / 在线目标 / 管道统计 + 底部「复制诊断信息」动作卡。
 * - V26 R4:框架名等过长值单行省略,单击经 [DiagnosticsDetailSheet]
 *   底部弹层展示完整信息(V39 起经统一 ModevSheet 脚手架,契约单点保证);
 * - 复制快照与页面行同源(同资源标签 + 同格式化)。
 */

/** 弹层详情载荷:标题 + 完整值(可选中/复制);internal 供组件级测试直驱。 */
internal data class DiagnosticsDetail(val title: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
) {
    val connectedCount by viewModel.connectedCount.collectAsStateWithLifecycle()
    val frameworkState by viewModel.frameworkState.collectAsStateWithLifecycle()
    val pipelineStats by viewModel.pipelineStats.collectAsStateWithLifecycle()
    val externalAccess by viewModel.externalAccess.collectAsStateWithLifecycle()
    val listeningAddress by viewModel.listeningAddress.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()
    // V39: MCP 只读态(复制快照消费)。
    val mcpEnabled by viewModel.mcpEnabled.collectAsStateWithLifecycle()
    val mcpPort by viewModel.mcpPort.collectAsStateWithLifecycle()
    val mcpBoundAddress by viewModel.mcpBoundAddress.collectAsStateWithLifecycle()
    val mcpTokenAuth by viewModel.mcpTokenAuth.collectAsStateWithLifecycle()

    // V15:统计数值按应用实际生效 locale 千分位分组(与 stringResource 解析同源)。
    // V16 R5:热切换下 LocalConfiguration 陈旧,一律经包装 Context 解析(effectiveLocale)。
    val locale = rememberEffectiveLocale()
    fun stat(value: Long): String = AppCountFormat.grouped(value, locale)

    // V26 详情弹层状态(hide 动画完成后置空,见 ModalBottomSheet 契约)。
    var detailSheet by remember { mutableStateOf<DiagnosticsDetail?>(null) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 当前绑定地址展示口径:VM 已合并「未绑定回退回环」,UI 零传输层感知。
    val displayAddress = listeningAddress

    // 剪贴板快照:与页面行同源(同资源标签 + 同格式化),统计无论折叠与否全量收录。
    // V38 D2:装配逻辑下沉 DiagnosticsReport(纯函数),与 MCP diagnostics 工具双端同源。
    fun copyDiagnostics() {
        val labels = DiagnosticsReport.Labels(
            frameworkService = context.getString(R.string.settings_framework_service),
            framework = context.getString(R.string.settings_framework),
            connecting = context.getString(R.string.settings_connecting),
            notConnected = context.getString(R.string.settings_not_connected),
            scopedCount = context.getString(R.string.settings_scoped_count),
            scopedCountValue = context.getString(R.string.settings_scoped_count_value),
            frameworkError = context.getString(R.string.settings_framework_error),
            unknownError = context.getString(R.string.unknown_error),
            port = context.getString(R.string.settings_port),
            portValue = context.getString(R.string.settings_port_value),
            portScopeAll = context.getString(R.string.settings_port_scope_all),
            portScopeLocal = context.getString(R.string.settings_port_scope_local),
            onlineTargets = context.getString(R.string.settings_online_targets),
            onlineTargetsValue = context.getString(R.string.settings_online_targets_value),
            mcpServer = context.getString(R.string.mcp_enabled),
            mcpDisabled = context.getString(R.string.mcp_disabled),
            mcpTokenAuth = context.getString(R.string.mcp_token_auth),
            stateOn = context.getString(R.string.mcp_state_on),
            stateOff = context.getString(R.string.mcp_state_off),
            statsReceived = context.getString(R.string.stats_received),
            statsDropped = context.getString(R.string.stats_dropped),
            statsBytesDropped = context.getString(R.string.stats_bytes_dropped),
            statsWriteFailures = context.getString(R.string.stats_write_failures),
            statsTrimmed = context.getString(R.string.stats_trimmed),
            statsRejectedHellos = context.getString(R.string.stats_rejected_hellos),
            statsDroppedRecords = context.getString(R.string.stats_dropped_records),
            statsRejectedRecords = context.getString(R.string.stats_rejected_records),
        )
        val lines = DiagnosticsReport.build(
            snapshot = DiagnosticsReport.Snapshot(
                frameworkState = frameworkState,
                displayAddress = displayAddress,
                port = port,
                externalAccess = externalAccess,
                connectedCount = connectedCount,
                pipelineStats = pipelineStats,
                mcpEnabled = mcpEnabled,
                mcpPort = mcpPort,
                mcpBoundAddress = mcpBoundAddress,
                mcpTokenAuth = mcpTokenAuth,
            ),
            labels = labels,
            formatCount = ::stat,
        )
        clipboard.setText(AnnotatedString(lines.joinToString("\n")))
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.diagnostics_copied)) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.diagnostics_settings_title),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsSection(stringResource(R.string.settings_framework_section)) {
                when (val framework = frameworkState) {
                    is ScopeState.Connecting -> {
                        SettingsInfoItem(
                            icon = Icons.Default.Android,
                            title = stringResource(R.string.settings_framework_service),
                            value = stringResource(R.string.settings_connecting),
                        )
                    }
                    is ScopeState.Ready -> {
                        // V26 R4:框架名+版本可能超长 —— 单行省略,单击弹层看全文。
                        val frameworkValue = "${framework.frameworkName} ${framework.frameworkVersion}"
                        SettingsInfoItem(
                            icon = Icons.Default.Android,
                            title = stringResource(R.string.settings_framework),
                            value = frameworkValue,
                            valueMaxLines = 1,
                            onClick = {
                                detailSheet =
                                    DiagnosticsDetail(
                                        title = context.getString(R.string.settings_framework),
                                        value = frameworkValue,
                                    )
                            },
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Apps,
                            title = stringResource(R.string.settings_scoped_count),
                            value = stringResource(R.string.settings_scoped_count_value, framework.scope.size),
                        )
                    }
                    is ScopeState.Unavailable -> {
                        SettingsInfoItem(
                            icon = Icons.Default.Android,
                            title = stringResource(R.string.settings_framework_service),
                            value = stringResource(R.string.settings_not_connected),
                        )
                        if (framework.reason == ScopeState.UnavailableReason.SERVICE_ERROR) {
                            // V26 R4:错误信息由独立多行文本块收敛为单行省略行,全文入弹层。
                            val errorValue =
                                framework.message?.let { msg ->
                                    stringResource(R.string.settings_framework_error, msg)
                                } ?: stringResource(R.string.unknown_error)
                            SettingsInfoItem(
                                icon = Icons.Default.Warning,
                                title = stringResource(R.string.settings_framework),
                                value = errorValue,
                                valueMaxLines = 1,
                                onClick = {
                                    detailSheet =
                                        DiagnosticsDetail(
                                            title = context.getString(R.string.settings_framework),
                                            value = errorValue,
                                        )
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            // V39:在线目标进程数属运行时观测,留诊断页;外部访问开关/端口编辑迁服务页。
            SettingsSection(stringResource(R.string.settings_server_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Devices,
                    title = stringResource(R.string.settings_online_targets),
                    value = stringResource(R.string.settings_online_targets_value, connectedCount),
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.height(8.dp))
            // 管道统计分区(V21 T5 卡片化;V40 T1 恒展开——7 行统计直渲染,
            // 折叠机制退役:运行时观测无折叠价值;卡片 token 与
            // SettingsSection 同源 surfaceContainerLow)。
            SettingsSection(
                title = stringResource(R.string.settings_stats_section),
                modifier = Modifier.testTag("diagnostics-stats"),
            ) {
                // V21 T5 图标语义化(消除 7× Dns 重复):
                // 接收/丢弃=流方向(SwapHoriz/SwapVert)、写入失败=Warning、
                // 超容量裁剪=ContentCut、通道队列溢出丢弃=FilterAltOff、
                // 拒绝(握手/记录)=Block。
                SettingsInfoItem(
                    icon = Icons.Default.SwapHoriz,
                    title = stringResource(R.string.stats_received),
                    value = stat(pipelineStats.received),
                    modifier = Modifier.testTag("diagnostics-stat-received"),
                )
                SettingsInfoItem(
                    icon = Icons.Default.SwapVert,
                    title = stringResource(R.string.stats_dropped),
                    value = stat(pipelineStats.dropped),
                    modifier = Modifier.testTag("diagnostics-stat-dropped"),
                )
                // 载荷预算丢弃(V45 T1.6):与条数丢弃分列(成因不同,合并即失真)
                SettingsInfoItem(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.stats_bytes_dropped),
                    value = stat(pipelineStats.bytesDropped),
                    modifier = Modifier.testTag("diagnostics-stat-bytes-dropped"),
                )
                SettingsInfoItem(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.stats_write_failures),
                    value = stat(pipelineStats.writeFailures),
                    modifier = Modifier.testTag("diagnostics-stat-write-failures"),
                )
                SettingsInfoItem(
                    icon = Icons.Default.ContentCut,
                    title = stringResource(R.string.stats_trimmed),
                    value = stat(pipelineStats.trimmed),
                    modifier = Modifier.testTag("diagnostics-stat-trimmed"),
                )
                SettingsInfoItem(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.stats_rejected_hellos),
                    value = stat(pipelineStats.channel.rejectedHellos),
                    modifier = Modifier.testTag("diagnostics-stat-rejected-hellos"),
                )
                SettingsInfoItem(
                    icon = Icons.Default.FilterAltOff,
                    title = stringResource(R.string.stats_dropped_records),
                    value = stat(pipelineStats.channel.droppedRecords),
                    modifier = Modifier.testTag("diagnostics-stat-dropped-records"),
                )
                SettingsInfoItem(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.stats_rejected_records),
                    value = stat(pipelineStats.channel.rejectedRecords),
                    modifier = Modifier.testTag("diagnostics-stat-rejected-records"),
                )
            }
            Spacer(Modifier.height(24.dp))
            // 复制诊断信息(V21 T5:标准 M3 动作行卡片化,点击 = 剪贴板快照 +
            // Snackbar 反馈,与记录长按复制同 UX)。
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                shape = MaterialTheme.shapes.medium,
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = {
                        Text(
                            stringResource(R.string.diagnostics_copy),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.ContentCopy,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = ::copyDiagnostics),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    detailSheet?.let { detail ->
        DiagnosticsDetailSheet(
            detail = detail,
            onDismiss = { detailSheet = null },
            onCopy = { value ->
                clipboard.setText(AnnotatedString(value))
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.diagnostics_copied)) }
            },
        )
    }
}

/**
 * 诊断详情底部弹层(V26 R4;V39 收编统一 [ModevSheet] 脚手架):完整值可选中等宽展示 +
 * 复制动作;hide 动画收口、surfaceContainerHigh、imePadding 契约由脚手架单点保证。
 */
@Composable
internal fun DiagnosticsDetailSheet(
    detail: DiagnosticsDetail,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    ModevSheet(
        title = detail.title,
        onDismiss = onDismiss,
        modifier = Modifier.testTag("diagnostics-info-sheet"),
    ) { sheet ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(SheetContentPadding),
        ) {
            SelectionContainer {
                Text(
                    text = detail.value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onCopy(detail.value) },
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.diagnostics_detail_copy),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

