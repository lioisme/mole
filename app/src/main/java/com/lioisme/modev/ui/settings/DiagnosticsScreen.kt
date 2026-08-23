package com.lioisme.modev.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.apps.AppCountFormat
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import kotlinx.coroutines.launch

/**
 * 服务与诊断页(V10 设置分级;V19 T8 分区化;V21 T5 重新设计):
 * 三分区——框架与作用域 / 通信服务 / 管道统计(折叠卡,默认收起,
 * AnimatedVisibility,状态 rememberSaveable;统计行图标语义分组:
 * 拒绝类 Block / 流向 SwapHoriz·SwapVert)+ 底部「复制诊断信息」
 * 动作卡(剪贴板快照 + Snackbar 反馈,与记录长按复制同 UX)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
) {
    val connectedCount by viewModel.connectedCount.collectAsStateWithLifecycle()
    val frameworkState by viewModel.frameworkState.collectAsStateWithLifecycle()
    val pipelineStats by viewModel.pipelineStats.collectAsStateWithLifecycle()

    // V15:统计数值按应用实际生效 locale 千分位分组(与 stringResource 解析同源)。
    // V16 R5:热切换下 LocalConfiguration 陈旧,一律经包装 Context 解析(effectiveLocale)。
    val locale = rememberEffectiveLocale()
    fun stat(value: Long): String = AppCountFormat.grouped(value, locale)

    // 管道统计折叠态:跨重建存活,展开/收起不因重组复位。
    var statsExpanded by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 剪贴板快照:与页面行同源(同资源标签 + 同格式化),统计无论折叠与否全量收录。
    fun copyDiagnostics() {
        val lines = buildList {
            fun line(label: String, value: String) = add("$label: $value")
            when (val framework = frameworkState) {
                is ScopeState.Connecting -> {
                    line(context.getString(R.string.settings_framework_service), context.getString(R.string.settings_connecting))
                }
                is ScopeState.Ready -> {
                    line(context.getString(R.string.settings_framework), "${framework.frameworkName} ${framework.frameworkVersion}")
                    line(context.getString(R.string.settings_scoped_count), context.getString(R.string.settings_scoped_count_value, framework.scope.size))
                }
                is ScopeState.Unavailable -> {
                    line(context.getString(R.string.settings_framework_service), context.getString(R.string.settings_not_connected))
                    if (framework.reason == ScopeState.UnavailableReason.SERVICE_ERROR) {
                        add(
                            framework.message?.let { context.getString(R.string.settings_framework_error, it) }
                                ?: context.getString(R.string.unknown_error),
                        )
                    }
                }
            }
            line(context.getString(R.string.settings_port), context.getString(R.string.settings_port_value, viewModel.port))
            line(context.getString(R.string.settings_online_targets), context.getString(R.string.settings_online_targets_value, connectedCount))
            line(context.getString(R.string.stats_received), stat(pipelineStats.received))
            line(context.getString(R.string.stats_dropped), stat(pipelineStats.dropped))
            line(context.getString(R.string.stats_write_failures), stat(pipelineStats.writeFailures))
            line(context.getString(R.string.stats_trimmed), stat(pipelineStats.trimmed))
            line(context.getString(R.string.stats_rejected_hellos), stat(pipelineStats.channel.rejectedHellos))
            line(context.getString(R.string.stats_dropped_records), stat(pipelineStats.channel.droppedRecords))
            line(context.getString(R.string.stats_rejected_records), stat(pipelineStats.channel.rejectedRecords))
        }
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
            Spacer(Modifier.padding(top = 8.dp))
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
                        SettingsInfoItem(
                            icon = Icons.Default.Android,
                            title = stringResource(R.string.settings_framework),
                            value = "${framework.frameworkName} ${framework.frameworkVersion}",
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
                            Text(
                                framework.message?.let { msg ->
                                    stringResource(R.string.settings_framework_error, msg)
                                } ?: stringResource(R.string.unknown_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_server_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_port),
                    // M18:传输常量经 VM 暴露,UI 零传输层 import
                    value = stringResource(R.string.settings_port_value, viewModel.port),
                )
                SettingsInfoItem(
                    icon = Icons.Default.Devices,
                    title = stringResource(R.string.settings_online_targets),
                    value = stringResource(R.string.settings_online_targets_value, connectedCount),
                )
            }
            Spacer(Modifier.height(24.dp))
            // 管道统计分区(V21 T5:卡片化,与 Data 页分区视觉对齐;折叠头行
            // 即分区标题——标题唯一,不与 Section 重复;卡片 token 与
            // SettingsSection 同源 surfaceContainerLow)。
            Spacer(Modifier.padding(top = 8.dp))
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                shape = MaterialTheme.shapes.medium,
            ) {
                StatsToggleRow(expanded = statsExpanded, onClick = { statsExpanded = !statsExpanded })
                AnimatedVisibility(visible = statsExpanded) {
                    Column {
                        // V21 T5 图标语义化(消除 7× Dns 重复):
                        // 接收/丢弃=流方向(SwapHoriz/SwapVert)、写入失败=Warning、
                        // 超容量裁剪=ContentCut、通道队列溢出丢弃=FilterAltOff、
                        // 拒绝(握手/记录)=Block。
                        SettingsInfoItem(
                            icon = Icons.Default.SwapHoriz,
                            title = stringResource(R.string.stats_received),
                            value = stat(pipelineStats.received),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.SwapVert,
                            title = stringResource(R.string.stats_dropped),
                            value = stat(pipelineStats.dropped),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Warning,
                            title = stringResource(R.string.stats_write_failures),
                            value = stat(pipelineStats.writeFailures),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.ContentCut,
                            title = stringResource(R.string.stats_trimmed),
                            value = stat(pipelineStats.trimmed),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Block,
                            title = stringResource(R.string.stats_rejected_hellos),
                            value = stat(pipelineStats.channel.rejectedHellos),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.FilterAltOff,
                            title = stringResource(R.string.stats_dropped_records),
                            value = stat(pipelineStats.channel.droppedRecords),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Block,
                            title = stringResource(R.string.stats_rejected_records),
                            value = stat(pipelineStats.channel.rejectedRecords),
                        )
                    }
                }
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
}

/**
 * 管道统计折叠头行(V9;V19 T6 矩阵 M9:M3 [ListItem] 承载):
 * 图标 + 标题 + 方向指示图标(展开↑/收起↓),
 * 与详情页字节区块「展开/收起」文案同语义(contentDescription 复用)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsToggleRow(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                stringResource(R.string.settings_stats_section),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.section_collapse else R.string.section_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}