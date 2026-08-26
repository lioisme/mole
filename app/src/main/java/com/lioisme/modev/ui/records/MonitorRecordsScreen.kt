package com.lioisme.modev.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.data.mapping.BehaviorRow
import com.lioisme.modev.data.mapping.CryptoRow
import com.lioisme.modev.data.mapping.PrefsRow
import com.lioisme.modev.data.mapping.RowModel
import com.lioisme.modev.data.mapping.SqlRow
import com.lioisme.modev.data.mapping.WebViewRow
import com.lioisme.modev.ui.apps.AppCountFormat
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.components.CollapsedSearchInput
import com.lioisme.modev.ui.components.KindFilterMenu
import com.lioisme.modev.ui.components.KindOption
import com.lioisme.modev.ui.components.SearchInputField
import com.lioisme.modev.ui.components.behaviorKindLabelRes
import com.lioisme.modev.ui.components.behaviorSummary
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.prefsKindLabelRes
import com.lioisme.modev.ui.components.prefsSummary
import com.lioisme.modev.ui.components.rememberSearchController
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.sqlRowSummary
import com.lioisme.modev.ui.components.copySummary
import com.lioisme.modev.ui.components.webviewKindLabelRes
import com.lioisme.modev.ui.components.webviewSummary
import kotlinx.coroutines.launch

/**
 * 单应用算法记录列表(V19,需求 R1 之一;V20 单应用语境化;V23 T4.2 薄壳化:
 * 列表渲染收敛至 [RecordTimelineList],数据流统一 [RecordsViewModel]):
 * - 顶栏:CenterAlignedTopAppBar + SearchBar(折叠条,单击展开全屏 SearchView);
 *   AppBar 内仅此一个元素(硬性规范);
 * - 搜索条内仅筛选按钮(类别单选 12 项,固定记录类型 = 算法);
 * - 无应用头部 / 启动 / 危险操作 —— 纯列表界面(语境由入口页承担);
 * - 返回语义:仅弹栈(过导航门控)。
 */
@Composable
fun AlgorithmRecordsScreen(
    viewModel: RecordsViewModel,
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
    viewModel: RecordsViewModel,
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

/**
 * 单应用行为记录列表(V27 一等公民):同两兄弟结构,
 * 固定记录类型 = 行为,类别单选 7 项。
 */
@Composable
fun BehaviorRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.behavior_records_search),
        kinds = BehaviorKind.entries.map { KindOption(it.name, behaviorKindLabelRes(it)) },
    )
}

/**
 * 单应用偏好记录列表(V28 一等公民):同三兄弟结构,
 * 固定记录类型 = 偏好,类别单选 5 项。
 */
@Composable
fun PrefsRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.prefs_records_search),
        kinds = PrefsKind.entries.map { KindOption(it.name, prefsKindLabelRes(it)) },
    )
}

/**
 * 单应用 WebView 记录列表(V29 一等公民):同四兄弟结构,
 * 固定记录类型 = WebView,类别单选 5 项。
 */
@Composable
fun WebViewRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.webview_records_search),
        kinds = WebViewKind.entries.map { KindOption(it.name, webviewKindLabelRes(it)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorRecordsContent(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    placeholder: String,
    kinds: List<KindOption>,
) {
    val records = viewModel.records.collectAsLazyPagingItems()

    // 搜索状态机共享封装(独立意图 / 唯一动画驱动 / 收起后导航 / snapTo 兜底,红线全守)。
    val search = rememberSearchController<Long>(onNavigate = onOpenRecord)
    var selectedKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // V30:计数副标使用 records.itemCount(消除独立 Room COUNT Flow 的重组触发)。
    val locale = rememberEffectiveLocale()

    // V27 M1-T4:长按复制摘要(剪贴板 + Snackbar 统一出站)。
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun copySummary(record: RowModel) {
        val summary =
            when (record) {
                is CryptoRow -> record.record.copySummary(context)
                is SqlRow -> record.sqlRowSummary(context)
                is BehaviorRow -> record.behaviorSummary(context)
                is PrefsRow -> record.prefsSummary(context)
                is WebViewRow -> webviewSummary(context, record)
            }
        clipboard.setText(AnnotatedString(summary))
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.record_copied_toast))
        }
    }

    // 过滤条件变化 → ViewModel 重建 Paging 流(SQL 过滤)。
    LaunchedEffect(search.queryText, selectedKindName) {
        viewModel.setQuery(search.queryText)
        viewModel.setKindName(selectedKindName)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Column {
                if (records.itemCount > 0 && !search.searchActive) {
                    Text(
                        text =
                            stringResource(
                                R.string.records_count_subtitle,
                                AppCountFormat.grouped(records.itemCount.toLong(), locale),
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                RecordTimelineList(
                    records = records,
                    listState = viewModel.listState,
                    onOpenRecord = { id ->
                        // 交互统一:展开态先收起,导航由动画唯一驱动协程执行。
                        if (search.searchActive) {
                            search.collapseWithNavigation(id)
                        } else {
                            onOpenRecord(id)
                        }
                    },
                    onRecordLongPress = ::copySummary,
                )
            }
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
            RecordTimelineList(
                records = records,
                listState = viewModel.listState,
                onOpenRecord = { id ->
                    search.collapseWithNavigation(id)
                },
                onRecordLongPress = ::copySummary,
            )
        }
    }
}
