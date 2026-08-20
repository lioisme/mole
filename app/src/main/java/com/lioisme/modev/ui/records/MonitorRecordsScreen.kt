package com.lioisme.modev.ui.records

import androidx.activity.compose.BackHandler
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.data.mapping.RowModel
import com.lioisme.modev.ui.components.CollapsedSearchInput
import com.lioisme.modev.ui.components.EmptyState
import com.lioisme.modev.ui.components.KindFilterMenu
import com.lioisme.modev.ui.components.KindOption
import com.lioisme.modev.ui.components.RecordRow
import com.lioisme.modev.ui.components.RetryRow
import com.lioisme.modev.ui.components.SearchInputField
import com.lioisme.modev.ui.components.TimelineHeader
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.rememberSearchController
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.common.groupLabelOrNull
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.common.secondKey
import com.lioisme.modev.ui.common.timelineLabel

/**
 * 单应用算法记录列表(V19,需求 R1 之一;V20 单应用语境化):
 * - 顶栏:CenterAlignedTopAppBar + SearchBar(折叠条,单击展开全屏 SearchView);
 *   AppBar 内仅此一个元素(硬性规范);
 * - 搜索条内仅筛选按钮(类别单选 12 项,固定记录类型 = 算法);
 * - 内容区:分页记录列表(Paging + 类别筛选 + 搜索,空态/加载/重试),
 *   秒级时间线分组(与每应用记录页同契约);
 * - 无应用头部 / 启动 / 危险操作 —— 纯列表界面(语境由入口页承担);
 * - 返回语义:仅弹栈(过导航门控)。
 */
@Composable
fun AlgorithmRecordsScreen(
    viewModel: MonitorRecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.algorithm_records_search),
        kinds = CryptoKind.entries.map { KindOption(it.name, kindLabelRes(it)) },
    )
}

/**
 * 单应用数据库记录列表(V19,需求 R1 之二):同 [AlgorithmRecordsScreen] 结构,
 * 固定记录类型 = 数据库,类别单选 5 项。
 */
@Composable
fun DatabaseRecordsScreen(
    viewModel: MonitorRecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.database_records_search),
        kinds = SqlKind.entries.map { KindOption(it.name, sqlKindLabelRes(it)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorRecordsContent(
    viewModel: MonitorRecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    placeholder: String,
    kinds: List<KindOption>,
) {
    val context = LocalContext.current
    val records = viewModel.records.collectAsLazyPagingItems()

    // 搜索状态机共享封装(与每应用记录页同款:独立意图 / 唯一动画驱动 /
    // 收起后导航 / snapTo 兜底,红线全守)。
    val search = rememberSearchController<Long>(onNavigate = onOpenRecord)
    var selectedKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // 过滤条件变化 → ViewModel 重建 Paging 流(SQL 过滤)。
    LaunchedEffect(search.queryText, selectedKindName) {
        viewModel.setQuery(search.queryText)
        viewModel.setKindName(selectedKindName)
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
                                placeholder = placeholder,
                                filterSlot = {
                                    KindFilterMenu(
                                        expanded = filterMenuOpen,
                                        onExpandedChange = { filterMenuOpen = it },
                                        selectedKindName = selectedKindName,
                                        onKindSelect = { selectedKindName = it },
                                        kinds = kinds,
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
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            RecordsList(
                records = records,
                listState = viewModel.listState,
                onOpenRecord = { id ->
                    // 交互统一(与每应用记录页同款):展开态先收起,导航由动画唯一驱动协程执行。
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
                    onBack = {
                        search.collapse()
                        focusManager.clearFocus()
                    },
                    placeholder = placeholder,
                    filterSlot = {
                        KindFilterMenu(
                            expanded = filterMenuOpen,
                            onExpandedChange = { filterMenuOpen = it },
                            selectedKindName = selectedKindName,
                            onKindSelect = { selectedKindName = it },
                            kinds = kinds,
                        )
                    },
                )
            },
        ) {
            BackHandler(enabled = true) {
                search.collapse()
                focusManager.clearFocus()
            }
            RecordsList(
                records = records,
                listState = viewModel.listState,
                onOpenRecord = { id ->
                    search.collapseWithNavigation(id)
                },
            )
        }
    }
}

/**
 * 分页记录列表(时间线分组;滚动状态由 VM 级 [LazyListState] 承载,
 * 详情往返后原位恢复 —— V20 R3 滚动位置保持)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordsList(
    records: LazyPagingItems<RowModel>,
    listState: LazyListState,
    onOpenRecord: (Long) -> Unit,
) {
    // V17 R3:组头 locale/时制与 RecordRow 尾部时间同源(context 包 locale)。
    val context = LocalContext.current
    val locale = rememberEffectiveLocale()
    val is24h = remember(context) { DateFormat.is24HourFormat(context) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
                // 手动索引循环(V17 R3 秒级时间线分组):组起始插组头;首项恒为组起始。
                for (index in 0 until records.itemCount) {
                    val record = records[index]
                    if (record == null) {
                        item(key = "ph-$index") { Box(Modifier.fillMaxWidth().height(0.dp)) }
                    } else {
                        val previous = if (index > 0) records[index - 1] else null
                        val label =
                            if (index == 0) {
                                timelineLabel(record.timestamp, locale, is24h)
                            } else {
                                groupLabelOrNull(
                                    record.timestamp,
                                    previous?.timestamp,
                                    locale,
                                    is24h,
                                )
                            }
                        if (label != null) {
                            item(key = "h-" + secondKey(record.timestamp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TimelineHeader(text = label)
                                }
                            }
                        }
                        item(key = record.id) {
                            RecordRow(
                                record = record,
                                onClick = { onOpenRecord(record.id) },
                                modifier =
                                    Modifier
                                        .testTag("record-row")
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
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