package com.lioisme.modev.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.CollapsedSearchInput
import com.lioisme.modev.ui.components.EmptyState
import com.lioisme.modev.ui.components.RecordRow
import com.lioisme.modev.ui.components.SearchInputField
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.copySummary
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.rememberSearchController
import kotlinx.coroutines.launch

/**
 * 应用记录页(2026-08-16 V3 五界面收敛;2026-08-17 V7 捕获设置入筛选菜单;
 * V10 更名 AppRecords + 捕获设置全局化 + 应用级危险操作归位):
 * 点击应用进入,单一职责 = 该应用的记录列表。
 * - 顶栏:CenterAlignedTopAppBar + SearchBar(折叠条,单击展开全屏 SearchView);
 *   AppBar 内仅此一个元素(硬性规范);
 * - 搜索条内仅筛选按钮(类别单选 9 项,KindFilterMenu;有激活条件时按钮着色);
 * - 内容区(自顶向下,头部固定不滚动):
 *   应用头部(AppIcon 40dp + 应用名 + 状态 chips:在线/监听 + 「启动」按钮 +
 *   「更多」菜单 = 移出作用域 / 清空该应用日志,均带确认)+ 分页记录列表
 *   (Paging + 类别筛选 + 搜索,空态/加载/重试);
 * - 返回语义:仅弹栈(过导航门控)。
 * 搜索状态机由共享封装 [rememberSearchController] 提供(红线全守)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRecordsScreen(
    viewModel: AppRecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    onLaunch: (String) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val records = viewModel.records.collectAsLazyPagingItems()

    // 搜索状态机共享封装(无参 rememberSearchBarState / 独立意图 / 唯一动画驱动 /
    // 收起后导航 / snapTo 兜底,红线全守)。
    val search = rememberSearchController<Long>(onNavigate = onOpenRecord)
    var selectedKind by rememberSaveable { mutableStateOf<CryptoKind?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }

    // 应用级危险操作(移出作用域 / 清空该应用日志)确认状态
    var pendingDanger by remember { mutableStateOf<DangerAction?>(null) }

    // 过滤条件变化 → ViewModel 重建 Paging 流(SQL 过滤)。
    LaunchedEffect(search.queryText, selectedKind) {
        viewModel.setQuery(search.queryText)
        viewModel.setKind(selectedKind)
    }

    LaunchedEffect(viewModel.messages) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray())
            )
        }
    }

    // 列表行长按复制记录摘要(V13,与详情页整段复制同 UX:剪贴板 + Snackbar 反馈)。
    fun copyRecord(record: CryptoRecord) {
        clipboard.setText(AnnotatedString(record.copySummary(context)))
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.record_copied)) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SearchBar(
                        state = search.searchBarState,
                        inputField = {
                            CollapsedSearchInput(
                                query = search.queryText,
                                onBack = onBack,
                                onClear = { search.queryText = "" },
                                onTapToExpand = { search.searchActive = true },
                                placeholder = stringResource(R.string.detail_search),
                                filterSlot = {
                                    KindFilterMenu(
                                        expanded = filterMenuOpen,
                                        onExpandedChange = { filterMenuOpen = it },
                                        selectedKind = selectedKind,
                                        onKindSelect = { selectedKind = it },
                                    )
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(0.92f),
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AppRecordsContent(
                viewModel = viewModel,
                records = records,
                onLaunch = onLaunch,
                onCopyRecord = ::copyRecord,
                onRemoveScope = { pendingDanger = DangerAction.REMOVE_SCOPE },
                onClearLogs = { pendingDanger = DangerAction.CLEAR_LOGS },
                onOpenRecord = { id ->
                    // 交互统一(折叠态直接导航——无 Dialog 可收起;
                    // 展开态先收起,导航由动画唯一驱动协程在收起完成后执行)。
                    if (search.searchActive) {
                        search.collapseWithNavigation(id)
                    } else {
                        onOpenRecord(id)
                    }
                },
            )
        }
    }

    // 全屏搜索视图:Dialog 新窗口承载(官方组件,返回键/缩放动画/键盘管理内置)。
    if (search.searchBarState.currentValue == SearchBarValue.Expanded) {
        ExpandedFullScreenSearchBar(
            state = search.searchBarState,
            inputField = {
                SearchInputField(
                    query = search.queryText,
                    onQueryChange = { search.queryText = it },
                    active = search.searchActive,
                    onActiveChange = search::onInputActiveChange,
                    onSearch = {
                        search.collapse()
                        focusManager.clearFocus()
                    },
                    // 展开态返回语义=退出搜索(与 LIDE 同款:返回键等价收起)。
                    onBack = {
                        search.collapse()
                        focusManager.clearFocus()
                    },
                    placeholder = stringResource(R.string.detail_search),
                    filterSlot = {
                        KindFilterMenu(
                            expanded = filterMenuOpen,
                            onExpandedChange = { filterMenuOpen = it },
                            selectedKind = selectedKind,
                            onKindSelect = { selectedKind = it },
                        )
                    },
                )
            },
        ) {
            // Dialog 内返回键:收起 + 显式清焦点(防焦点回落折叠条重新展开死循环)。
            BackHandler(enabled = true) {
                search.collapse()
                focusManager.clearFocus()
            }
            AppRecordsContent(
                viewModel = viewModel,
                records = records,
                onLaunch = onLaunch,
                onCopyRecord = ::copyRecord,
                onRemoveScope = { pendingDanger = DangerAction.REMOVE_SCOPE },
                onClearLogs = { pendingDanger = DangerAction.CLEAR_LOGS },
                onOpenRecord = { id ->
                    search.collapseWithNavigation(id)
                },
            )
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
 * 应用记录主体:固定头部(应用信息)+ 分页记录列表。
 * 头部固定不随列表滚动——AppBar 仅 SearchBar 无应用名,头部承担「当前应用」语境。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRecordsContent(
    viewModel: AppRecordsViewModel,
    records: LazyPagingItems<CryptoRecord>,
    onLaunch: (String) -> Unit,
    onCopyRecord: (CryptoRecord) -> Unit,
    onRemoveScope: () -> Unit,
    onClearLogs: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val scopeState by viewModel.scopeState.collectAsStateWithLifecycle()
    val appInfo by viewModel.appInfo.collectAsStateWithLifecycle()
    val scoped = (scopeState as? ScopeState.Ready)?.isScoped(viewModel.packageName) == true

    Column(Modifier.fillMaxSize()) {
        // 固定头部:应用名 + 状态 chips(AppBar 无标题,头部是唯一语境来源)。
        AppHeaderRow(
            label = appInfo?.label ?: viewModel.packageName,
            packageName = viewModel.packageName,
            connected = connected,
            scopeState = scopeState,
            scoped = scoped,
            // 仅存在启动入口时展示「启动」按钮
            onLaunch =
                if (appInfo?.launchable == true && appInfo?.enabled != false) {
                    { onLaunch(viewModel.packageName) }
                } else {
                    null
                },
            // 应用级危险操作(V10 从捕获设置页归位本页语境)
            onRemoveScope = if (scoped) onRemoveScope else null,
            onClearLogs = onClearLogs,
        )
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                // 卡片列表呼吸感:上下 8dp + 行内 4dp,卡间距合计 8dp。
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                when {
                    records.itemCount == 0 && records.loadState.refresh is LoadState.NotLoading ->
                        item(key = "empty") {
                            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                EmptyState(
                                    icon = Icons.Outlined.Terminal,
                                    message = stringResource(R.string.detail_empty),
                                )
                            }
                        }

                    records.itemCount == 0 && records.loadState.refresh is LoadState.Loading ->
                        item(key = "loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                    records.itemCount == 0 && records.loadState.refresh is LoadState.Error ->
                        item(key = "refresh-error") {
                            RetryRow(onRetry = { records.retry() })
                        }

                    else -> {
                        items(records.itemCount, key = { index -> records[index]?.id ?: -index }) { index ->
                            val record = records[index] ?: return@items
                            RecordRow(
                                record = record,
                                onClick = { onOpenRecord(record.id) },
                                onLongClick = { onCopyRecord(record) },
                                // 卡片左右贴 16dp 页边距,行间留白(间距合计 8dp)。
                                modifier =
                                    Modifier
                                        .testTag("record-row")
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                when (val append = records.loadState.append) {
                    is LoadState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is LoadState.Error -> item {
                        RetryRow(onRetry = { records.retry() })
                    }
                    else -> {}
                }
            }
        }
    }
}

/**
 * 固定头部:AppIcon 40dp + 应用名(titleMedium)+ 在线/监听 chips + 「启动」按钮
 * + 「更多」菜单(移出作用域 / 清空该应用日志,V10 自捕获设置页归位)。
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
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcon(packageName = packageName, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
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
        StatusChip(
            if (connected) stringResource(R.string.online) else stringResource(R.string.offline),
            connected,
        )
        StatusChip(
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
            active = scoped && connected,
        )
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
                            text = { Text(stringResource(R.string.detail_clear_logs), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
}

/**
 * 类别筛选菜单(9 项 RadioButton 单选,SearchBar trailingIcon 槽内使用)。
 * V10:底部「捕获设置」入口移除(捕获配置已全局化,入口收敛至设置页)。
 */
