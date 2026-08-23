package com.lioisme.modev.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.domain.scope.ScopeChangeResult
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.ui.common.UiMessage
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.StatusDot
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.theme.StatusKind
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用监听入口页(V18 单击应用先到本页;V20 整合「查看记录」头部职责并退役该界面):
 * - 顶部:AppHeaderRow(应用名 + 包名 + 双状态点 + 一键启动 + 更多菜单 =
 *   移出作用域 / 清空该应用记录,均居中 AlertDialog 确认);
 * - 入口卡片:算法记录 / 数据库记录,单应用记录列表,副标题 = 每类型计数。
 * 单一事实源 = AppCatalogRepository(应用信息)+ SettingsRepository.globalSwitch
 * (监听总开关态)+ ScopeRepository / ConnectionMonitor(状态角标)+ LogRepository(计数)。
 */
@Composable
fun AppMonitorScreen(
    viewModel: AppMonitorViewModel,
    onBack: () -> Unit,
    onOpenAlgorithm: () -> Unit,
    onOpenDatabase: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    val appInfo by viewModel.appInfo.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val scopeState by viewModel.scopeState.collectAsStateWithLifecycle()
    val launchable by viewModel.launchable.collectAsStateWithLifecycle()
    val cryptoCount by viewModel.cryptoCount.collectAsStateWithLifecycle()
    val dbCount by viewModel.dbCount.collectAsStateWithLifecycle()
    val switch by viewModel.globalSwitch.collectAsStateWithLifecycle()

    val label = appInfo?.label?.takeIf { it.isNotBlank() } ?: viewModel.packageName
    val scoped = (scopeState as? ScopeState.Ready)?.isScoped(viewModel.packageName) == true

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
                .verticalScroll(rememberScrollState()),
        ) {
            AppHeaderRow(
                label = label,
                packageName = viewModel.packageName,
                connected = connected,
                scopeState = scopeState,
                scoped = scoped,
                // 仅存在启动入口时展示「启动」按钮,解析完成前不渲染,防闪烁。
                onLaunch =
                    if (launchable == true) {
                        { onLaunch(viewModel.packageName) }
                    } else {
                        null
                    },
                // 应用级危险操作(V20 自「查看记录」头部迁入本页语境)。
                onRemoveScope = if (scoped) { { pendingDanger = DangerAction.REMOVE_SCOPE } } else null,
                onClearLogs = { pendingDanger = DangerAction.CLEAR_LOGS },
            )
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            SettingsSection(stringResource(R.string.section_monitor_kinds)) {
                SettingsItem(
                    icon = Icons.Filled.Sensors,
                    title = stringResource(R.string.algorithm_records_title),
                    subtitle = countText(cryptoCount),
                    onClick = onOpenAlgorithm,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.database_records_title),
                    subtitle = countText(dbCount),
                    onClick = onOpenDatabase,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
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

/**
 * 入口页头部(V20 自「查看记录」页迁入;V19 T6 矩阵 M7:M3 [ListItem] 结构承载):
 * leading AppIcon 40dp + headline 应用名(titleMedium)+ supporting 包名
 * + 状态角标(连接/作用域双圆点)+ trailing 「启动」按钮 + 「更多」菜单
 * (移出作用域 / 清空该应用记录,均经居中 AlertDialog 确认)。
 */
@Composable
private fun AppHeaderRow(
    label: String,
    packageName: String,
    connected: Boolean,
    scopeState: ScopeState,
    scoped: Boolean,
    onLaunch: (() -> Unit)? = null,
    onRemoveScope: (() -> Unit)? = null,
    onClearLogs: (() -> Unit)? = null,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                StatusDot(
                    kind = if (connected) StatusKind.ONLINE else StatusKind.OFFLINE,
                    label =
                        if (connected) {
                            stringResource(R.string.online)
                        } else {
                            stringResource(R.string.offline)
                        },
                )
                Spacer(Modifier.width(6.dp))
                StatusDot(
                    kind =
                        when (scopeState) {
                            is ScopeState.Ready ->
                                if (scoped) StatusKind.SCOPED else StatusKind.UNSCOPED
                            ScopeState.Connecting -> StatusKind.CONNECTING
                            is ScopeState.Unavailable ->
                                if (scopeState.reason == ScopeState.UnavailableReason.NO_SERVICE) {
                                    StatusKind.ERROR
                                } else {
                                    StatusKind.UNSCOPED
                                }
                        },
                    label =
                        when (scopeState) {
                            is ScopeState.Ready ->
                                if (scoped) stringResource(R.string.detail_listening) else stringResource(R.string.detail_not_listening)
                            ScopeState.Connecting -> stringResource(R.string.detail_reading_scope)
                            is ScopeState.Unavailable ->
                                if (scopeState.reason == ScopeState.UnavailableReason.NO_SERVICE) {
                                    stringResource(R.string.detail_framework_no_service)
                                } else {
                                    stringResource(R.string.detail_not_listening)
                                }
                        },
                )
            }
        },
        leadingContent = { AppIcon(packageName = packageName, size = 40.dp) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onLaunch != null) {
                    IconButton(
                        onClick = onLaunch,
                        // 测试锚点:一键启动按钮。
                        modifier = Modifier.testTag("app-launch"),
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.apps_launch),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onRemoveScope != null || onClearLogs != null) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        TipIconButton(tooltip = stringResource(R.string.record_more), onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.record_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (onRemoveScope != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_remove_scope), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.GppBad,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onRemoveScope()
                                    },
                                )
                            }
                            if (onClearLogs != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.detail_clear_records), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onClearLogs()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun countText(count: Long): String {
    val locale = rememberEffectiveLocale()
    return AppCountFormat.compact(count, locale)
}

