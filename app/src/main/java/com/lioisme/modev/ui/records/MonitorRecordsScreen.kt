package com.lioisme.modev.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.record.BehaviorRow
import com.lioisme.modev.domain.record.CryptoRow
import com.lioisme.modev.domain.record.PrefsRow
import com.lioisme.modev.domain.record.RowModel
import com.lioisme.modev.domain.record.SqlRow
import com.lioisme.modev.domain.record.WebViewRow
import com.lioisme.modev.domain.record.NetworkRow
import com.lioisme.modev.domain.record.DeviceEnvRow
import com.lioisme.modev.domain.record.UiInteractionRow
import com.lioisme.modev.domain.record.SystemRow
import com.lioisme.modev.domain.record.ProcessRow
import com.lioisme.modev.ui.components.networkSummary
import com.lioisme.modev.ui.apps.AppCountFormat
import com.lioisme.modev.ui.apps.KindLabelRes
import com.lioisme.modev.ui.common.rememberEffectiveLocale
import com.lioisme.modev.ui.components.KindFilterMenu
import com.lioisme.modev.ui.components.KindOption
import com.lioisme.modev.ui.components.ModevConfirmDialog
import com.lioisme.modev.ui.components.SearchInputField
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.behaviorSummary
import com.lioisme.modev.ui.components.prefsSummary
import com.lioisme.modev.ui.components.rememberSearchController
import com.lioisme.modev.ui.components.sqlRowSummary
import com.lioisme.modev.ui.components.copySummary
import com.lioisme.modev.ui.components.webviewSummary
import com.lioisme.modev.ui.components.deviceEnvSummary
import com.lioisme.modev.ui.components.uiInteractionSummary
import com.lioisme.modev.ui.components.systemSummary
import com.lioisme.modev.ui.components.processControlSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

/**
 * 单应用算法记录列表(V19,需求 R1 之一;V20 单应用语境化;V23 T4.2 薄壳化:
 * 列表渲染收敛至 [RecordTimelineList],数据流统一 [RecordsViewModel]):
 * - 顶栏:CenterAlignedTopAppBar + SearchBar(折叠条,单击展开全屏 SearchView);
 *   AppBar 内仅此一个元素、零 actions(V39 R5 排他规范,门禁 AppbarSearchExclusivityTest);
 * - 搜索条内动作:「清空本类」+ 筛选按钮(类别单选 12 项,固定记录类型 = 算法);
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
        kinds = kindOptionsOf(RecordType.CRYPTO),
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
        kinds = kindOptionsOf(RecordType.DATABASE),
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
        kinds = kindOptionsOf(RecordType.BEHAVIOR),
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
        kinds = kindOptionsOf(RecordType.PREFS),
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
        kinds = kindOptionsOf(RecordType.WEBVIEW),
    )
}

@Composable
fun NetworkRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.network_records_search),
        kinds = kindOptionsOf(RecordType.NETWORK),
    )
}

/** 单应用设备环境记录列表(V31 一等公民)。 */
@Composable
fun DeviceEnvRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.device_records_search),
        kinds = kindOptionsOf(RecordType.DEVICE_ENV),
    )
}

/** 单应用 UI 交互记录列表(V33 一等公民)。 */
@Composable
fun UiInteractionRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.ui_interaction_records_search),
        kinds = kindOptionsOf(RecordType.UI_INTERACTION),
    )
}

/** 单应用系统信息记录列表(V33 一等公民)。 */
@Composable
fun SystemRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.system_records_search),
        kinds = kindOptionsOf(RecordType.SYSTEM),
    )
}

/** 单应用进程控制记录列表(V33 一等公民)。 */
@Composable
fun ProcessControlRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(R.string.process_control_records_search),
        kinds = kindOptionsOf(RecordType.PROCESS_CONTROL),
    )
}

/**
 * 按类型全局记录列表(V40 T4 双维度:类型 × 全部应用;数据管理页类型计数行入口):
 * 同 [MonitorRecordsContent] 渲染管线;「清空本类」语义升级 = 清空该类型全部应用
 * (确认文案随语境),行内携带应用名上下文([MonitorRecordsContent] showPackage)。
 */
