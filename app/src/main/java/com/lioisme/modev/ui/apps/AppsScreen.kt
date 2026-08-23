package com.lioisme.modev.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.ui.components.AppIcon
import com.lioisme.modev.ui.components.CollapsedSearchInput
import com.lioisme.modev.ui.components.EmptyState
import com.lioisme.modev.ui.components.SearchInputField
import com.lioisme.modev.ui.components.StatusDot
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.rememberSearchController
import com.lioisme.modev.ui.theme.StatusKind
/// 搜索收起动画完成后的导航目标(先收后导,单一事实源)。
internal sealed interface AppsNavTarget {
    /// 应用详情页。
    data class App(val packageName: String) : AppsNavTarget
}

/**
 * 应用列表页(2026-08-16 V3,四界面重构,根页;V18 设置入口归位搜索条左上角):
 * 全量已安装应用,勾选 = LSPosed 作用域,单一事实源。
 * - 顶栏:CenterAlignedTopAppBar + SearchBar(折叠条,单击展开全屏 SearchView);
 *   AppBar 内仅此一个元素,硬性规则,禁止放置其他按钮;
 * - SearchBar 内:左上角设置按钮与右下角筛选按钮对称(V18 自筛选菜单迁出),
 *   右下角仅筛选按钮:「已作用域置顶 / 系统应用 / Xposed 模块」DropdownMenu
 *   (Checkbox 复选,V22 R2 置顶开关迁回本菜单);
 * - 搜索状态机由共享封装 rememberSearchController 提供(红线全守);
 * - 应用表:ProjectList 行规范:AppIcon 40dp + 名称 bodyLarge Medium + 包名
 *  bodySmall + 记录数 secondaryContainer 徽标,行尾 Checkbox 作用域勾选,
 *  padding h16/v12,行间 outlineVariant 分隔线(V7 起无运行按钮);
 * - 下拉刷新(PullToRefreshBox);空态用 EmptyState。
 * 返回语义:根页空闲返回由导航层抛回系统退出,全屏 SearchView 返回只收状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // 已作用域应用置顶(V22 R2 自通用设置页迁回筛选菜单,与系统应用 / 模块同列):
    // AppListSettings 单一事实源,排序流水线直接消费,变更即重排,跨页返回不滞留旧序。

    // 搜索状态机共享封装(无参 rememberSearchBarState / 独立意图 / 唯一动画驱动 /
    // 收起后导航 / snapTo 兜底,红线全守)。T = 导航目标(应用详情)。
    val search = rememberSearchController<AppsNavTarget>(
        onNavigate = { target ->
            when (target) {
                is AppsNavTarget.App -> onOpen(target.packageName)
            }
        },
    )
    // 作用域筛选菜单开关:折叠条与全屏 SearchView 共用同一状态,单源驱动)。
    var filterMenuOpen by remember { mutableStateOf(false) }
    // 已作用域置顶开关(V22 R2 迁回筛选菜单):AppListSettings 单一事实源。
    val pinScoped by AppListSettings.pinScoped.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(
                context.getString(message.resId, *message.args.toTypedArray())
            )
        }
    }

    // 查询词变更 → ViewModel 过滤(内存过滤,语义不变,UI 层持有状态)。
    LaunchedEffect(search.queryText) {
        viewModel.setQuery(search.queryText)
    }

    // 设置入口:折叠态位于搜索条左上角,与筛选按钮对称,直接导航。
    val openSettings: () -> Unit = { onOpenSettings() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    SearchBar(
                        state = search.searchBarState,
                        inputField = {
CollapsedSearchInput(
                                query = search.queryText,
                                // 根页无返回语义,返回按钮隐藏(二级页才有);
                                // 左上角 = 设置按钮(V18,与右下角筛选按钮对称)。
                                onBack = null,
                                onClear = { search.queryText = "" },
                                onTapToExpand = { search.searchActive = true },
                                placeholder = stringResource(R.string.apps_search),
                                leadingContent = {
                                    TipIconButton(
                                        tooltip = stringResource(R.string.cd_settings),
                                        onClick = openSettings,
                                    ) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = stringResource(R.string.cd_settings),
                                        )
                                    }
                                },
                                filterSlot = {
                                    ScopeFilterMenu(
                                        expanded = filterMenuOpen,
                                        onExpandedChange = { filterMenuOpen = it },
                                        pinScoped = pinScoped,
                                        onPinScopedChange = { AppListSettings.setPinScoped(it, context) },
                                        showSystem = state.showSystem,
                                        onShowSystemChange = { AppListSettings.setShowSystem(it, context) },
                                        showModules = state.showModules,
                                        onShowModulesChange = { AppListSettings.setShowModules(it, context) },
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
                // V16 R4:目录扫描进行中且尚无行 → progress,防首帧误显「未发现应用」。
                if (state.rows.isEmpty() && state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (state.rows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Android,
                        message =
                            if (search.queryText.isNotBlank()) {
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
                                // 展开态点击先收后导,见全屏 SearchView 内 onClick)。
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

// 全屏搜索视图:Dialog 新窗口承载,官方组件,返回键/缩放动画/键盘管理内置)。
    // 内容直接渲染 ViewModel 过滤结果(state.rows),不做二次内存过滤(数据流单一事实源)。
    if (search.searchBarState.currentValue == SearchBarValue.Expanded) {
        // V16 R5/R6:官方 ExpandedFullScreenSearchBar 为独立 Dialog 窗口,窗口组合
        // 只继承创建时的 LocalContext 快照(主树 provider 更新不传入窗口),故窗口内
        // 文本一律经 rememberLocaleAwareResources(语言状态驱动重组 + wrap 解析)。
        val dlgRes = rememberLocaleAwareResources()
        CompositionLocalProvider(LocalContext provides LocaleManager.wrap(context)) {
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
                    // 展开态返回语义:退出搜索,与 LIDE 同款:返回键等价收起。
                    onBack = {
                        search.collapse()
                        focusManager.clearFocus()
                    },
                    placeholder = dlgRes.getString(R.string.apps_search),
                    filterSlot = {
                        ScopeFilterMenu(
                            expanded = filterMenuOpen,
                            onExpandedChange = { filterMenuOpen = it },
                            pinScoped = pinScoped,
                            onPinScopedChange = { AppListSettings.setPinScoped(it, context) },
                            showSystem = state.showSystem,
                            onShowSystemChange = { AppListSettings.setShowSystem(it, context) },
                            showModules = state.showModules,
                            onShowModulesChange = { AppListSettings.setShowModules(it, context) },
                        )
                    },
                )
            },
        ) {
            // Dialog 内返回键:收起 + 显式清焦点,防焦点回落折叠条重新展开死循环。
            androidx.activity.compose.BackHandler(enabled = true) {
                search.collapse()
                focusManager.clearFocus()
            }
            if (state.rows.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Android,
                    message = dlgRes.getString(R.string.apps_empty_no_match),
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
}

/**
 * 作用域筛选菜单(2026-08-16 V3;2026-08-17 V10 置顶入菜单;2026-08-18 V14 R7
 * 置顶收敛通用设置页;V17 R1 新增「Xposed 模块」显示;V18 移除「设置」项,
 * 设置入口归位搜索条左上角;V22 R1 移除「仅作用域」;V22 R2 置顶开关迁回本菜单,
 * 与系统应用 / 模块并列):
 * SearchBar trailingIcon 槽内,三项 Checkbox 复选(只读 Checkbox + 整项 OnClick,
 * 契约 8 勾选态断言不可行,改行为级断言),状态持久化,
 * AppListSettings 单一事实源。
 * 有激活条件时筛选按钮 primary 着色,筛选启用指示。
 */
