package com.lioisme.modev.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.common.StatusSemantic
import com.lioisme.modev.ui.common.connectionStatus
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.common.scopeStatus
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.ModevConfirmDialog
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.theme.Dimens
import com.lioisme.modev.ui.theme.behaviorKindColor
import com.lioisme.modev.ui.theme.deviceEnvKindColor
import com.lioisme.modev.ui.theme.kindColor
import com.lioisme.modev.ui.theme.networkKindColor
import com.lioisme.modev.ui.theme.prefsKindColor
import com.lioisme.modev.ui.theme.processKindColor
import com.lioisme.modev.ui.theme.sqlKindColor
import com.lioisme.modev.ui.theme.statusColor
import com.lioisme.modev.ui.theme.Tokens
import com.lioisme.modev.ui.theme.systemKindColor
import com.lioisme.modev.ui.theme.uiInteractionKindColor
import com.lioisme.modev.ui.theme.webviewKindColor

/**
 * 应用监听入口页(V18 单击应用先到本页;V23 T5.2 Hero/统计/入口卡;
 * **V37 T3.1/T3.2 双域重构**:「启动 / 清空记录」上移 Appbar actions;
 * 正文 = Hero 卡 + 双段统计卡(监听域/功能域各自比例条)+「监听」区
 * (监听设置 + 6 记录列表)+「功能」区(功能设置 + 4 功能记录列表);
 * 危险操作居中 AlertDialog 确认契约保留)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMonitorScreen(
    viewModel: AppMonitorViewModel,
    onBack: () -> Unit,
    onOpenAlgorithm: () -> Unit,
    onOpenDatabase: () -> Unit,
    onOpenBehavior: () -> Unit,
    onOpenPrefs: () -> Unit,
    onOpenWebView: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenDeviceEnv: () -> Unit,
    onOpenUiInteraction: () -> Unit,
    onOpenSystem: () -> Unit,
    onOpenProcessControl: () -> Unit,
    onOpenListenSettings: () -> Unit,
    onOpenFeatureSettings: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    val appInfo by viewModel.appInfo.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val scopeState by viewModel.scopeState.collectAsStateWithLifecycle()
    val scoped by viewModel.scoped.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val launchable by viewModel.launchable.collectAsStateWithLifecycle()
    val cryptoCount by viewModel.cryptoCount.collectAsStateWithLifecycle()
    val dbCount by viewModel.dbCount.collectAsStateWithLifecycle()
    val behaviorCount by viewModel.behaviorCount.collectAsStateWithLifecycle()
    val prefsCount by viewModel.prefsCount.collectAsStateWithLifecycle()
    val webviewCount by viewModel.webviewCount.collectAsStateWithLifecycle()
    val networkCount by viewModel.networkCount.collectAsStateWithLifecycle()
    val deviceEnvCount by viewModel.deviceEnvCount.collectAsStateWithLifecycle()
    val uiInteractionCount by viewModel.uiInteractionCount.collectAsStateWithLifecycle()
    val systemCount by viewModel.systemCount.collectAsStateWithLifecycle()
    val processControlCount by viewModel.processControlCount.collectAsStateWithLifecycle()
    val listenOverride by viewModel.listenOverride.collectAsStateWithLifecycle()

    val label = appInfo?.label?.takeIf { it.isNotBlank() } ?: viewModel.packageName
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmClearLogs by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray())
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = label,
                onBack = onBack,
                actions = {
                    // V37 T3.2:启动/清空上移 Appbar(V40 T3:启动恒最右,清空居左;
                    // 仅存在启动入口时展示启动,防闪烁契约保留)
                    TooltipAction(
                        tooltip = stringResource(R.string.detail_clear_records),
                        testTag = "app-clear-records",
                    ) { confirmClearLogs = true }
                    if (launchable == true) {
                        TooltipAction(
                            tooltip = stringResource(R.string.apps_launch),
                            testTag = "app-launch",
                        ) { onLaunch(viewModel.packageName) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.pagePadding),
        ) {
            HeroCard(
                label = label,
                packageName = viewModel.packageName,
                connected = connected,
                scopeState = scopeState,
                scoped = scoped,
                scopePending = pending,
                scopeReady = scopeState is ScopeState.Ready,
                onToggleScope = viewModel::toggleScope,
            )
            Spacer(Modifier.height(Dimens.sectionGapDense))
            StatsCard(
                listenCounts = listOf(
                    stringResource(R.string.filter_type_crypto) to
                        (kindColor(CryptoKind.CIPHER) to cryptoCount),
                    stringResource(R.string.filter_type_database) to
                        (sqlKindColor(SqlKind.QUERY) to dbCount),
                    stringResource(R.string.filter_type_behavior) to
                        (behaviorKindColor(BehaviorKind.FILE_WRITE) to behaviorCount),
                    stringResource(R.string.filter_type_prefs) to
                        (prefsKindColor(PrefsKind.PUT) to prefsCount),
                    stringResource(R.string.filter_type_webview) to
                        (webviewKindColor(WebViewKind.PAGE_LOAD) to webviewCount),
                    stringResource(R.string.filter_type_system) to
                        (systemKindColor(SystemKind.APP_INFO) to systemCount),
                ),
                featureCounts = listOf(
                    stringResource(R.string.filter_type_ui_interaction) to
                        (uiInteractionKindColor(UiInteractionKind.DIALOG_KEYWORD) to uiInteractionCount),
                    stringResource(R.string.filter_type_network) to
                        (networkKindColor(NetworkKind.SSL_CONTEXT) to networkCount),
                    stringResource(R.string.filter_type_device_env) to
                        (deviceEnvKindColor(DeviceEnvKind.ROOT_FILE) to deviceEnvCount),
                    stringResource(R.string.filter_type_process_control) to
                        (processKindColor(ProcessKind.EXIT_INTERCEPT) to processControlCount),
                ),
            )
            Spacer(Modifier.height(Dimens.sectionGapDense))
            SettingsSection(stringResource(R.string.monitor_section_listen)) {
                EntryRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.listen_settings_entry),
                    value = listenSummary(listenOverride, CapabilityDomain.LISTEN),
                    onClick = onOpenListenSettings,
                    modifier = Modifier.testTag("monitor-entry-listen-settings"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.Sensors,
                    title = stringResource(R.string.algorithm_records_title),
                    value = countText(cryptoCount),
                    onClick = onOpenAlgorithm,
                    // V45 T3.7:与其余 8 个入口行同构(此前只有这两行无 tag,插桩只能按文本反查)
                    modifier = Modifier.testTag("monitor-entry-algorithm"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.database_records_title),
                    value = countText(dbCount),
                    onClick = onOpenDatabase,
                    modifier = Modifier.testTag("monitor-entry-database"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.behavior_records_title),
                    value = countText(behaviorCount),
                    onClick = onOpenBehavior,
                    modifier = Modifier.testTag("monitor-entry-behavior"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.prefs_records_title),
                    value = countText(prefsCount),
                    onClick = onOpenPrefs,
                    modifier = Modifier.testTag("monitor-entry-prefs"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.webview_records_title),
                    value = countText(webviewCount),
                    onClick = onOpenWebView,
                    modifier = Modifier.testTag("monitor-entry-webview"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.system_records_title),
                    value = countText(systemCount),
                    onClick = onOpenSystem,
                    modifier = Modifier.testTag("monitor-entry-system"),
                )
            }
            Spacer(Modifier.height(Dimens.sectionGapDense))
            SettingsSection(stringResource(R.string.monitor_section_feature)) {
                EntryRow(
                    icon = Icons.Filled.Shield,
                    title = stringResource(R.string.feature_settings_entry),
                    value = listenSummary(listenOverride, CapabilityDomain.FEATURE),
                    onClick = onOpenFeatureSettings,
                    modifier = Modifier.testTag("monitor-entry-feature-settings"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.Warning,
                    title = stringResource(R.string.ui_interaction_records_title),
                    value = countText(uiInteractionCount),
                    onClick = onOpenUiInteraction,
                    modifier = Modifier.testTag("monitor-entry-ui-interaction"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.Dns,
                    title = stringResource(R.string.network_records_title),
                    value = countText(networkCount),
                    onClick = onOpenNetwork,
                    modifier = Modifier.testTag("monitor-entry-network"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.Memory,
                    title = stringResource(R.string.device_records_title),
                    value = countText(deviceEnvCount),
                    onClick = onOpenDeviceEnv,
                    modifier = Modifier.testTag("monitor-entry-device-env"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = Icons.Filled.GppBad,
                    title = stringResource(R.string.process_control_records_title),
                    value = countText(processControlCount),
                    onClick = onOpenProcessControl,
                    modifier = Modifier.testTag("monitor-entry-process-control"),
                )
            }
            Spacer(Modifier.height(Dimens.pageBottom))
        }
    }

    if (confirmClearLogs) {
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(R.string.danger_clear_logs_msg, viewModel.packageName),
            onConfirm = {
                viewModel.clearLogs()
                confirmClearLogs = false
            },
            onDismiss = { confirmClearLogs = false },
        )
    }
}

/** Appbar 图标动作(TipIconButton 自带 tooltip + 长按振动;testTag 锚定)。 */
@Composable
private fun TooltipAction(
    tooltip: String,
    testTag: String,
    onClick: () -> Unit,
) {
    TipIconButton(tooltip = tooltip, onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(
            when (testTag) {
                "app-launch" -> Icons.Filled.PlayArrow
                else -> Icons.Filled.Delete
            },
            contentDescription = tooltip,
        )
    }
}

