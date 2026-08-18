package com.lioisme.modev.ui.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
import com.lioisme.modev.core.util.ChunkedText
import com.lioisme.modev.data.mapping.toJson
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
 * 记录详情页(2026-08-17 V7 信息架构收敛,对齐 LIDE LogcatDetailScreen 三态范式;
 * V11 折叠语义 + 性能;V13 去折叠 + 整段复制):
 * - 顶栏 = ModevTopBar(返回 + 标题=算法名,标题恒单行省略)+ 上一条/下一条/更多
 *   三个 TipIconButton(长按 tooltip);「更多」菜单唯一功能 = 导出 JSON
 *   (SAF CreateDocument 落盘,专业文件格式,后台构建 + 写流,失败 snackbar 兜底);
 * - 三态契约:Loading 转圈 / Loaded 内容 / Failed(记录被容量清理或参数非法)
 *   error 文案 + 重试 Button,禁止误用「无结果」或无限转圈;
 * - 元数据 MetadataRow(label 定宽 + Monospace 值,类别行带语义色);
 * - 字节区块(V13):恒展开全量展示,无折叠/收起控件、无预览截断;hex 与 text
 *   均按 4K 字符/块惰性组合([ChunkedText]),无巨型单 Text、无主线程 chunk
 *   物化——大数据不闪退、滚动不卡顿的核心;
 * - 整段复制(V13):长按任意块复制该区块整段(hex 块复制全文 hex / text 块复制
 *   全文 text),触觉 + Snackbar(区块名 + 字符数);footer 复制按钮 = 无障碍与
 *   可发现入口(复制完整 hex);元数据块/别名/参数/堆栈保留原生选择;
 * - 列表虚拟化:LazyColumn 逐 Section item,仅组合可视区块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    viewModel: RecordDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showSnack(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val clipboard = LocalClipboardManager.current

    /**
     * V13 整段复制统一出口:写剪贴板 + Snackbar 反馈(区块名 + 字符数)。
     * 由字节区块长按/复制按钮调用,单一事实源,列表页摘要复制各自独立。
     */
    fun copyFull(titleRes: Int, fullText: String) {
        clipboard.setText(AnnotatedString(fullText))
        showSnack(context.getString(R.string.record_copy_done, context.getString(titleRes), fullText.length))
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
                title = state.record?.algorithm ?: stringResource(R.string.record_title),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.record_prev),
                        onClick = viewModel::goOlder,
                        enabled = state.record != null && state.canOlder,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.record_prev))
                    }
                    TipIconButton(
                        tooltip = stringResource(R.string.record_next),
                        onClick = viewModel::goNewer,
                        enabled = state.record != null && state.canNewer,
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.record_next))
                    }
                    // 更多菜单:唯一功能 = 导出 JSON(V7 信息架构收敛,无记录时禁用)。
                    val current = state.record
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
            !state.loaded -> LoadingState(innerPadding)
            state.record == null -> FailedState(innerPadding, onRetry = viewModel::retry)
            else -> LoadedContent(
                record = state.record!!,
                innerPadding = innerPadding,
                copyFull = ::copyFull,
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
 * 仅组合可视区块;字节区块恒展开,数据按块惰性组合([byteSectionItems])
 * ——大数据记录不闪退、不冻结主线程。
 * V13 去折叠:无折叠状态机、无预览截断、无展开/收起按钮,全量恒展示;
 * 整段复制经 [copyFull](区块名 + 完整文本)统一写剪贴板 + Snackbar。
 */
@Composable
private fun LoadedContent(
    record: CryptoRecord,
    innerPadding: PaddingValues,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
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
                copyFull = copyFull,
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
                copyFull = copyFull,
            )
        }
        record.salt?.let { salt ->
            byteSectionItems(
                sectionKey = "salt",
                titleRes = R.string.section_salt,
                view = salt,
                copyFull = copyFull,
            )
        }
        record.aad?.let { aad ->
            byteSectionItems(
                sectionKey = "aad",
                titleRes = R.string.section_aad,
                view = aad,
                copyFull = copyFull,
            )
        }
        record.publicKey?.let { pk ->
            byteSectionItems(
                sectionKey = "publicKey",
                titleRes = R.string.section_public_key,
                view = pk,
                copyFull = copyFull,
            )
        }
        record.input?.let { input ->
            byteSectionItems(
                sectionKey = "input",
                titleRes = R.string.section_input,
                view = input,
                copyFull = copyFull,
            )
        }
        record.output?.let { output ->
            byteSectionItems(
                sectionKey = "output",
                titleRes = R.string.section_output,
                view = output,
                copyFull = copyFull,
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
                SelectionContainer {
                    Text(
                        record.stack[index],
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/** 元数据块:类别/操作/时间/上下文/方向/提供者/进程/验签/密钥可导出(V12 起整块可长按复制)。 */
@Composable
private fun MetadataBlock(record: CryptoRecord) {
    // L9: 格式器每组合一次创建并缓存(语言切换整体重建组合,不残留旧 locale)。
    val timeFmt = remember { detailTimeFmt() }
    SelectionContainer {
        Column {
            Spacer(Modifier.height(8.dp))
            MetadataRow(
                stringResource(R.string.meta_kind),
                stringResource(kindLabelRes(record.kind)),
                valueColor = kindColor(record.kind),
            )
            MetadataRow(stringResource(R.string.meta_operation), operationLabel(record.operation))
            MetadataRow(stringResource(R.string.meta_time), timeFmt.format(Date(record.timestamp)))
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
 * 字节区块头部(V13 去折叠:无预览、无折叠按钮,恒全量惰性分块):
 * 标题 + footer(字节数(含捕获侧截断标记)+ 整段复制按钮)。
 * 复制按钮 = 完整 hex(无损承载),无障碍与可发现入口;text 表示经
 * text 分块长按复制(见 [byteSectionItems])。
 */
@Composable
internal fun ByteSectionHeader(
    title: String,
    view: BytesView,
    onCopyHex: () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.bytes_count, view.totalLength) +
                if (view.truncated) stringResource(R.string.bytes_truncated) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        TipIconButton(tooltip = stringResource(R.string.section_copy), onClick = onCopyHex) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.section_copy),
            )
        }
    }
}

/**
 * 在宿主 LazyColumn 中组合字节区块全部条目(V13 恒展开):头部单条 + text 分块
 * + hex 分块。分块惰性:仅可视块被组合,滚动按需创建([ChunkedText] O(1) 取块,
 * 零物化)——无巨型 Text、无长帧、无主线程 chunk 列表重复分配。
 * 复制:长按任意块 → [copyFull] 复制整段(hex 块复制全文 hex / text 块复制全文
 * text),宿主统一写剪贴板 + Snackbar;footer 复制按钮为无障碍/可发现入口。
 */
internal fun LazyListScope.byteSectionItems(
    sectionKey: String,
    titleRes: Int,
    view: BytesView,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    item(key = "section-$sectionKey-header") {
        ByteSectionHeader(
            title = stringResource(titleRes),
            view = view,
            onCopyHex = { copyFull(titleRes, view.hex) },
        )
    }
    view.text?.let { text ->
        val textChunks = ChunkedText(text)
        items(textChunks.size, key = { i -> "section-$sectionKey-text-chunk-$i" }) { i ->
            CopyableChunk(
                text = textChunks[i],
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.animateItem(),
                onLongPress = { copyFull(titleRes, text) },
            )
        }
    }
    val hexChunks = ChunkedText(view.hex)
    items(hexChunks.size, key = { i -> "section-$sectionKey-hex-chunk-$i" }) { i ->
        CopyableChunk(
            text = hexChunks[i],
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.animateItem(),
            onLongPress = { copyFull(titleRes, view.hex) },
        )
    }
}

/**
 * 可长按复制的分块文本(V13):无 semantics 污染(避免数百块全量成为 TalkBack
 * button 节点);长按 = 触觉反馈 + 宿主整段复制;无障碍与可发现入口 =
 * 区块 footer 复制按钮([ByteSectionHeader])。detectTapGestures 内建
 * tap/drag 判定,列表滚动不受影响。
 */
@Composable
private fun CopyableChunk(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    color: Color? = null,
    onLongPress: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Text(
        text = text,
        style = style,
        fontFamily = fontFamily,
        color = color ?: Color.Unspecified,
        modifier = modifier.pointerInput(text) {
            detectTapGestures(onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPress()
            })
        },
    )
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

/** 结构化 JSON 导出已移入数据映射层(data/mapping/CryptoRecordMappers.toJson)。 */

/**
 * 记录详情(2026-08-15 UI 重构 Phase 5,2026-08-17 V6 邻接投影;V11 L6 状态收敛):
 * 按 id 订阅单条记录;邻接导航仅查询邻居 id(投影),不再物化邻居大字段。
 * 三态契约由单一 [DetailUiState] 承载:loaded 仅在首次真实查询结果到达后置 true;
 * Failed(loaded && record == null)提供 retry()。
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

    private val _uiState = MutableStateFlow(DetailUiState())

    /** 详情页单一状态源(L6):记录 / 加载完成 / 上下条可用,一次收集整屏。 */
    val uiState: StateFlow<DetailUiState> = _uiState

    init {
        viewModelScope.launch {
            query.collect { current ->
                _uiState.value = DetailUiState(
                    record = current,
                    loaded = true,
                    canOlder = current != null && logs.neighborIdOf(current, newer = false) != null,
                    canNewer = current != null && logs.neighborIdOf(current, newer = true) != null,
                )
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
        val current = _uiState.value.record ?: return
        logs.neighborIdOf(current, newer)?.let { recordIdFlow.value = it }
    }
}

/**
 * 记录详情单一状态(L6 收敛):record=当前记录(null=不存在),loaded=首次真实查询
 * 结果已到达,canOlder/canNewer=同包邻接可用性。failed = 加载完成但记录缺失。
 */
data class DetailUiState(
    val record: CryptoRecord? = null,
    val loaded: Boolean = false,
    val canOlder: Boolean = false,
    val canNewer: Boolean = false,
) {
    val failed: Boolean get() = loaded && record == null
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