@Composable
private fun ScopeFilterMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    pinScoped: Boolean,
    onPinScopedChange: (Boolean) -> Unit,
    showSystem: Boolean,
    onShowSystemChange: (Boolean) -> Unit,
    showModules: Boolean,
    onShowModulesChange: (Boolean) -> Unit,
) {
    // 筛选槽可组合进 Dialog 窗口(展开态 trailingIcon),文本经语言状态驱动解析。
    val res = rememberLocaleAwareResources()
    Box {
        TipIconButton(tooltip = res.getString(R.string.cd_filter), onClick = { onExpandedChange(true) }) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = res.getString(R.string.cd_filter),
                tint =
                    if (!showSystem || !showModules) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text(res.getString(R.string.settings_pin_scoped), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Checkbox(checked = pinScoped, onCheckedChange = null) },
                onClick = {
                    onPinScopedChange(!pinScoped)
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(res.getString(R.string.apps_system), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Checkbox(checked = showSystem, onCheckedChange = null) },
                onClick = {
                    onShowSystemChange(!showSystem)
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(res.getString(R.string.apps_modules), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Checkbox(checked = showModules, onCheckedChange = null) },
                onClick = {
                    onShowModulesChange(!showModules)
                    onExpandedChange(false)
                },
            )
        }
    }
}

/**
 * 应用行(2026-08-15 UI 重构 Phase 5,对齐 LIDE ProjectList 行规范;2026-08-17 V7;
 * 2026-08-18 V14 R4 状态角标化;V19 T6 矩阵 M6:M3 [ListItem] 承载):
 * leading AppIcon 40dp + headline 名称 bodyLarge Medium + 记录数 secondaryContainer
 * 徽标 + supporting 包名 bodySmall + 状态角标(已作用域时:运行中=绿色圆点/
 * 未运行=灰色圆点,完整语义进 contentDescription 与长按 tooltip),
 * trailing Checkbox(作用域勾选,V7 起置于右侧,无运行按钮);
 * 行间 outlineVariant 分隔线,列表外侧,行内不分割。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRowItem(
    row: AppsViewModel.AppRow,
    scopeReady: Boolean,
    pending: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 仅计数徽标存在时保留间距(V13:无空缺字段不留空白)。
                if (row.recordCount > 0) {
                    Spacer(Modifier.width(6.dp))
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
        },
        supportingContent = {
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
                    StatusDot(
                        kind =
                            if (row.connected) {
                                StatusKind.ONLINE
                            } else {
                                StatusKind.OFFLINE
                            },
                        label =
                            if (row.connected) {
                                stringResource(R.string.online)
                            } else {
                                stringResource(R.string.offline)
                            },
                    )
                }
            }
        },
        leadingContent = { AppIcon(packageName = row.packageName, size = 40.dp) },
        trailingContent = {
            Checkbox(
                checked = row.scoped,
                onCheckedChange = { onToggle() },
                enabled = scopeReady && !pending,
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                // 测试锚点:导航行 UI 测试用 testTag 定位应用行(Google 惯例)。
                .testTag("app-row")
                .clickable(onClick = onClick),
    )
}

@Composable
private fun countText(count: Long): String {
// V13:按应用实际生效 locale 判定(与 stringResource 解析同源)——跟随系统
    // 时,LocaleManager.currentLanguageTag = "system" 会误判为英文(与千错位)。
    // V15:locale 驱动 NumberFormat(zh ≥1 万紧凑 / 其他 ≥1K,以下千分位)。
    // V16 R5:热切换下 LocalConfiguration 陈旧,一律经包装 Context 解析(effectiveLocale)。
    val locale = rememberEffectiveLocale()
    return AppCountFormat.compact(count, locale)
}
