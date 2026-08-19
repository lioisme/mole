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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.ChunkedText
import com.lioisme.modev.data.mapping.CryptoDetail
import com.lioisme.modev.data.mapping.RecordDetailModel
import com.lioisme.modev.data.mapping.SqlDetail
import com.lioisme.modev.data.mapping.toJson
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.directionLabel
import com.lioisme.modev.ui.components.displayAlgorithm
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.operationLabel
import com.lioisme.modev.ui.components.sqlHeadline
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.sqlOperationLabel
import com.lioisme.modev.ui.theme.kindColor
import com.lioisme.modev.ui.theme.sqlKindColor
import com.webtoonscorp.android.readmore.material3.ReadMoreText
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
 * V11 折叠语义 + 性能;V13 去折叠 + 整段复制;V14 R2/R3 原生选择 + 组件库自动折叠):
 * - 顶栏 = ModevTopBar(返回 + 标题=算法名,标题恒单行省略)+ 上一条/下一条/更多
 *   三个 TipIconButton(长按 tooltip);「更多」菜单唯一功能 = 导出 JSON
 *   (SAF CreateDocument 落盘,专业文件格式,后台构建 + 写流,失败 snackbar 兜底);
 * - 三态契约:Loading 转圈 / Loaded 内容 / Failed(记录被容量清理或参数非法)
 *   error 文案 + 重试 Button,禁止误用「无结果」或无限转圈;
 * - 元数据 MetadataRow(label 定宽 + Monospace 值,类别行带语义色);
 * - 字节区块(V14/V15):>4096 字符自动收起(ReadMoreText 组件库「… 展开更多」内联,
 *   收起态仅喂前 4096 字符有界预览,零大布局);展开态按 [sectionRenderMode] 双档:
 *   Tier A(≤128K 字符)整段单 [SelectionContainer] 原生选择(系统「全选」一次覆盖
 *   整段);Tier B(>128K)hex 与 text 按 4K 字符/块惰性组合([ChunkedText]),
 *   无巨型单 Text、无主线程 chunk 物化——大数据不闪退、滚动不卡顿的核心;
 *   展开态尾部「收起」按钮回折叠态;短内容无折叠控件恒全量;
 * - 原生选择(V14 R2/V15):Tier A 整段选择;Tier B 每个分块包 [SelectionContainer],
 *   长按弹出系统选择手柄可自由框选;footer 复制按钮 = 无障碍与可发现入口
 *   (复制完整 hex);元数据块/别名/参数/堆栈保留原生选择;
 * - 列表虚拟化:LazyColumn 逐 Section item,仅组合可视区块。
 */

/**
 * 整段可选择的字符数上限(Tier A,128 KiB ≈ 13 万字符):单 Text 布局一次性成本
 * 可控(<100ms 级),覆盖绝大多数记录段;超过此值回退 Tier B 分块虚拟化(防 OOM)。
 */
internal const val SELECTABLE_CAP_CHARS = 128 * 1024

/** 详情页字节区块展开态渲染档位:Tier A 整段选择 / Tier B 分块虚拟化。 */
internal enum class SectionRenderMode { SINGLE_SELECTABLE, CHUNKED }

/** 档位判定(纯函数,单测覆盖):hex 字符数 ≤ 上限 → 整段可选,否则分块。 */
internal fun sectionRenderMode(hexChars: Int): SectionRenderMode =
    if (hexChars <= SELECTABLE_CAP_CHARS) {
        SectionRenderMode.SINGLE_SELECTABLE
    } else {
        SectionRenderMode.CHUNKED
    }

/** 复制护栏(V15):超此字符数拒写剪贴板(ClipboardManager 同步物化,防主线程卡死)。 */
internal const val COPY_MAX_CHARS = 4 * 1024 * 1024

