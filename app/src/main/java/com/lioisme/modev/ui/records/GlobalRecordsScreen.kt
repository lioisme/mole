package com.lioisme.modev.ui.records

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.ui.components.CollapsedSearchInput
import com.lioisme.modev.ui.components.KindFilterMenu
import com.lioisme.modev.ui.components.KindOption
import com.lioisme.modev.ui.components.SearchInputField
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.rememberSearchController
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.common.rememberLocaleAwareResources

/**
 * 全局统一记录页(V23 R1/G1):跨应用、跨类型的全部监听记录单一入口。
 * - 筛选维度(D6):关键字(SQL LIKE)+ 类型单选(全部/算法/数据库,
 *   SegmentedButton)+ 应用多选(ModalBottomSheet 选择器)+ 类别单选
 *   (仅类型唯一时可用,联动置灰);
 * - 列表渲染与滚动保持复用共享 [RecordTimelineList] / VM 级 listState;
 * - 激活筛选以 chip 呈现(应用数走 plurals 复数契约);
 * - 入口 = 应用列表顶栏(单一入口原则)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalRecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val records = viewModel.records.collectAsLazyPagingItems()
    val knownPackages by viewModel.knownPackages.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPackages by rememberSaveable { mutableStateOf(setOf<String>()) }
    var typeIndex by rememberSaveable { mutableStateOf(0) }
    var selectedKindName by rememberSaveable { mutableStateOf<String?>(null) }

    // D6:类别仅在类型单选(算法/数据库)时可选
    val selectedType = when (typeIndex) {
        1 -> RecordType.CRYPTO
        2 -> RecordType.DATABASE
        else -> null
    }
    val kindEnabled = selectedType != null
    val effectiveKindName = if (kindEnabled) selectedKindName else null

    val search = rememberSearchController<Long>(onNavigate = onOpenRecord)
    var kindMenuOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // 状态 → VM 单向下发(SQL 过滤单一事实源)
    LaunchedEffect(search.queryText, selectedKindName, typeIndex, selectedPackages) {
        viewModel.setQuery(search.queryText)
        viewModel.setTypeFilter(selectedType)
        viewModel.setKindName(effectiveKindName)
        viewModel.setPackages(selectedPackages)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
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
                                placeholder = stringResource(R.string.records_all_search),
                                filterSlot = {
                                    KindFilterMenu(
                                        expanded = kindMenuOpen,
                                        // 类型未选(全部)时类别入口禁用(D6):不展开
                                        onExpandedChange = { open ->
                                            if (kindEnabled || !open) {
                                                kindMenuOpen = open
                                            }
                                        },
                                        enabled = kindEnabled,
                                        selectedKindName = effectiveKindName,
                                        onKindSelect = { selectedKindName = it },
                                        kinds = kindsFor(selectedType),
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FilterSummaryRow(
                typeIndex = typeIndex,
                onTypeSelect = { index ->
                    typeIndex = index
                    // 切换类型后旧类别失效(D6 防跨类型残留)
                    selectedKindName = null
                },
                selectedPackages = selectedPackages,
                onOpenPicker = { pickerOpen = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RecordTimelineList(
                records = records,
                listState = viewModel.listState,
                onOpenRecord = onOpenRecord,
            )
        }
    }

    // 全屏搜索视图:Dialog 新窗口承载,复用同一分页流(数据流单一事实源)。
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
                    placeholder = stringResource(R.string.records_all_search),
                    filterSlot = {},
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
                onOpenRecord = { id -> search.collapseWithNavigation(id) },
            )
        }
    }

    AppPickerSheet(
        visible = pickerOpen,
        packages = knownPackages,
        selected = selectedPackages,
        onToggle = { pkg ->
            selectedPackages =
                if (pkg in selectedPackages) selectedPackages - pkg else selectedPackages + pkg
        },
        onClear = { selectedPackages = emptySet() },
        onDismiss = { pickerOpen = false },
    )
}

/** 类型 + 应用概览行:三段类型 SegmentedButton(D6)+ 应用多选 chip。 */
@Composable
private fun FilterSummaryRow(
    typeIndex: Int,
    onTypeSelect: (Int) -> Unit,
    selectedPackages: Set<String>,
    onOpenPicker: () -> Unit,
) {
    val res = rememberLocaleAwareResources()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            val labels = listOf(
                res.getString(R.string.filter_type_all),
                res.getString(R.string.filter_type_crypto),
                res.getString(R.string.filter_type_database),
            )
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = typeIndex == index,
                    onClick = { onTypeSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                    label = {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = selectedPackages.isNotEmpty(),
            onClick = onOpenPicker,
            label = {
                Text(
                    if (selectedPackages.isEmpty()) {
                        res.getString(R.string.filter_apps_any)
                    } else {
                        res.getQuantityString(R.plurals.filter_apps_selected, selectedPackages.size, selectedPackages.size)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(Icons.Filled.Groups, contentDescription = res.getString(R.string.cd_filter_apps))
            },
        )
    }
}

/**
 * 应用多选选择器(V23,契约 4:带键盘输入弹层一律 ModalBottomSheet +
 * imePadding,显式 surfaceContainerHigh):包名搜索过滤 + Checkbox 多选,
 * 勾选即时生效,底部清除 / 完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    visible: Boolean,
    packages: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val res = rememberLocaleAwareResources()
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, packages) {
        if (query.isBlank()) packages else packages.filter { it.contains(query, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp)) {
            Text(
                text = res.getString(R.string.app_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(res.getString(R.string.app_picker_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(top = 8.dp),
            ) {
                items(filtered, key = { it }) { pkg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(pkg) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = pkg in selected, onCheckedChange = null)
                        Text(
                            pkg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    onClear()
                    query = ""
                }) {
                    Text(res.getString(R.string.app_picker_clear))
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text(res.getString(R.string.app_picker_done))
                }
            }
        }
    }
}

/** 类型对应的类别选项(全部类型 → 空,配合 [KindFilterMenu.enabled] 置灰)。 */
private fun kindsFor(type: RecordType?): List<KindOption> = when (type) {
    RecordType.CRYPTO -> CryptoKind.entries.map { KindOption(it.name, kindLabelRes(it)) }
    RecordType.DATABASE -> SqlKind.entries.map { KindOption(it.name, sqlKindLabelRes(it)) }
    null -> emptyList()
}
