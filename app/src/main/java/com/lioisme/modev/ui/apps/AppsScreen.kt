package com.lioisme.modev.ui.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.CollapsedSearchInput
import com.lioisme.modev.ui.components.EmptyState
import com.lioisme.modev.ui.components.SearchInputField
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.rememberSearchController
/// 搜索收起动画完成后的导航目标(先收后导,单一事实源)。
internal sealed interface AppsNavTarget {
    /// 应用详情页。
    data class App(val packageName: String) : AppsNavTarget
    /// 全局设置页。
    data object Settings : AppsNavTarget
}

/**
 * 应用列表页(2026-08-16 V3,四界面重构,根页):
 * 全量已安装应用,勾选 = LSPosed 作用域(单一事实源)。
 * - 顶栏:CenterAlignedTopAppBar + SearchBar(折叠条,单击展开全屏 SearchView);
 *   AppBar 内仅此一个元素(硬性规范),禁止放置其他按钮;
 * - SearchBar 内仅筛选按钮:「仅已作用域 / 系统应用」DropdownMenu(Checkbox 复选),
 *   菜单底部含「设置」项——全局设置页的唯一规范入口(菜单内容不受按钮数限制);
 * - 搜索状态机由共享封装 rememberSearchController 提供(红线全守);
* - 应用行:ProjectList 行规范(AppIcon 40dp + 名称 bodyLarge Medium + 包名
 *   bodySmall + 记录数 secondaryContainer 徽标,行尾 Checkbox 作用域勾选,
 *   padding h16/v12,行间 outlineVariant 分隔线;V7 起无运行按钮);
 * - 下拉刷新(PullToRefreshBox);空态用 EmptyState。
 * 返回语义:根页空闲返回由导航层抛回系统退出;全屏 SearchView 返回只收状态。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(
    viewModel: AppsViewModel,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 搜索状态机共享封装(无参 rememberSearchBarState / 独立意图 / 唯一动画驱动 /
    // 收起后导航 / snapTo 兜底,红线全守)。T = 导航目标(应用详情 / 设置)。
    val search = rememberSearchController<AppsNavTarget>(
        onNavigate = { target ->
            when (target) {
                is AppsNavTarget.App -> onOpen(target.packageName)
                AppsNavTarget.Settings -> onOpenSettings()
            }
        },
    )
    // 作用域筛选菜单开合(折叠条与全屏 SearchView 共用同一状态,单源驱动)。
    var filterMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray())
            )
        }
    }

    // 查询词变化 → ViewModel 过滤(内存过滤,语义不变,UI 层持有状态)。
    LaunchedEffect(search.queryText) {
        viewModel.setQuery(search.queryText)
    }

    // 设置入口统一先收后导(Q2 交互统一):展开态进入设置前必须走收起动画,
    // 导航由动画唯一驱动协程在动画完成后执行;若直接 navigate,动画协程随
    // 组合销毁被打断,currentValue 卡在 Expanded,返回时全屏搜索视图重新弹出
    // (2026-08-16 实机走查发现的回归,见 SearchBarState.currentValue = progress==0)。
    val openSettings: () -> Unit = {
        if (search.searchActive) {
            search.collapseWithNavigation(AppsNavTarget.Settings)
        } else {
            onOpenSettings()
        }
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
                                // 根页无返回语义,返回按钮隐藏(二级页才有)。
                                onBack = null,
                                onClear = { search.queryText = "" },
                                onTapToExpand = { search.searchActive = true },
                                placeholder = stringResource(R.string.apps_search),
                                filterSlot = {
                                    ScopeFilterMenu(
                                        expanded = filterMenuOpen,
                                        onExpandedChange = { filterMenuOpen = it },
                                        onlyScoped = state.onlyScoped,
                                        onOnlyScopedChange = viewModel::setOnlyScoped,
                                        showSystem = state.showSystem,
                                        onShowSystemChange = viewModel::setShowSystem,
                                        onOpenSettings = openSettings,
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
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
Column(Modifier.fillMaxSize()) {
                if (state.rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Android,
                        message =
                            if (search.queryText.isNotBlank() || state.onlyScoped) {
                                stringResource(R.string.apps_empty_no_match)
                            } else {
                                stringResource(R.string.apps_empty_no_apps)
                            },
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
items(state.rows, key = { it.packageName }) { row ->
                            AppRowItem(
                                row = row,
                                scopeReady = state.scopeReady,
                                pending = row.packageName in state.pending,
                                onToggle = { viewModel.toggle(row.packageName) },
                                // 折叠态点击直接导航(无 Dialog 可收起);
                                // 展开态点击先收后导(见全屏 SearchView 内 onClick)。
                                onClick = { onOpen(row.packageName) },
                            )
                            if (row != state.rows.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 全屏搜索视图:Dialog 新窗口承载(官方组件,返回键/缩放动画/键盘管理内置)。
    // 内容直接渲染 ViewModel 过滤结果(state.rows),不做二次内存过滤(数据流单一事实源)。
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
                    placeholder = stringResource(R.string.apps_search),
                    filterSlot = {
                        ScopeFilterMenu(
                            expanded = filterMenuOpen,
                            onExpandedChange = { filterMenuOpen = it },
                            onlyScoped = state.onlyScoped,
                            onOnlyScopedChange = viewModel::setOnlyScoped,
                            showSystem = state.showSystem,
                            onShowSystemChange = viewModel::setShowSystem,
                            onOpenSettings = openSettings,
                        )
                    },
                )
            },
        ) {
            // Dialog 内返回键:收起 + 显式清焦点(防焦点回落折叠条重新展开死循环)。
            androidx.activity.compose.BackHandler(enabled = true) {
                search.collapse()
                focusManager.clearFocus()
            }
            if (state.rows.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Android,
                    message = stringResource(R.string.apps_empty_no_match),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
items(state.rows, key = { it.packageName }) { row ->
                        AppRowItem(
                            row = row,
                            scopeReady = state.scopeReady,
                            pending = row.packageName in state.pending,
                            onToggle = { viewModel.toggle(row.packageName) },
                            onClick = {
                                // 先收起搜索态再导航,实际导航由动画唯一驱动协程执行。
                                search.collapseWithNavigation(AppsNavTarget.App(row.packageName))
                            },
                        )
                        if (row != state.rows.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 作用域筛选菜单(2026-08-16 V3):SearchBar trailingIcon 槽内,
 * 「仅已作用域」/「系统应用」Checkbox 复选 + 底部「设置」项(全局设置页唯一规范入口)。
 * 有激活条件时筛选按钮 primary 着色(筛选启用指示)。
 */