@Composable
fun GlobalTypeRecordsScreen(
    viewModel: RecordsViewModel,
    type: RecordType,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    MonitorRecordsContent(
        viewModel = viewModel,
        onBack = onBack,
        onOpenRecord = onOpenRecord,
        placeholder = stringResource(recordTypeSearchPlaceholderRes(type)),
        kinds = kindOptionsOf(type),
        showPackage = true,
        clearMessageRes = recordTypeClearMsgRes(type),
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
    /** V40 T4:全局列表注入行内应用名上下文(单应用列表恒 false,语境由入口页承担)。 */
    showPackage: Boolean = false,
    /** V40 T4:清空确认文案(全局 = 该类型全部应用;默认 = 单应用本类)。 */
    @StringRes clearMessageRes: Int = R.string.danger_clear_page_records_msg,
) {
    val records = viewModel.records.collectAsLazyPagingItems()

    // 搜索状态机共享封装(独立意图 / 唯一动画驱动 / 收起后导航 / snapTo 兜底,红线全守)。
    val search = rememberSearchController<Long>(onNavigate = onOpenRecord)
    var selectedKindName by rememberSaveable { mutableStateOf<String?>(null) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // V30:计数副标使用 records.itemCount(消除独立 Room COUNT Flow 的重组触发)。
    val locale = rememberEffectiveLocale()

    // V40 T4:包名 → 应用名映射(全局列表行内上下文;目录在内存,零逐行 binder)。
    val packageLabels by viewModel.packageLabels.collectAsStateWithLifecycle()

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
                is NetworkRow -> networkSummary(context, record)
                is DeviceEnvRow -> deviceEnvSummary(context, record)
                is UiInteractionRow -> uiInteractionSummary(context, record)
                is SystemRow -> systemSummary(context, record)
                is ProcessRow -> processControlSummary(context, record)
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
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // V39 R5 Appbar 排他规范:含 SearchBar 的页面 Appbar 零 actions,
            // 「清空本类」收进搜索条动作槽(折叠态 + 展开态同位)。
            CenterAlignedTopAppBar(
                title = {
                    SearchBar(
                        state = search.searchBarState,
                        inputField = {
                            // V40 T5.1:折叠态 = 标准 MD3 InputField(CollapsedSearchInput
                            // 退役);「清空本类」与筛选槽位语义不变。
                            SearchInputField(
                                query = search.queryText,
                                onQueryChange = { search.queryText = it },
                                active = search.searchActive,
                                onActiveChange = search::onInputActiveChange,
                                onSearch = {
                                    search.collapse()
                                    focusManager.clearFocus()
                                },
                                onBack = onBack,
                                placeholder = placeholder,
                                actionSlot = { ClearTypeAction(onClick = { confirmClear = true }) },
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
                // V43 T5 + V44 D1:计数读取下沉独立最小子组合器(SQL COUNT 口径)——
                // 表写入/页增长不再重进本函数主体(连带 RecordTimelineList 参数面失效)的旧放大面。
                RecordsCountSubtitle(totalCount = viewModel.totalCount, locale = locale, show = !search.searchActive)
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
                    packageLabels = if (showPackage) packageLabels else null,
                )
            }
        }
    }

    // 全屏搜索视图:Dialog 新窗口承载(官方组件,返回键/缩放动画/键盘管理内置)。
    if (search.searchBarState.currentValue == SearchBarValue.Expanded) {        ExpandedFullScreenSearchBar(
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
                    actionSlot = { ClearTypeAction(onClick = { confirmClear = true }) },
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
                packageLabels = if (showPackage) packageLabels else null,
            )
        }
    }

    if (confirmClear) {
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(clearMessageRes),
            onConfirm = {
                viewModel.clearCurrentScope { }
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
            confirmTestTag = "records-clear-confirm",
        )
    }
}

/**
 * 「清空本类」动作(V39 R5):搜索条动作槽形态,折叠/展开两态同 testTag
 * `records-clear-type`(Appbar 排他规范下页面动作不入 Appbar);
 * 点击唤起居中确认对话框(契约 4/13/24)。
 */
@Composable
private fun ClearTypeAction(onClick: () -> Unit) {
    TipIconButton(
        tooltip = stringResource(R.string.records_clear_action),
        onClick = onClick,
        modifier = Modifier.testTag("records-clear-type"),
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.records_clear_action),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * 类别选项表(V44 D2:由 [CategoryTaxonomy.forType] 驱动——菜单项恒为 taxonomy 已登记类别、
 * 顺序 = taxonomy.order、标签 = [KindLabelRes] (type,id) 唯一表;取代旧裸枚举遍历,
 * 新枚举漏登记 taxonomy 不再可能渲染出「恒零结果」幽灵项,菜单序与设置页行序合一)。
 */
internal fun kindOptionsOf(type: RecordType): List<KindOption> =
    CategoryTaxonomy.forType(type).map { category ->
        KindOption(category.id, KindLabelRes.resolve(type, category.id))
    }

/**
 * 记录计数副标(V43 T5 最小子组合器;V44 D1 口径回归底层数据):
 * 读取 SQL `COUNT(*)`(WHERE_FILTER 单源,与 records_count/MCP 同口径)而非
 * Paging 已加载窗口数——旧 itemCount 形态「展示数≠匹配数」随滚动增长、封顶 2 万。
 * 订阅收敛于本叶组合器(WhileSubscribed StateFlow):表写入失效仅重组本槽位,
 * 分页 append 与页面主体零连带(V43 T5 隔离契约保持)。
 */
@Composable
private fun RecordsCountSubtitle(
    totalCount: StateFlow<Long?>,
    locale: java.util.Locale,
    show: Boolean,
) {
    val count = totalCount.collectAsStateWithLifecycle().value
    if (count != null && count > 0 && show) {
        Text(
            text =
                stringResource(
                    R.string.records_count_subtitle,
                    AppCountFormat.grouped(count, locale),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        )
    }
}
