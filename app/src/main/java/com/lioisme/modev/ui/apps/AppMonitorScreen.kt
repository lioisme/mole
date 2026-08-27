package com.lioisme.modev.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.common.StatusSemantic
import com.lioisme.modev.ui.common.connectionStatus
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.common.scopeStatus
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.StatusDot
import com.lioisme.modev.ui.theme.behaviorKindColor
import com.lioisme.modev.ui.theme.kindColor
import com.lioisme.modev.ui.theme.prefsKindColor
import com.lioisme.modev.ui.theme.sqlKindColor
import com.lioisme.modev.ui.theme.statusColor
import com.lioisme.modev.ui.theme.webviewKindColor
import com.lioisme.modev.ui.theme.networkKindColor
import com.lioisme.modev.core.model.NetworkKind
import androidx.compose.material.icons.outlined.FolderOpen

/**
 * 应用监听入口页(V18 单击应用先到本页;V20 整合「查看记录」头部职责;
 * **V23 T5.2 重构**:Hero 头卡(图标 + 名称 + 状态胶囊 + 作用域开关)、
 * 统计概览卡(总数大字 + 类型分布双色比例条,KindColorTokens 语义色)、
 * 功能入口卡(算法记录 / 数据库记录 / 监听设置)+ 操作卡(启动 / 清空记录);
 * 危险操作均居中 AlertDialog 确认(契约保留);死代码 globalSwitch 删除,
 * VM 拆分至 [AppMonitorViewModel](V23 T5.1)。
 */
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
    onOpenListenSettings: () -> Unit,
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
    val listenOverride by viewModel.listenOverride.collectAsStateWithLifecycle()

    val label = appInfo?.label?.takeIf { it.isNotBlank() } ?: viewModel.packageName
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingDanger by remember { mutableStateOf<DangerAction?>(null) }

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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
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
            Spacer(Modifier.height(16.dp))
            StatsCard(
                cryptoCount = cryptoCount,
                dbCount = dbCount,
                behaviorCount = behaviorCount,
                prefsCount = prefsCount,
                webviewCount = webviewCount,
                networkCount = networkCount,
            )
            Spacer(Modifier.height(16.dp))
            SettingsSection(stringResource(R.string.section_monitor_kinds)) {
                EntryRow(
                    icon = { Icon(Icons.Filled.Sensors, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.algorithm_records_title),
                    value = countText(cryptoCount),
                    onClick = onOpenAlgorithm,
                )
                HorizontalDivider16()
                EntryRow(
                    icon = { Icon(Icons.Filled.Storage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.database_records_title),
                    value = countText(dbCount),
                    onClick = onOpenDatabase,
                )
                HorizontalDivider16()
                EntryRow(
                    icon = { Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.behavior_records_title),
                    value = countText(behaviorCount),
                    onClick = onOpenBehavior,
                    modifier = Modifier.testTag("monitor-entry-behavior"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = { Icon(Icons.Filled.DataObject, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.prefs_records_title),
                    value = countText(prefsCount),
                    onClick = onOpenPrefs,
                    modifier = Modifier.testTag("monitor-entry-prefs"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = { Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.webview_records_title),
                    value = countText(webviewCount),
                    onClick = onOpenWebView,
                    modifier = Modifier.testTag("monitor-entry-webview"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = { Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.network_records_title),
                    value = countText(networkCount),
                    onClick = onOpenNetwork,
                    modifier = Modifier.testTag("monitor-entry-network"),
                )
                HorizontalDivider16()
                EntryRow(
                    icon = { Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                    title = stringResource(R.string.listen_settings_entry),
                    value = listenSummary(listenOverride),
                    onClick = onOpenListenSettings,
                    modifier = Modifier.testTag("monitor-entry-listen-settings"),
                )            }
            Spacer(Modifier.height(16.dp))
            SettingsSection(stringResource(R.string.app_monitor_actions_section)) {
                // 仅存在启动入口时展示,解析完成前不渲染防闪烁(V16 R4)。
                if (launchable == true) {
                    EntryRow(
                        icon = { Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) },
                        title = stringResource(R.string.apps_launch),
                        value = "",
                        onClick = { onLaunch(viewModel.packageName) },
                        modifier = Modifier.testTag("app-launch"),
                    )
                    HorizontalDivider16()
                }
                DangerRow(
                    text = stringResource(R.string.detail_clear_records),
                    onClick = { pendingDanger = DangerAction.CLEAR_LOGS },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingDanger?.let { danger ->
        AlertDialog(
            onDismissRequest = { pendingDanger = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
 * 状态胶囊:圆点 + 文案的 tonal pill(StatusColorTokens AA 语义色)。
 * V25 视觉整改:点↔文字 6dp 间距(对齐全 App 点标惯例:AppsScreen 行内 /
 * 统计卡图例),内边距对齐 TimelineHeader 惯例(horizontal 10 / vertical 3),
 * 消除旧版 start6/end10 非对称补偿与 0dp 挤贴。
 */
@Composable
private fun StatusPill(semantic: StatusSemantic) {
    val color = statusColor(semantic.kind)
    val label = stringResource(semantic.labelRes)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(kind = semantic.kind, label = label)
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
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.GppGood,
                null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

/**
 * 统计概览卡(V23 T5.2;V28 四段比例条;V30 五段):总记录数大字(locale 紧凑格式)+
 * 类型分布五色比例条(算法 = CIPHER / 数据库 = QUERY / 行为 = FILE_WRITE /
 * 偏好 = PUT / 网络 = SSL_CONTEXT,KindColorTokens 同源)+ 图例计数;
 * 零记录时隐藏比例条与图例(空卡仅大数字,不显零除噪)。
 */
@Composable
private fun StatsCard(
    cryptoCount: Long,
    dbCount: Long,
    behaviorCount: Long,
    prefsCount: Long,
    webviewCount: Long,
    networkCount: Long,
) {
    val total = cryptoCount + dbCount + behaviorCount + prefsCount + webviewCount + networkCount
    val cryptoColor = kindColor(CryptoKind.CIPHER)
    val dbColor = sqlKindColor(SqlKind.QUERY)
    val behaviorColor = behaviorKindColor(BehaviorKind.FILE_WRITE)
    val prefsColor = prefsKindColor(PrefsKind.PUT)
    val webviewColor = webviewKindColor(WebViewKind.PAGE_LOAD)
    val networkColor = networkKindColor(NetworkKind.SSL_CONTEXT)
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
                val cryptoWeight = (cryptoCount.toFloat() / total).coerceAtLeast(0.02f)
                val dbWeight = (dbCount.toFloat() / total).coerceAtLeast(0.02f)
                val behaviorWeight = (behaviorCount.toFloat() / total).coerceAtLeast(0.02f)
                val prefsWeight = (prefsCount.toFloat() / total).coerceAtLeast(0.02f)
                val webviewWeight = (webviewCount.toFloat() / total).coerceAtLeast(0.02f)
                val networkWeight = (networkCount.toFloat() / total).coerceAtLeast(0.02f)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(5.dp),
                        ),
                ) {
                    Box(
                        Modifier
                            .weight(cryptoWeight)
                            .fillMaxSize()
                            .background(color = cryptoColor, shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp)),
                    )
                    Box(
                        Modifier
                            .weight(dbWeight)
                            .fillMaxSize()
                            .background(color = dbColor),
                    )
                    Box(
                        Modifier
                            .weight(behaviorWeight)
                            .fillMaxSize()
                            .background(color = behaviorColor),
                    )
                    Box(
                        Modifier
                            .weight(prefsWeight)
                            .fillMaxSize()
                            .background(color = prefsColor),
                    )
                    Box(
                        Modifier
                            .weight(webviewWeight)
                            .fillMaxSize()
                            .background(color = webviewColor),
                    )
                    Box(
                        Modifier
                            .weight(networkWeight)
                            .fillMaxSize()
                            .background(color = networkColor, shape = RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp)),
                    )
                }
                Spacer(Modifier.height(10.dp))
                LegendRow(color = cryptoColor, label = stringResource(R.string.filter_type_crypto), count = cryptoCount)
                LegendRow(color = dbColor, label = stringResource(R.string.filter_type_database), count = dbCount)
                LegendRow(color = behaviorColor, label = stringResource(R.string.filter_type_behavior), count = behaviorCount)
                LegendRow(color = prefsColor, label = stringResource(R.string.filter_type_prefs), count = prefsCount)
                LegendRow(color = webviewColor, label = stringResource(R.string.filter_type_webview), count = webviewCount)
                LegendRow(color = networkColor, label = stringResource(R.string.filter_type_network), count = networkCount)
            }
        }
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String, count: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(8.dp).background(color = color, shape = CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
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
    icon: @Composable () -> Unit,
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
        leadingContent = icon,
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
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/** 危险操作行(error 语义:Delete 图标 error 色居左,点击唤起居中确认)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DangerRow(
    text: String,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        },
        leadingContent = {
            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun HorizontalDivider16() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun countText(count: Long): String {
    val locale = rememberEffectiveLocale()
    return AppCountFormat.compact(count, locale)
}

/**
 * 「监听设置」摘要(V24 G3):未定制 → 「使用默认配置」;
 * 有自定义 → 「N 项已自定义」(N = 总开关偏差数 + 类别偏差数)。
 */
@Composable
private fun listenSummary(override: AppListenOverride): String {
    if (override.isDefault) {
        return stringResource(R.string.listen_summary_default)
    }
    val n = (if (override.cryptoListen != null) 1 else 0) +
        (if (override.dbListen != null) 1 else 0) +
        (if (override.behaviorListen != null) 1 else 0) +
        (if (override.prefsListen != null) 1 else 0) +
        override.kinds.size
    // 复数契约 6:计数文案走 plurals(one/other;zh 仅 other)。
    return pluralStringResource(R.plurals.listen_settings_custom_n, n, n)
}