/**
 * 应用监听入口页数据(V18;V20 整合「查看记录」头部职责):
 * 应用信息 / 在线 / 作用域 / 启动入口 / 每类型记录计数 / 监听总开关态
 * + 应用级操作:移出作用域 + 清空该应用记录。
 */
class AppMonitorViewModel(
    val packageName: String,
    private val catalog: AppCatalogRepository,
    server: ConnectionMonitor,
    private val scopeRepository: ScopeRepository,
    private val logs: LogRepository,
    settings: SettingsRepository,
) : ViewModel() {

    /** 应用名+图标信息。*/
    val appInfo: StateFlow<AppCatalogRepository.AppInfo?> = catalog.apps
        .map { list -> list.firstOrNull { it.packageName == packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connected: StateFlow<Boolean> = server.connectedPackagesFlow
        .map { set -> packageName in set }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scopeState: StateFlow<ScopeState> = scopeRepository.state

    private val launchableState = MutableStateFlow<Boolean?>(null)

    /** 启动入口解析结果(null = 解析中,V16 R4 惰性单次解析 + 缓存)。*/
    val launchable: StateFlow<Boolean?> = launchableState

    init {
        viewModelScope.launch {
            launchableState.value = catalog.resolveLaunchable(packageName)
        }
    }

    /** 该应用算法记录数(入口副标题,AppCountFormat 紧凑格式)。*/
    val cryptoCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.CRYPTO)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 该应用数据库记录数(入口副标题,AppCountFormat 紧凑格式)。*/
    val dbCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.DATABASE)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 全局监听配置(算法/数据库总开关态,入口副标题语义来源)。*/
    val globalSwitch: StateFlow<HookSwitch> = settings.globalSwitch

    /** 一次性提示(移出作用域 / 清空记录结果),由 UI 收集发 Snackbar。*/
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)

    fun clearLogs() {
        viewModelScope.launch {
            logs.clear(packageName)
            messages.emit(UiMessage(R.string.apps_records_cleared, listOf(packageName)))
        }
    }

    /** 移出作用域,等价于 LSPosed 作用域取消勾选。*/
    fun removeScope() {
        viewModelScope.launch {
            val result = scopeRepository.remove(packageName)
            messages.emit(
                when (result) {
                    is ScopeChangeResult.ScopeRemoved ->
                        UiMessage(R.string.apps_scope_removed, listOf(packageName))
                    is ScopeChangeResult.ScopeRequested ->
                        UiMessage(R.string.apps_scope_requested, listOf(packageName))
                    is ScopeChangeResult.Failed -> scopeFailedMessage(packageName, result)
                }
            )
        }
    }
}

/** M17:失败错误码 → 本地化文案,domain 零硬编码,Snackbar 语义由 UI 层承担。*/
private fun scopeFailedMessage(packageName: String, failed: ScopeChangeResult.Failed): UiMessage =
    when (failed) {
        ScopeChangeResult.Failed.FrameworkNotReady ->
            UiMessage(R.string.apps_scope_failed_not_ready)
        ScopeChangeResult.Failed.ServiceNotConnected ->
            UiMessage(R.string.apps_scope_failed_service)
        ScopeChangeResult.Failed.UserRejected ->
            UiMessage(R.string.apps_scope_failed_rejected)
        ScopeChangeResult.Failed.Timeout ->
            UiMessage(R.string.apps_scope_failed_timeout)
        ScopeChangeResult.Failed.RemoveFailed ->
            UiMessage(R.string.apps_scope_failed_remove, listOf(packageName))
    }

/** 简单工厂,避免在 Compose 中手动拼参。*/
class AppMonitorViewModelFactory(
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppMonitorViewModel(
            packageName = packageName,
            catalog = container.catalog,
            server = container.server,
            scopeRepository = container.scopeRepository,
            logs = container.logs,
            settings = container.settings,
        ) as T
    }
}