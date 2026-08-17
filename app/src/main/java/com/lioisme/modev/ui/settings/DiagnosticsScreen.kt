package com.lioisme.modev.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsSection

/**
 * 服务与诊断页(V10 设置分级,自设置页「数据与服务」入口):
 * 框架与作用域 + 通信服务只读信息(SettingsInfoItem)+ 管道统计折叠卡
 * (默认收起,AnimatedVisibility,状态 rememberSaveable)。
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

    // 管道统计折叠态(跨重建存活:展开/收起不因重组复位)。
    var statsExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.diagnostics_settings_title),
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
            SettingsSection(stringResource(R.string.settings_service_section)) {
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
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_port),
                    value = stringResource(R.string.settings_port_value, WireProtocol.PORT),
                )
                SettingsInfoItem(
                    icon = Icons.Default.Devices,
                    title = stringResource(R.string.settings_online_targets),
                    value = stringResource(R.string.settings_online_targets_value, connectedCount),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                StatsToggleRow(expanded = statsExpanded, onClick = { statsExpanded = !statsExpanded })
                AnimatedVisibility(visible = statsExpanded) {
                    Column {
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_received),
                            value = pipelineStats.received.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_dropped),
                            value = pipelineStats.dropped.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_write_failures),
                            value = pipelineStats.writeFailures.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_trimmed),
                            value = pipelineStats.trimmed.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_rejected_hellos),
                            value = pipelineStats.channel.rejectedHellos.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_dropped_records),
                            value = pipelineStats.channel.droppedRecords.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_rejected_records),
                            value = pipelineStats.channel.rejectedRecords.toString(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 管道统计折叠头行(V9):图标 + 标题 + 方向指示图标(展开↑/收起↓),
 * 与详情页字节区块「展开/收起」文案同语义(contentDescription 复用)。
 */
@Composable
private fun StatsToggleRow(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.settings_stats_section),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(if (expanded) R.string.section_collapse else R.string.section_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}