/** 导出护栏(V15):超此字符数拒绝落盘(SAF 写流虽可行,但 32M+ JSON 无实际用途)。 */
internal const val EXPORT_MAX_CHARS = 32 * 1024 * 1024
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
     * V15 护栏:超 [COPY_MAX_CHARS] 字符拒写(ClipboardManager.setText 全量同步
     * 物化,超巨量会卡主线程),仅提示。
     */
    fun copyFull(titleRes: Int, fullText: String) {
        if (fullText.length > COPY_MAX_CHARS) {
            showSnack(context.getString(R.string.record_copy_too_large))
            return
        }
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

    /** 导出:先按 ×2 保守估算预检(V16 R2,超限免构建),通过后后台构建 JSON + SAF 落盘。 */
    fun startExport(current: RecordDetailModel) {
        val estimatedChars =
            when (current) {
                is CryptoDetail -> estimateJsonChars(current.record)
                is SqlDetail -> estimateSqlJsonChars(current.record)
            }
        if (estimatedChars > EXPORT_MAX_CHARS) {
            showSnack(context.getString(R.string.export_too_large, estimatedChars))
            return
        }
        scope.launch {
            val json =
                runCatching {
                    withContext(Dispatchers.Default) {
                        when (current) {
                            is CryptoDetail -> current.record.toJson()
                            is SqlDetail -> current.record.toJson()
                        }
                    }
                }
            json.onSuccess { text ->
                if (text.length > EXPORT_MAX_CHARS) {
                    showSnack(context.getString(R.string.export_too_large))
                    return@onSuccess
                }
                exportJson = text
                exportLauncher.launch("modev-record-${current.id}.json")
            }
                .onFailure { showSnack(context.getString(R.string.export_failed)) }
        }
    }

    Scaffold(
        topBar = {
            ModevTopBar(
                title =
                    when (val current = state.record) {
                        is CryptoDetail -> current.record.displayAlgorithm()
                        is SqlDetail -> current.record.sqlHeadline()
                        null -> stringResource(R.string.record_title)
                    },
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

/** 详情内容分派(V17 双形态):算法走原 [CryptoLoadedContent],数据库走 SQL 渲染。 */
@Composable
private fun LoadedContent(
    record: RecordDetailModel,
    innerPadding: PaddingValues,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    when (record) {
        is CryptoDetail -> CryptoLoadedContent(record.record, innerPadding, copyFull)
        is SqlDetail -> SqlLoadedContent(record.record, innerPadding, copyFull)
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
 * 仅组合可视区块;数据按块惰性组合([byteSectionItems])
 * ——大数据记录不闪退、不冻结主线程。
 * V14 R3:长区块自动收起(组件库 ReadMoreText 预览,按 sectionKey 记忆展开态,
 * rememberSaveable 进程重建保持);整段复制经 [copyFull](区块名 + 完整文本)
 * 统一写剪贴板 + Snackbar。
 */
@Composable
private fun CryptoLoadedContent(
    record: CryptoRecord,
    innerPadding: PaddingValues,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    // 长区块展开态记忆(V14 R3):默认收起,点「展开更多」置 true,「收起」置 false。
    var expandedSections by rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }
    fun sectionExpanded(sectionKey: String): Boolean = expandedSections[sectionKey] == true
    fun setSectionExpanded(
        sectionKey: String,
        value: Boolean,
    ) {
        expandedSections = if (value) expandedSections + (sectionKey to true) else expandedSections - sectionKey
    }

    // V16 R2:params 护栏化文本(纯函数 + 本地化省略标记,组合上下文完成,DSL 内只消费)。
    val res = LocalContext.current.resources
    val paramsValue: String? = remember(record) {
        record.params?.takeIf { it.isNotEmpty() }?.let { formatParamsText(it) }
    }?.let { text ->
        val truncatedMarker = if (text.truncatedValues > 0) stringResource(R.string.params_truncated) else null
        val omittedMarker =
            if (text.omittedEntries > 0) {
                res.getQuantityString(R.plurals.params_omitted, text.omittedEntries, text.omittedEntries)
            } else {
                null
            }
        buildString {
            text.lines.forEach { append(it).append('\n') }
            truncatedMarker?.let { append(it).append('\n') }
            omittedMarker?.let { append(it) }
        }
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
                expanded = sectionExpanded("key"),
                onExpandedChange = { setSectionExpanded("key", it) },
                copyFull = copyFull,
            )
        }
        record.keyAlias?.let { alias ->
            textSectionItems(
                sectionKey = "key-alias",
                titleRes = R.string.section_key_alias,
                value = alias,
                expanded = sectionExpanded("key-alias"),
                onExpandedChange = { setSectionExpanded("key-alias", it) },
                copyFull = copyFull,
            )
        }
        record.iv?.let { iv ->
            byteSectionItems(
                sectionKey = "iv",
                titleRes = R.string.section_iv,
                view = iv,
                expanded = sectionExpanded("iv"),
                onExpandedChange = { setSectionExpanded("iv", it) },
                copyFull = copyFull,
            )
        }
        record.salt?.let { salt ->
            byteSectionItems(
                sectionKey = "salt",
                titleRes = R.string.section_salt,
                view = salt,
                expanded = sectionExpanded("salt"),
                onExpandedChange = { setSectionExpanded("salt", it) },
                copyFull = copyFull,
            )
        }
        record.aad?.let { aad ->
            byteSectionItems(
                sectionKey = "aad",
                titleRes = R.string.section_aad,
                view = aad,
                expanded = sectionExpanded("aad"),
                onExpandedChange = { setSectionExpanded("aad", it) },
                copyFull = copyFull,
            )
        }
        record.publicKey?.let { pk ->
            byteSectionItems(
                sectionKey = "publicKey",
                titleRes = R.string.section_public_key,
                view = pk,
                expanded = sectionExpanded("publicKey"),
                onExpandedChange = { setSectionExpanded("publicKey", it) },
                copyFull = copyFull,
            )
        }
        record.input?.let { input ->
            byteSectionItems(
                sectionKey = "input",
                titleRes = R.string.section_input,
                view = input,
                expanded = sectionExpanded("input"),
                onExpandedChange = { setSectionExpanded("input", it) },
                copyFull = copyFull,
            )
        }
        record.output?.let { output ->
            byteSectionItems(
                sectionKey = "output",
                titleRes = R.string.section_output,
                view = output,
                expanded = sectionExpanded("output"),
                onExpandedChange = { setSectionExpanded("output", it) },
                copyFull = copyFull,
            )
        }
        if (paramsValue != null) {
            textSectionItems(
                sectionKey = "params",
                titleRes = R.string.section_params,
                value = paramsValue,
                expanded = sectionExpanded("params"),
                onExpandedChange = { setSectionExpanded("params", it) },
                copyFull = copyFull,
            )
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
            // V16 R2 护栏:最多渲染 STACK_MAX_FRAMES 帧(近因优先),超出追加本地化标记。
            val frames = record.stack.take(STACK_MAX_FRAMES)
            items(frames.size, key = { index -> "stack-frame-$index" }) { index ->
                SelectionContainer {
                    Text(
                        frames[index],
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (record.stack.size > STACK_MAX_FRAMES) {
                val omittedFrames = record.stack.size - STACK_MAX_FRAMES
                item(key = "stack-more") {
                    Text(
                        res.getQuantityString(R.plurals.stack_more, omittedFrames, omittedFrames),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * 数据库记录详情内容(V17 T11):SQL 语句 / 绑定参数 / 结果集 / 元数据,
 * 复用既有分档渲染设施(textSectionItems 双层渲染 + 4M 复制护栏):
 * - SQL 原文缺失时整节隐藏(不渲染空标题);
 * - bindArgs / rows / resultText 以纯函数格式化(有界行数/列数护栏)为文本节;
 * - 结果行超限计数由 UI 追加本地化省略标记(不截断原始数据)。
 */
@Composable
private fun SqlLoadedContent(
    record: SqlRecord,
    innerPadding: PaddingValues,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    var expandedSections by rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }
    fun sectionExpanded(sectionKey: String): Boolean = expandedSections[sectionKey] == true
    fun setSectionExpanded(
        sectionKey: String,
        value: Boolean,
    ) {
        expandedSections = if (value) expandedSections + (sectionKey to true) else expandedSections - sectionKey
    }
    val res = LocalContext.current.resources

    val bindArgsText = record.bindArgs?.takeIf { it.isNotEmpty() }?.let(::formatBindArgsText)
    val rowsText = record.rows?.takeIf { it.isNotEmpty() }?.let(::formatRowsText)
    val omittedBindRows = (record.bindArgs?.size ?: 0) - (bindArgsText?.size ?: 0)
    val omittedRows = (record.rows?.size ?: 0) - (rowsText?.size ?: 0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item(key = "sql-meta") { SqlMetadataBlock(record) }
        item(key = "divider") {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
        record.sql?.let { sql ->
            textSectionItems(
                sectionKey = "sql",
                titleRes = R.string.section_sql,
                value = sql,
                expanded = sectionExpanded("sql"),
                onExpandedChange = { setSectionExpanded("sql", it) },
                copyFull = copyFull,
            )
        }
        record.dbPath?.let { path ->
            textSectionItems(
                sectionKey = "dbpath",
                titleRes = R.string.section_db_path,
                value = path,
                expanded = sectionExpanded("dbpath"),
                onExpandedChange = { setSectionExpanded("dbpath", it) },
                copyFull = copyFull,
            )
        }
        bindArgsText?.let { lines ->
            val body =
                buildString {
                    lines.forEach { append(it).append('\n') }
                    if (omittedBindRows > 0) {
                        append(res.getQuantityString(R.plurals.rows_more, omittedBindRows, omittedBindRows))
                    }
                }.trimEnd('\n')
            textSectionItems(
                sectionKey = "bindargs",
                titleRes = R.string.section_bind_args,
                value = body,
                expanded = sectionExpanded("bindargs"),
                onExpandedChange = { setSectionExpanded("bindargs", it) },
                copyFull = copyFull,
            )
        }
        rowsText?.let { lines ->
            val body =
                buildString {
                    lines.forEach { append(it).append('\n') }
                    if (omittedRows > 0) {
                        append(res.getQuantityString(R.plurals.rows_more, omittedRows, omittedRows))
                    }
                }.trimEnd('\n')
            textSectionItems(
                sectionKey = "rows",
                titleRes = R.string.section_result_rows,
                value = body,
                expanded = sectionExpanded("rows"),
                onExpandedChange = { setSectionExpanded("rows", it) },
                copyFull = copyFull,
            )
        }
        record.resultText?.let { text ->
            textSectionItems(
                sectionKey = "result",
                titleRes = R.string.section_result_text,
                value = text,
                expanded = sectionExpanded("result"),
                onExpandedChange = { setSectionExpanded("result", it) },
                copyFull = copyFull,
            )
        }
        record.params?.takeIf { it.isNotEmpty() }?.let { params ->
            val text = formatParamsText(params)
            val body = buildString {
                text.lines.forEach { append(it).append('\n') }
                if (text.omittedEntries > 0) {
                    append(res.getQuantityString(R.plurals.params_omitted, text.omittedEntries, text.omittedEntries))
                }
            }.trimEnd('\n')
            textSectionItems(
                sectionKey = "params",
                titleRes = R.string.section_params,
                value = body,
                expanded = sectionExpanded("params"),
                onExpandedChange = { setSectionExpanded("params", it) },
                copyFull = copyFull,
            )
        }
        if (record.stack.isNotEmpty()) {
            item(key = "sql-stack-header") {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.section_stack),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val frames = record.stack.take(STACK_MAX_FRAMES)
            items(frames.size, key = { index -> "sql-stack-frame-$index" }) { index ->
                SelectionContainer {
                    Text(
                        frames[index],
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (record.stack.size > STACK_MAX_FRAMES) {
                val omittedFrames = record.stack.size - STACK_MAX_FRAMES
                item(key = "sql-stack-more") {
                    Text(
                        res.getQuantityString(R.plurals.stack_more, omittedFrames, omittedFrames),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/** 数据库记录元数据块:类别/操作/数据库/路径/时间/上下文/进程/线程/行数元数据。 */
@Composable
private fun SqlMetadataBlock(record: SqlRecord) {
    val context = LocalContext.current
    val timeFmt = remember(context) { detailTimeFmt() }
    SelectionContainer {
        Column {
            Spacer(Modifier.height(8.dp))
            MetadataRow(
                stringResource(R.string.meta_kind),
                stringResource(sqlKindLabelRes(record.kind)),
                valueColor = sqlKindColor(record.kind),
            )
            MetadataRow(stringResource(R.string.meta_operation), sqlOperationLabel(record.operation))
            MetadataRow(stringResource(R.string.meta_db_name), record.databaseName)
            record.dbPath?.let { MetadataRow(stringResource(R.string.meta_db_path), it) }
            MetadataRow(stringResource(R.string.meta_time), timeFmt.format(Date(record.timestamp)))
            MetadataRow(stringResource(R.string.meta_context), "#${record.contextId}")
            record.processName?.let { MetadataRow(stringResource(R.string.meta_process), it) }
            MetadataRow(stringResource(R.string.meta_thread), record.threadName)
            record.rowCount?.let { MetadataRow(stringResource(R.string.meta_rows), it.toString()) }
            record.affectedCount?.let { MetadataRow(stringResource(R.string.meta_affected), it.toString()) }
            record.rowId?.let { MetadataRow(stringResource(R.string.meta_row_id), it.toString()) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 元数据块:类别/操作/时间/上下文/方向/提供者/进程/验签/密钥可导出(V12 起整块可长按复制)。 */
@Composable
private fun MetadataBlock(record: CryptoRecord) {
    // L9/V15:格式器按包装 Context 缓存(语言切换 → Context 换实例 → 重建格式器,
    // 不残留旧 locale;与 RecordRow 同规)。
    val context = LocalContext.current
    val timeFmt = remember(context) { detailTimeFmt() }
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
 * 字节区块头部(V14:恒展示,不随折叠态变化;V16 R3 复制菜单化):
 * 标题 + footer(字节数(含捕获侧截断标记)+ 复制菜单按钮)。
 * 复制菜单 = 文本 / Hex / 文本+Hex 三选(V16 R3:文本与 Hex 双承载,
 * Tier B 分块无法跨块框选,复制菜单是全量复制唯一入口);
 * 文本缺失或目标超 [COPY_MAX_CHARS] 的项隐藏(不留空白项,不产生死按钮)。
 */
@Composable
internal fun ByteSectionHeader(
    title: String,
    view: BytesView,
    onCopyText: (() -> Unit)?,
    onCopyHex: () -> Unit,
    onCopyAll: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
        if (onCopyText != null || onCopyAll != null) {
            TipIconButton(tooltip = stringResource(R.string.section_copy), onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.section_copy),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                onCopyText?.let { copy ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.section_copy_text)) },
                        onClick = {
                            menuOpen = false
                            copy()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.section_copy_hex)) },
                    onClick = {
                        menuOpen = false
                        onCopyHex()
                    },
                )
                onCopyAll?.let { copy ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.section_copy_all)) },
                        onClick = {
                            menuOpen = false
                            copy()
                        },
                    )
                }
            }
        }
    }
}

/**
 * 在宿主 LazyColumn 中组合字节区块全部条目(V14 R3/V15 双层渲染):
 * - 短内容(≤ 4096 字符):头部单条 + 整段单 Text(Tier A 无折叠控件,恒全量);
 * - 长内容(> 4096 字符):默认收起——头部 + 单条预览(前 4096 字符有界文本,
 *   ReadMoreText 组件库渲染「… 展开更多」内联链接);展开后按 [sectionRenderMode]:
 *   Tier A(≤ 128K 字符)= 整段单 [SelectionContainer] 原生选择,系统「全选」一次覆盖
 *   整段;Tier B(> 128K 字符)= text/hex 分块(惰性,仅可视块组合,[ChunkedText]
 *   O(1) 取块,零物化)+ 尾部「收起」按钮。
 * 复制:footer 复制菜单(V16 R3)= 文本 / Hex / 文本+Hex 三选(超 4M 或文本缺失项
 * 隐藏);Tier A 整段原生选择;Tier B 分块原生选择(V14 R2 替代 V13 长按整段复制)。
 */
internal fun LazyListScope.byteSectionItems(
    sectionKey: String,
    titleRes: Int,
    view: BytesView,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    item(key = "section-$sectionKey-header") {
        val allText = buildString {
            view.text?.let { append(it).append('\n') }
            append(view.hex)
        }
        ByteSectionHeader(
            title = stringResource(titleRes),
            view = view,
            // V16 R3:菜单项按可用性收敛——文本缺失 / 超复制护栏的项隐藏
            onCopyText =
                view.text
                    ?.takeIf { it.length <= COPY_MAX_CHARS }
                    ?.let { text -> { copyFull(titleRes, text) } },
            onCopyHex = { copyFull(titleRes, view.hex) },
            onCopyAll =
                if (allText.length <= COPY_MAX_CHARS) {
                    { copyFull(titleRes, allText) }
                } else {
                    null
                },
        )
    }
    val exceeds = view.hex.length > ChunkedText.DEFAULT_CHUNK_CHARS
    if (!exceeds) {
        // 短内容:整段单 Text 恒全量展示(Tier A),无折叠控件。
        item(key = "section-$sectionKey-content") { SelectableSectionContent(view) }
        return
    }
    if (!expanded) {
        item(key = "section-$sectionKey-preview") {
            SectionPreview(titleRes = titleRes, view = view, onExpand = { onExpandedChange(true) })
        }
    } else {
        if (sectionRenderMode(view.hex.length) == SectionRenderMode.SINGLE_SELECTABLE) {
            // Tier A:整段单 SelectionContainer,一次手势选中全部。
            item(key = "section-$sectionKey-content") { SelectableSectionContent(view) }
        } else {
            // Tier B:分块虚拟化(跨 item 连续选择为平台限制,复制按钮承载全选语义)。
            byteSectionChunks(sectionKey, view)
        }
        item(key = "section-$sectionKey-collapse") {
            TextButton(onClick = { onExpandedChange(false) }) {
                Text(stringResource(R.string.section_collapse))
            }
        }
    }
}

/**
 * 整段可选中内容(Tier A,V15):单一 [SelectionContainer] 包裹 text + hex 单 Text,
 * 系统长按手柄 / 「全选」一次覆盖整段(满足「同一段文本可选中全部」);
 * 字符数由 [sectionRenderMode] 保证有界(≤ [SELECTABLE_CAP_CHARS]),
 * 单 Text 布局一次性成本可控,不引入分块碎片化选择。
 */
@Composable
private fun SelectableSectionContent(view: BytesView) {
    SelectionContainer {
        Column {
            view.text?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Text(
                text = view.hex,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}

/**
 * 字节区块 Tier B 展开态分块(text 优先,hex 随后):每块包 [SelectionContainer] 提供
 * 原生选择(跨 LazyColumn item 连续选择为平台限制,Tier A 整段选择承载全量框选)。
 */
private fun LazyListScope.byteSectionChunks(
    sectionKey: String,
    view: BytesView,
) {
    view.text?.let { text ->
        val textChunks = ChunkedText(text)
        items(textChunks.size, key = { i -> "section-$sectionKey-text-chunk-$i" }) { i ->
            SelectionContainer {
                Text(
                    text = textChunks[i],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    val hexChunks = ChunkedText(view.hex)
    items(hexChunks.size, key = { i -> "section-$sectionKey-hex-chunk-$i" }) { i ->
        SelectionContainer {
            Text(
                text = hexChunks[i],
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * 长区块收起态预览(V14 R3):仅喂前 [ChunkedText.DEFAULT_CHUNK_CHARS] 字符
 * 有界文本(text 优先,无 text 用 hex),ReadMoreText(collapsedMaxLines=4)
 * 渲染「… [record_see_more]」内联链接,点按即展开——预览零大布局、零性能回退。
 * 预览不包 SelectionContainer(原生选择仅在展开态分块上,避免手势冲突)。
 */
@Composable
private fun SectionPreview(
    titleRes: Int,
    view: BytesView,
    onExpand: () -> Unit,
) {
    val limit = ChunkedText.DEFAULT_CHUNK_CHARS
    val hasText = view.text != null
    val preview: String =
        if (hasText) {
            view.text!!.take(limit)
        } else {
            view.hex.take(limit)
        }
    val style =
        if (hasText) {
            MaterialTheme.typography.bodyMedium
        } else {
            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    ReadMoreText(
        text = preview,
        expanded = false,
        onExpandedChange = { if (it) onExpand() },
        readMoreText = stringResource(R.string.record_see_more),
        readMoreColor = MaterialTheme.colorScheme.primary,
        readMoreMaxLines = 4,
        style = style,
    )
}

/**
 * 文本区块条目组合(V16 R2 空/大数据健壮性:keyAlias / params 等纯文本区块,
 * 与字节区块同规的分档渲染):
 * - ≤ 4096 字符:标题(含复制按钮)+ 整段单 Text(恒全量,无折叠控件);
 * - > 4096 字符:默认收起——有界预览(ReadMoreText「… 展开更多」内联);
 *   展开按 [sectionRenderMode] 分档:Tier A 整段单 [SelectionContainer] 原生选择 /
 *   Tier B [ChunkedText] 分块虚拟化;尾部「收起」按钮;
 * - 复制:标题行复制按钮 = 完整文本(经 [copyFull] 4M 护栏)。
 */
internal fun LazyListScope.textSectionItems(
    sectionKey: String,
    titleRes: Int,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    item(key = "section-$sectionKey-header") {
        TextSectionHeader(
            title = stringResource(titleRes),
            onCopy = { copyFull(titleRes, value) },
        )
    }
    val exceeds = value.length > ChunkedText.DEFAULT_CHUNK_CHARS
    if (!exceeds) {
        item(key = "section-$sectionKey-content") { TextSectionContent(value) }
        return
    }
    if (!expanded) {
        item(key = "section-$sectionKey-preview") {
            TextSectionPreview(value = value, onExpand = { onExpandedChange(true) })
        }
    } else {
        if (sectionRenderMode(value.length) == SectionRenderMode.SINGLE_SELECTABLE) {
            item(key = "section-$sectionKey-content") { TextSectionContent(value) }
        } else {
            val chunks = ChunkedText(value)
            items(chunks.size, key = { i -> "section-$sectionKey-chunk-$i" }) { i ->
                SelectionContainer {
                    Text(
                        text = chunks[i],
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
        item(key = "section-$sectionKey-collapse") {
            TextButton(onClick = { onExpandedChange(false) }) {
                Text(stringResource(R.string.section_collapse))
            }
        }
    }
}

/** 文本区块头部:标题 + 尾部复制按钮(经 copyFull 护栏;对齐字节区块可发现入口)。 */
@Composable
private fun TextSectionHeader(title: String, onCopy: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TipIconButton(tooltip = stringResource(R.string.section_copy), onClick = onCopy) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.section_copy),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** 文本区块整段内容(短 / Tier A 展开态):单 Text 可原生选择。 */
@Composable
private fun TextSectionContent(value: String) {
    SelectionContainer {
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
    }
}

/** 文本区块收起态预览(有界前 4096 字符,ReadMoreText 内联展开,零大布局)。 */
@Composable
private fun TextSectionPreview(value: String, onExpand: () -> Unit) {
    ReadMoreText(
        text = value.take(ChunkedText.DEFAULT_CHUNK_CHARS),
        expanded = false,
        onExpandedChange = { if (it) onExpand() },
        readMoreText = stringResource(R.string.record_see_more),
        readMoreColor = MaterialTheme.colorScheme.primary,
        readMoreMaxLines = 4,
        style =
            MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
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
                    canOlder = current != null &&
                        logs.neighborIdOf(current.recordType, current.packageName, current.timestamp, current.id, newer = false) != null,
                    canNewer = current != null &&
                        logs.neighborIdOf(current.recordType, current.packageName, current.timestamp, current.id, newer = true) != null,
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
        logs.neighborIdOf(current.recordType, current.packageName, current.timestamp, current.id, newer)
            ?.let { recordIdFlow.value = it }
    }
}

/**
 * 记录详情单一状态(L6 收敛):record=当前记录(null=不存在),loaded=首次真实查询
 * 结果已到达,canOlder/canNewer=同包邻接可用性。failed = 加载完成但记录缺失。
 * V17:record 泛化为 [RecordDetailModel](算法/数据库双形态)。
 */
data class DetailUiState(
    val record: RecordDetailModel? = null,
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