@Composable
private fun ScopeFilterMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onlyScoped: Boolean,
    onOnlyScopedChange: (Boolean) -> Unit,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box {
        TipIconButton(tooltip = stringResource(R.string.cd_filter), onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = stringResource(R.string.cd_filter),
                tint =
                    if (onlyScoped || !showSystem) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.apps_only_scoped), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Checkbox(checked = onlyScoped, onCheckedChange = null) },
                onClick = {
                    onOnlyScopedChange(!onlyScoped)
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.apps_system), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Checkbox(checked = showSystem, onCheckedChange = null) },
                onClick = {
                    onShowSystemChange(!showSystem)
                    onExpandedChange(false)
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onOpenSettings()
                },
            )
        }
    }
}

/**
 * 应用行(2026-08-15 UI 重构 Phase 5,对齐 LIDE ProjectList 行规范;2026-08-17 V7):
 * AppIcon 40dp + 名称 bodyLarge Medium + 记录数 secondaryContainer 徽标 +
 * 包名 bodySmall + 在线状态,行尾 Checkbox(作用域勾选,V7 起置于右侧,
 * 无运行按钮);
 * padding h16/v12,行间 outlineVariant 分隔线(列表外侧,行内不分隔)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRowItem(
    row: AppsViewModel.AppRow,
    scopeReady: Boolean,
    pending: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // 测试锚点:导航级 UI 测试经 testTag 定位应用行(Google 惯例)。
                .testTag("app-row")
                .combinedClickable(onClick = onClick, onLongClick = null)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = row.packageName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                if (row.recordCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            countText(row.recordCount),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.scoped) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (row.connected) {
                            stringResource(R.string.online)
                        } else {
                            stringResource(R.string.apps_not_running)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (row.connected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
            checked = row.scoped,
            onCheckedChange = { onToggle() },
            enabled = scopeReady && !pending,
        )
    }
}

@Composable
private fun countText(count: Long): String = when {
    count >= 10_000 -> stringResource(R.string.apps_count_wan, count / 10_000)
    count >= 1_000 -> stringResource(R.string.apps_count_qian, count / 1000)
    else -> count.toString()
}