/**
 * Hero 头卡(V23 T5.2):AppIcon 56dp + 名称(titleLarge Medium)+ 包名 +
 * 连接/作用域双状态胶囊([connectionStatus]/[scopeStatus] 单一语义源)+
 * 作用域 Switch 行(pending 态禁用;框架不可用时禁用并保持原状)。
 */
@Composable
private fun HeroCard(
    label: String,
    packageName: String,
    connected: Boolean,
    scopeState: ScopeState,
    scoped: Boolean,
    scopePending: Boolean,
    scopeReady: Boolean,
    onToggleScope: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(bottom = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName = packageName, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.padding(horizontal = Dimens.pagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val conn = connectionStatus(connected)
                StatusPill(conn)
                val scope = scopeStatus(scopeState, packageName)
                StatusPill(scope)
            }
            Spacer(Modifier.height(4.dp))
            ScopeToggleRow(
                checked = scoped,
                enabled = scopeReady && !scopePending,
                onCheckedChange = { onToggleScope() },
            )
        }
    }
}

/**
 * 状态胶囊(V40 T5.2):圆点 = 标准 M3 [Badge](自研 StatusDot 退役)+
 * 文案的 tonal Surface(StatusColorTokens AA 语义色);语义经 contentDescription。
 */
@Composable
private fun StatusPill(semantic: StatusSemantic) {
    val color = statusColor(semantic.kind)
    val label = stringResource(semantic.labelRes)
    Surface(shape = CircleShape, color = color.copy(alpha = Tokens.pillContainerAlpha)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(
                containerColor = color,
                modifier = Modifier.semantics { contentDescription = label },
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

/** 作用域开关行(契约 13:ListItem 显式容器色 = 所在卡片 Low;整行 toggleable 单语义)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScopeToggleRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                stringResource(R.string.app_monitor_scope_row),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = Tokens.disabledContentAlpha),
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.GppGood,
                null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = Tokens.disabledContentAlpha),
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .testTag("app-scope-switch"),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app-scope-row")
            .semantics { role = Role.Switch }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

/**
 * 双段统计卡(V37 T3.3):监听域/功能域各自「计数 + 比例条 + FlowRow 图例」,
 * 替代 V33 十段单条不可读形态;零记录域隐藏比例条与图例。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsCard(
    listenCounts: List<Pair<String, Pair<Color, Long>>>,
    featureCounts: List<Pair<String, Pair<Color, Long>>>,
) {
    val listenTotal = listenCounts.sumOf { it.second.second }
    val featureTotal = featureCounts.sumOf { it.second.second }
    val total = listenTotal + featureTotal
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app-stats-card"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                countText(total),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.monitor_records_caption),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (total > 0) {
                Spacer(Modifier.height(14.dp))
                DomainBlock(
                    label = stringResource(R.string.monitor_domain_listen),
                    counts = listenCounts,
                    total = listenTotal,
                )
                Spacer(Modifier.height(12.dp))
                DomainBlock(
                    label = stringResource(R.string.monitor_domain_feature),
                    counts = featureCounts,
                    total = featureTotal,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomainBlock(
    label: String,
    counts: List<Pair<String, Pair<Color, Long>>>,
    total: Long,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                countText(total),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (total > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(4.dp),
                    ),
            ) {
                counts.filter { it.second.second > 0 }.forEachIndexed { index, (_, pair) ->
                    val (color, count) = pair
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxSize()
                            .background(
                                color = color,
                                shape = when {
                                    counts.size == 1 -> RoundedCornerShape(4.dp)
                                    index == 0 -> RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                                    index == counts.count { it.second.second > 0 } - 1 ->
                                        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                    else -> RoundedCornerShape(0.dp)
                                },
                            ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            counts.forEach { (name, pair) ->
                LegendItem(color = pair.first, label = name, count = pair.second)
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, count: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Box(Modifier.size(8.dp).background(color = color, shape = CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            countText(count),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 入口行(卡片内 M3 ListItem,契约 13 显式 Low 容器色;尾部值 + chevron)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Tokens.chevronAlpha),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun countText(count: Long): String {
    val locale = rememberEffectiveLocale()
    return AppCountFormat.compact(count, locale)
}

/**
 * 「设置」摘要(V24 G3;V37 C8 计数收敛 [AppListenOverride.deviationCount];
 * **V48-B3 按域计数**):两页入口恒各报自己那一域的偏差数 —— 编辑页早已用
 * `deviationCountFor(domain)`,而入口仍读跨域总数,导致「只定制了功能」时
 * 监听入口也显示同一个数字(两行同数 = 用户以为它们管的是同一批开关)。
 */
@Composable
private fun listenSummary(override: AppListenOverride, domain: CapabilityDomain): String {
    val n = override.deviationCountFor(domain)
    if (n == 0) {
        return stringResource(R.string.listen_summary_default)
    }
    // 复数契约 6:计数文案走 plurals(one/other;zh 仅 other)。
    return pluralStringResource(R.plurals.listen_settings_custom_n, n, n)
}
