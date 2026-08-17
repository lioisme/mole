package com.lioisme.modev.ui.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.directionLabel
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.operationLabel
import com.lioisme.modev.ui.theme.kindColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记录详情页(2026-08-17 V7 信息架构收敛,对齐 LIDE LogcatDetailScreen 三态范式):
 * - 顶栏 = ModevTopBar(返回 + 标题=算法名,标题恒单行省略)+ 上一条/下一条/更多
 *   三个 TipIconButton(长按 tooltip);「更多」菜单唯一功能 = 导出 JSON
 *   (SAF CreateDocument 落盘,专业文件格式,后台构建 + 写流,失败 snackbar 兜底);
 * - 复制能力全部收敛到导出(逐区块复制/分享/复制文本已删除,单一出口);
 * - 三态契约:Loading 转圈 / Loaded 内容 / Failed(记录被容量清理或参数非法)
 *   error 文案 + 重试 Button,禁止误用「无结果」或无限转圈;
 * - 元数据 MetadataRow(label 定宽 + Monospace 值,类别行带语义色);
 * - 字节区块:预览 + 渐进披露(展开/收起),展开受屏幕渲染上限约束,
 *   超出提示「已省略 N 字节,导出获取完整数据」——大数据不闪退的核心;
 * - 列表虚拟化:LazyColumn 逐 Section item,仅组合可视区块;
 * - 密钥不可导出提示走主题 tertiary token。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    viewModel: RecordDetailViewModel,
    onBack: () -> Unit,
) {
    val record by viewModel.record.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val canOlder by viewModel.canOlder.collectAsStateWithLifecycle()
    val canNewer by viewModel.canNewer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showSnack(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // 导出落盘状态:后台构建的 JSON 在 SAF 回调中写流(唯一导出出口)。
    var exportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val json = exportJson ?: return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                    }
                }.onSuccess { showSnack(context.getString(R.string.export_ok)) }
                    .onFailure { showSnack(context.getString(R.string.export_failed)) }
            }
        }

    /** 导出:后台构建 JSON(主线程零构建成本),成功后弹 SAF 落盘,失败 snackbar 兜底。 */
    fun startExport(current: CryptoRecord) {
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { current.toJson() } }
                .onSuccess { json ->
                    exportJson = json
                    exportLauncher.launch("modev-record-${current.id}.json")
                }
                .onFailure { showSnack(context.getString(R.string.export_failed)) }
        }
    }

    Scaffold(
        topBar = {
            ModevTopBar(
                title = record?.algorithm ?: stringResource(R.string.record_title),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.record_prev),
                        onClick = viewModel::goOlder,
                        enabled = record != null && canOlder,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.record_prev))
                    }
                    TipIconButton(
                        tooltip = stringResource(R.string.record_next),
                        onClick = viewModel::goNewer,
                        enabled = record != null && canNewer,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.record_next))
                    }
                    // 更多菜单:唯一功能 = 导出 JSON(V7 信息架构收敛,无记录时禁用)。
                    val current = record
                    var moreMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        TipIconButton(
                            tooltip = stringResource(R.string.record_more),
                            onClick = { moreMenuOpen = true },
                            enabled = current != null,
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.record_more))
                        }
                        DropdownMenu(expanded = moreMenuOpen, onDismissRequest = { moreMenuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.record_export),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.FileDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    current?.let { startExport(it) }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            !loaded -> LoadingState(innerPadding)
            record == null -> FailedState(innerPadding, onRetry = viewModel::retry)
            else -> LoadedContent(
                record = record!!,
                innerPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun LoadingState(innerPadding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FailedState(innerPadding: PaddingValues, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.record_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

/**
 * 详情内容:LazyColumn 逐区块虚拟化(元数据/分隔线/各字节区块/堆栈),
 * 仅组合可视区块;字节区块展开态由宿主持有,展开数据按块惰性组合
 * ([byteSectionItems])——大数据记录不闪退、不冻结主线程。
 * 完整数据获取统一走顶栏「更多 → 导出 JSON」(复制能力已收敛)。
 */
@Composable
private fun LoadedContent(
    record: CryptoRecord,
    innerPadding: PaddingValues,
) {
    // 各字节区块展开态(LazyColumn 分块条目需在此读取;List<String> 可 saveable)。
    var expandedSections by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    fun toggleSection(key: String) {
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item(key = "meta") { MetadataBlock(record) }
        item(key = "divider") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        record.key?.let { key ->
            byteSectionItems(
                sectionKey = "key",
                titleRes = R.string.section_key,
                view = key,
                exportableNote = record.keyExportable,
                expanded = "key" in expandedSections,
                onToggleExpanded = { toggleSection("key") },
            )
        }
        record.keyAlias?.let { alias ->
            item(key = "section-key-alias") {
                SectionText(stringResource(R.string.section_key_alias), alias, monospace = true)
            }
        }
        record.iv?.let { iv ->
            byteSectionItems(
                sectionKey = "iv",
                titleRes = R.string.section_iv,
                view = iv,
                exportableNote = false,
                expanded = "iv" in expandedSections,
                onToggleExpanded = { toggleSection("iv") },
            )
        }
        record.salt?.let { salt ->
            byteSectionItems(
                sectionKey = "salt",
                titleRes = R.string.section_salt,
                view = salt,
                exportableNote = false,
                expanded = "salt" in expandedSections,
                onToggleExpanded = { toggleSection("salt") },
            )
        }
        record.aad?.let { aad ->
            byteSectionItems(
                sectionKey = "aad",
                titleRes = R.string.section_aad,
                view = aad,
                exportableNote = false,
                expanded = "aad" in expandedSections,
                onToggleExpanded = { toggleSection("aad") },
            )
        }
        record.publicKey?.let { pk ->
            byteSectionItems(
                sectionKey = "publicKey",
                titleRes = R.string.section_public_key,
                view = pk,
                exportableNote = false,
                expanded = "publicKey" in expandedSections,
                onToggleExpanded = { toggleSection("publicKey") },
            )
        }
        record.input?.let { input ->
            byteSectionItems(
                sectionKey = "input",
                titleRes = R.string.section_input,
                view = input,
                exportableNote = false,
                expanded = "input" in expandedSections,
                onToggleExpanded = { toggleSection("input") },
            )
        }
        record.output?.let { output ->
            byteSectionItems(
                sectionKey = "output",
                titleRes = R.string.section_output,
                view = output,
                exportableNote = false,
                expanded = "output" in expandedSections,
                onToggleExpanded = { toggleSection("output") },
            )
        }
        record.params?.takeIf { it.isNotEmpty() }?.let { params ->
            item(key = "section-params") {
                SectionText(
                    stringResource(R.string.section_params),
                    params.entries.joinToString("\n") { (k, v) -> "$k = $v" },
                    monospace = true,
                )
            }
        }
        if (record.stack.isNotEmpty()) {
            item(key = "stack-header") {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.section_stack),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(record.stack.size, key = { index -> "stack-frame-$index" }) { index ->
                Text(
                    record.stack[index],
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/** 元数据块:类别/操作/时间/上下文/方向/提供者/进程/验签/密钥可导出。 */
@Composable
private fun MetadataBlock(record: CryptoRecord) {
    Column {
        Spacer(Modifier.height(8.dp))
        MetadataRow(
            stringResource(R.string.meta_kind),
            stringResource(kindLabelRes(record.kind)),
            valueColor = kindColor(record.kind),
        )
        MetadataRow(stringResource(R.string.meta_operation), operationLabel(record.operation))
        MetadataRow(stringResource(R.string.meta_time), detailTimeFmt().format(Date(record.timestamp)))
        MetadataRow(stringResource(R.string.meta_context), "#${record.contextId}")
        record.direction?.let {
            MetadataRow(stringResource(R.string.meta_direction), directionLabel(it))
        }
        record.provider?.let {
            MetadataRow(stringResource(R.string.meta_provider), it)
        }
        record.processName?.let {
            MetadataRow(stringResource(R.string.meta_process), it)
        }
        record.verifyResult?.let {
            MetadataRow(
                stringResource(R.string.meta_verify),
                stringResource(if (it) R.string.verify_ok else R.string.verify_fail),
                valueColor = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (record.keyExportable) {
            MetadataRow(stringResource(R.string.meta_key_exportable), stringResource(R.string.confirm))
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 元数据行:label 定宽(中文 3 字宽度)+ Monospace 值,可带语义色(类别行)。 */
@Composable
private fun MetadataRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 字节区块(2026-08-18 V7 惰性分块;V7 复制收敛):本组件只渲染区块头部
 * (标题)与底部(字节数/展开收起/导出提示);展开态数据由宿主 LazyColumn
 * 经 [byteSectionItems] 按块惰性组合——消灭巨型 Text,修复实机展开
 * 100K 字符主线程 26s 冻结(ANR)问题。
 * 展开状态由宿主持有([LoadedContent]),跨重建经 rememberSaveable 存活。
 */
@Composable
internal fun ByteSection(
    title: String,
    view: BytesView,
    exportableNote: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    SelectionContainer {
        Column {
            view.text?.let { text ->
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
            }
            if (!expanded) {
                Text(
                    view.preview(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.bytes_count, view.totalLength) +
                if (view.truncated) stringResource(R.string.bytes_truncated) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onToggleExpanded) {
            Text(stringResource(if (expanded) R.string.section_collapse else R.string.section_expand))
        }
    }
    if (!exportableNote) {
        Text(
            stringResource(R.string.key_not_exportable),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** 展开态 hex 单块字符数(4K ≈ 96 行,单块布局/绘制毫秒级,与折叠预览上限同值)。 */
internal const val EXPAND_CHUNK_CHARS = 4096

/**
 * 在宿主 LazyColumn 中组合字节区块全部条目:头部单条 + 展开态分块条目。
 * 分块惰性:仅可视块被组合,滚动按需创建——无巨型 Text、无长帧。
 */
internal fun LazyListScope.byteSectionItems(
    sectionKey: String,
    titleRes: Int,
    view: BytesView,
    exportableNote: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    item(key = "section-$sectionKey-header") {
        ByteSection(
            title = stringResource(titleRes),
            view = view,
            exportableNote = exportableNote,
            expanded = expanded,
            onToggleExpanded = onToggleExpanded,
        )
    }
    if (expanded) {
        val chunks = view.hex.chunked(EXPAND_CHUNK_CHARS)
        items(chunks.size, key = { i -> "section-$sectionKey-chunk-$i" }) { i ->
            Text(
                chunks[i],
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionText(title: String, value: String, monospace: Boolean = false) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    SelectionContainer {
        Text(
            value,
            style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

/** 结构化 JSON(导出用,org.json 随 Android SDK 提供;V7 起为唯一复制/导出出口)。 */
internal fun CryptoRecord.toJson(): String = org.json.JSONObject().apply {
    put("packageName", packageName)
    put("kind", kind.name)
    put("operation", operation.name)
    put("algorithm", algorithm)
    provider?.let { put("provider", it) }
    direction?.let { put("direction", it) }
    key?.let { put("key", it.toJsonObject("key")) }
    keyAlias?.let { put("keyAlias", it) }
    put("keyExportable", keyExportable)
    iv?.let { put("iv", it.toJsonObject("iv")) }
    salt?.let { put("salt", it.toJsonObject("salt")) }
    aad?.let { put("aad", it.toJsonObject("aad")) }
    publicKey?.let { put("publicKey", it.toJsonObject("publicKey")) }
    params?.takeIf { it.isNotEmpty() }?.let { put("params", org.json.JSONObject(it)) }
    verifyResult?.let { put("verifyResult", it) }
    processName?.let { put("processName", it) }
    input?.let { put("input", it.toJsonObject("input")) }
    output?.let { put("output", it.toJsonObject("output")) }
    put("contextId", contextId)
    put("threadName", threadName)
    put("stack", org.json.JSONArray(stack))
    put("timestamp", timestamp)
}.toString(2)

private fun BytesView.toJsonObject(name: String): org.json.JSONObject = org.json.JSONObject().apply {
    put("name", name)
    put("hex", hex)
    text?.let { put("text", it) }
    put("totalLength", totalLength)
    put("truncated", truncated)
}

/**
 * 记录详情(2026-08-15 UI 重构 Phase 5,2026-08-17 V6 邻接投影):
 * 按 id 订阅单条记录;邻接导航仅查询邻居 id(投影),不再物化邻居大字段。
 * 三态契约:loaded 仅在首次真实查询结果到达后置 true;Failed 态提供 retry()。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordDetailViewModel(
    recordId: Long,
    private val logs: LogRepository,
) : ViewModel() {

    private val recordIdFlow = MutableStateFlow(recordId)
    private val retryTick = MutableStateFlow(0)

    /**
     * 原始查询流(stateIn 会把初始值 null 当作首发发射,导致 loaded 假阳性,
     * 因此这里直接收集 upstream,只把真实查询结果算作"已加载")。
     */
    private val query = combine(recordIdFlow, retryTick) { id, _ -> id }
        .flatMapLatest { id -> logs.observeById(id) }

    private val _record = MutableStateFlow<CryptoRecord?>(null)

    /** 当前展示的记录(真实查询结果,记录被清理后为 null)。 */
    val record: StateFlow<CryptoRecord?> = _record

    private val _loaded = MutableStateFlow(false)

    /** 首次真实查询结果是否已到达(false = 加载中)。 */
    val loaded: StateFlow<Boolean> = _loaded

    private val _canOlder = MutableStateFlow(false)
    private val _canNewer = MutableStateFlow(false)

    /** 同包内是否还有更旧的记录(上一条可用)。 */
    val canOlder: StateFlow<Boolean> = _canOlder

    /** 同包内是否还有更新的记录(下一条可用)。 */
    val canNewer: StateFlow<Boolean> = _canNewer

    init {
        viewModelScope.launch {
            query.collect { current ->
                _record.value = current
                _loaded.value = true
                if (current != null) {
                    _canOlder.value = logs.neighborIdOf(current, newer = false) != null
                    _canNewer.value = logs.neighborIdOf(current, newer = true) != null
                }
            }
        }
    }

    fun retry() {
        retryTick.value++
    }

    fun goOlder() {
        viewModelScope.launch { move(newer = false) }
    }

    fun goNewer() {
        viewModelScope.launch { move(newer = true) }
    }

    private suspend fun move(newer: Boolean) {
        val current = record.value ?: return
        logs.neighborIdOf(current, newer)?.let { recordIdFlow.value = it }
    }
}

/** 简单工厂,避免在 Compose 中手动拼参。 */
class RecordDetailViewModelFactory(
    private val recordId: Long,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordDetailViewModel(
            recordId = recordId,
            logs = container.logs,
        ) as T
    }
}

// 每次调用按当前 Locale 构造(语言切换重建后不残留旧 locale)。
private fun detailTimeFmt(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())