@Composable
private fun KindFilterMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedKind: CryptoKind?,
    onKindSelect: (CryptoKind?) -> Unit,
) {
    Box {
        TipIconButton(tooltip = stringResource(R.string.cd_filter), onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = stringResource(R.string.cd_filter),
                // 筛选启用指示:有激活条件时 primary 着色,让用户感知列表被过滤。
                tint =
                    if (selectedKind != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { RadioButton(selected = selectedKind == null, onClick = null) },
                onClick = {
                    onKindSelect(null)
                    onExpandedChange(false)
                },
            )
            CryptoKind.entries.forEach { kind ->
                DropdownMenuItem(
                    text = { Text(stringResource(kindLabelRes(kind)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { RadioButton(selected = selectedKind == kind, onClick = null) },
                    onClick = {
                        onKindSelect(kind)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

/** 状态 chip(在线/监听语义色);宽度受限防长文字挤出操作按钮(V12 修复)。 */
@Composable
private fun StatusChip(text: String, active: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color =
            if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
    ) {
        Text(
            text,
            modifier =
                Modifier
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .widthIn(max = 110.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color =
                if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/** 重试行(Surface 承载 M3 质感,error 色文本)。 */
@Composable
private fun RetryRow(onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.retry),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.clickable(onClick = onRetry).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}