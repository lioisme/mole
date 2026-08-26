package com.lioisme.modev.ui.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.data.mapping.BehaviorDetail
import com.lioisme.modev.data.mapping.CryptoDetail
import com.lioisme.modev.data.mapping.RecordDetailModel
import com.lioisme.modev.data.mapping.SqlDetail
import com.lioisme.modev.data.mapping.toJson
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.behaviorKindLabelRes
import com.lioisme.modev.ui.components.behaviorOperationLabel
import com.lioisme.modev.ui.components.behaviorOperationLabelRes
import com.lioisme.modev.ui.components.directionLabel
import com.lioisme.modev.ui.components.displayAlgorithm
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.sqlHeadline
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.sqlOperationLabel
import com.lioisme.modev.ui.logs.detail.BehaviorHero
import com.lioisme.modev.ui.logs.detail.CryptoHero
import com.lioisme.modev.ui.logs.detail.DetailAnchorRow
import com.lioisme.modev.ui.logs.detail.SqlHero
import com.lioisme.modev.ui.logs.detail.behaviorAnchors
import com.lioisme.modev.ui.logs.detail.cryptoAnchors
import com.lioisme.modev.ui.logs.detail.sqlAnchors
import com.lioisme.modev.ui.logs.markdown.DetailCodeActions
import com.lioisme.modev.ui.logs.markdown.DetailFenceHandle
import com.lioisme.modev.ui.logs.markdown.FullscreenTextDialog
import com.lioisme.modev.ui.settings.StorageFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记录详情页(V26 markdown 化重构):
 * - 信息架构:Hero 身份卡(原生,D1 边界)→ 锚点芯片行 → 元数据 GFM 表 →
 *   数据流区(input→arrow→output)→ 密钥材料区 → 参数 → 调用栈;
 *   全部内容区块以单一 markdown 文档经 mikepenz 渲染(代码组标签页承接
 *   原三态表示切换,卡片限高滚动 + 复制/全屏出口);
 * - 顶栏 = ModevTopBar(标题=算法名恒单行)+ 上一条/下一条(id 投影邻接导航)
 *   + 更多(唯一功能 = 导出 JSON,SAF 后台构建 + 写流,双护栏预检);
 * - 性能红线:显示上限 128Ki(markdown 有界)/ 全屏 ChunkedText 虚拟化 /
 *   复制 4M / 导出 32M 闸门,数值语义与 V15 一致。
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
     * 整段复制统一出口(V13):写剪贴板 + Snackbar 反馈(区块名 + 字符数)。
     * V15 护栏:超 COPY_MAX_CHARS 字符拒写(ClipboardManager.setText 全量同步
     * 物化,超巨量会卡主线程),仅提示。V26 修复占位符缺参缺陷。
     */
    fun copyFull(
        label: String,
        fullText: String,
    ) {
        if (fullText.length > COPY_MAX_CHARS) {
            showSnack(context.getString(R.string.record_copy_too_large, fullText.length))
            return
        }
        clipboard.setText(AnnotatedString(fullText))
        showSnack(context.getString(R.string.record_copy_done, label, fullText.length))
    }

    // 全屏查看请求(V26 代码卡「展开」出口):句柄承载全量文本与标题。
    var fullscreenRequest by remember { mutableStateOf<DetailFenceHandle?>(null) }
    val codeActions =
        remember {
            DetailCodeActions(
                onCopyFull = { handle -> copyFull(handle.label, handle.fullText) },
                onFullscreen = { handle -> fullscreenRequest = handle },
            )
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
                is BehaviorDetail -> estimateBehaviorJsonChars(current.record)
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
                            is BehaviorDetail -> current.record.toJson()
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
                        is BehaviorDetail -> behaviorHeadline(current.record)
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
        // 详情列表状态:记录切换(上一条/下一条/重试)即回顶,避免滞留旧位置。
        val listState = rememberLazyListState()
        val currentRecordId = state.record?.id
        LaunchedEffect(currentRecordId) { listState.scrollToItem(0) }
        when {
            !state.loaded -> LoadingState(innerPadding)
            state.record == null -> FailedState(innerPadding, onRetry = viewModel::retry)
            else -> LoadedContent(
                record = state.record!!,
                innerPadding = innerPadding,
                listState = listState,
                actions = codeActions,
            )
        }
    }

    fullscreenRequest?.let { handle ->
        FullscreenTextDialog(
            title = handle.label,
            fullText = handle.fullText,
            onDismiss = { fullscreenRequest = null },
            onCopyFull = { text -> copyFull(handle.label, text) },
        )
    }
}

/** 详情内容分派(V17 双形态):算法走 CryptoLoadedContent,数据库走 SQL 渲染。 */
@Composable
private fun LoadedContent(
    record: RecordDetailModel,
    innerPadding: PaddingValues,
    listState: LazyListState,
    actions: DetailCodeActions,
) {
    when (record) {
        is CryptoDetail -> CryptoLoadedContent(record.record, innerPadding, listState, actions)
        is SqlDetail -> SqlLoadedContent(record.record, innerPadding, listState, actions)
        is BehaviorDetail -> BehaviorLoadedContent(record.record, innerPadding, listState, actions)
    }
}

/** 行为详情顶栏标题(路径 basename / 命令首段 / 操作名,恒非空)。 */
@Composable
private fun behaviorHeadline(record: BehaviorRecord): String {
    record.path?.trimEnd('/')?.ifEmpty { "/" }?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }?.let { return it }
    record.commandDisplay()?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return stringResource(behaviorOperationLabelRes(record.operation))
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
 * 锚点滚动装配辅助:锚点 key → LazyColumn item 索引簿记表。
 * 簿记在 LazyListScope DSL 同步执行期内完成(与条目发射严格同序),
 * 普通HashMap 承载(非快照态,不参与失效循环);点击时读取最近一次装配结果。
 */
private class AnchorBookkeeping(val listState: LazyListState) {
    val indexByKey = HashMap<String, Int>()
    var cursor = 0

    fun mark(key: String) {
        indexByKey[key] = cursor
    }

    fun advance(count: Int) {
        cursor += count
    }

    fun scrollTo(key: String, scope: CoroutineScope) {
        indexByKey[key]?.let { index ->
            scope.launch { listState.animateScrollToItem(index) }
        }
    }
}

/** 区块文档装配缓存键:title 已本地化,remember 以内容为键避免重组期重建文档。 */

/**
 * 算法记录详情内容(V26 markdown 装配):
 * hero → 锚点行 → 元数据表 → 数据流区(input→arrow→output)→ 密钥材料区
 * → 参数 → 调用栈;每个区块恒 1 item([markdownSectionItems])。
 */
@Composable
internal fun CryptoLoadedContent(
    record: CryptoRecord,
    innerPadding: PaddingValues,
    listState: LazyListState,
    actions: DetailCodeActions,
) {
    val res = LocalContext.current.resources

    // 元数据表条目(D1:语义色让位 markdown 表,身份徽章保留于 Hero 卡)
    val metaEntries =
        listOf(
            Pair(stringResource(R.string.meta_kind), stringResource(kindLabelRes(record.kind))),
            record.direction?.let { Pair(stringResource(R.string.meta_direction), directionLabel(it)) },
            record.provider?.let { Pair(stringResource(R.string.meta_provider), it) },
            record.processName?.let { Pair(stringResource(R.string.meta_process), it) },
            Pair(stringResource(R.string.meta_context), "#${record.contextId}"),
            Pair(stringResource(R.string.meta_thread), record.threadName),
            record.verifyResult?.let {
                Pair(
                    stringResource(R.string.meta_verify),
                    stringResource(if (it) R.string.verify_ok else R.string.verify_fail),
                )
            },
            if (record.keyExportable) Pair(stringResource(R.string.meta_key_exportable), stringResource(R.string.confirm)) else null,
        ).filterNotNull()
    val metaTable = metaTableDoc(metaEntries)
    val anchors = cryptoAnchors(record)

    // V16 R2:params 护栏化文本(纯函数),截断/省略标记改走围栏尾注段落。
    val paramsTitle = stringResource(R.string.section_params)
    val paramsDoc =
        record.params?.takeIf { it.isNotEmpty() }?.let { bundle ->
            val formatted = formatParamsText(bundle)
            val trailers =
                buildList {
                    if (formatted.truncatedValues > 0) add(res.getString(R.string.params_truncated))
                    if (formatted.omittedEntries > 0) {
                        add(res.getQuantityString(R.plurals.params_omitted, formatted.omittedEntries, formatted.omittedEntries))
                    }
                }
            textSectionDoc(paramsTitle, formatted.lines.joinToString("\n"), trailerLines = trailers)
        }

    val inputRole = stringResource(cryptoFlowRoles(record.kind, record.operation, record.direction).first ?: R.string.section_input)
    val outputRole = stringResource(cryptoFlowRoles(record.kind, record.operation, record.direction).second ?: R.string.section_output)
    val hasInput = record.input != null
    val hasOutput = record.output != null
    val hasFlowZone = hasInput && hasOutput

    // 区块文档预计算(组合期;@Composable 文档构建器不得进入 LazyListScope DSL 作用域):
    val keyTitle = stringResource(R.string.section_key)
    val keyAliasTitle = stringResource(R.string.section_key_alias)
    val ivTitle = stringResource(R.string.section_iv)
    val saltTitle = stringResource(R.string.section_salt)
    val aadTitle = stringResource(R.string.section_aad)
    val publicKeyTitle = stringResource(R.string.section_public_key)
    val stackTitle = stringResource(R.string.section_stack)

    val inputDoc = record.input?.let { byteSectionDoc(inputRole, it) }
    val outputDoc = record.output?.let { byteSectionDoc(outputRole, it) }
    val keyDoc = record.key?.let { byteSectionDoc(keyTitle, it) }
    val keyAliasDoc = record.keyAlias?.let { textSectionDoc(keyAliasTitle, it) }
    val ivDoc = record.iv?.let { byteSectionDoc(ivTitle, it) }
    val saltDoc = record.salt?.let { byteSectionDoc(saltTitle, it) }
    val aadDoc = record.aad?.let { byteSectionDoc(aadTitle, it) }
    val publicKeyDoc = record.publicKey?.let { byteSectionDoc(publicKeyTitle, it) }
    val stackDoc =
        if (record.stack.isEmpty()) {
            null
        } else {
            val frames = record.stack.take(STACK_MAX_FRAMES)
            val omittedFrames = record.stack.size - STACK_MAX_FRAMES
            val trailer =
                if (omittedFrames > 0) {
                    listOf(res.getQuantityString(R.plurals.stack_more, omittedFrames, omittedFrames))
                } else {
                    emptyList()
                }
            textSectionDoc(stackTitle, frames.joinToString("\n"), trailerLines = trailer)
        }

    val book = remember(record.id) { AnchorBookkeeping(listState) }
    val scrollScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        book.cursor = 0
        item(key = "hero") { CryptoHero(record) }
        book.advance(1)
        item(key = "anchors") {
            DetailAnchorRow(anchors = anchors, onAnchorClick = { book.scrollTo(it, scrollScope) })
        }
        book.advance(1)
        book.mark("meta")
        book.advance(metaTableItems("meta", metaTable))

        // V20 R4 数据关系分区:输入 → 流向箭头 → 输出;仅双侧齐备构成数据流。
        if (hasFlowZone) {
            book.mark("flow")
            item(key = "zone-dataflow") {
                ZoneHeader(icon = Icons.Filled.SwapVert, titleRes = R.string.section_zone_data_flow)
            }
            book.advance(1)
        }
        inputDoc?.let { doc ->
            book.advance(markdownSectionItems("input", doc, actions))
        }
        if (hasFlowZone) {
            item(key = "flow-arrow") { DetailFlowArrow() }
            book.advance(1)
        }
        outputDoc?.let { doc ->
            book.advance(markdownSectionItems("output", doc, actions))
        }

        // 密钥材料分区:key / keyAlias / iv / salt / aad / publicKey 归组。
        val hasKeyMaterial =
            keyDoc != null || keyAliasDoc != null || ivDoc != null ||
                saltDoc != null || aadDoc != null || publicKeyDoc != null
        if (hasKeyMaterial) {
            book.mark("keys")
            item(key = "zone-keymaterial") {
                ZoneHeader(icon = Icons.Filled.Key, titleRes = R.string.section_zone_key_material)
            }
            book.advance(1)
            keyDoc?.let { doc -> book.advance(markdownSectionItems("key", doc, actions)) }
            keyAliasDoc?.let { doc -> book.advance(markdownSectionItems("key-alias", doc, actions)) }
            ivDoc?.let { doc -> book.advance(markdownSectionItems("iv", doc, actions)) }
            saltDoc?.let { doc -> book.advance(markdownSectionItems("salt", doc, actions)) }
            aadDoc?.let { doc -> book.advance(markdownSectionItems("aad", doc, actions)) }
            publicKeyDoc?.let { doc -> book.advance(markdownSectionItems("publicKey", doc, actions)) }
        }
        paramsDoc?.let { doc ->
            book.mark("params")
            book.advance(markdownSectionItems("params", doc, actions))
        }
        stackDoc?.let { doc ->
            book.mark("stack")
            book.advance(markdownSectionItems("stack", doc, actions))
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * 数据库记录详情内容(V17 T11 引擎复用;V26 markdown 装配同款):
 * 元数据表 → SQL 语句(sql 围栏)→ 路径 → 绑定参数 → 结果集 → 结果文本
 * → 参数 → 调用栈,锚点键与内容一一对应。
 */
@Composable
private fun SqlLoadedContent(
    record: SqlRecord,
    innerPadding: PaddingValues,
    listState: LazyListState,
    actions: DetailCodeActions,
) {
    val res = LocalContext.current.resources

    val metaEntries =
        listOf(
            Pair(stringResource(R.string.meta_kind), stringResource(sqlKindLabelRes(record.kind))),
            Pair(stringResource(R.string.meta_operation), sqlOperationLabel(record.operation)),
            record.dbPath?.let { Pair(stringResource(R.string.meta_db_path), it) },
            Pair(stringResource(R.string.meta_context), "#${record.contextId}"),
            Pair(stringResource(R.string.meta_thread), record.threadName),
            record.rowCount?.let { Pair(stringResource(R.string.meta_rows), it.toString()) },
            record.affectedCount?.let { Pair(stringResource(R.string.meta_affected), it.toString()) },
            record.rowId?.let { Pair(stringResource(R.string.meta_row_id), it.toString()) },
        ).filterNotNull()
    val metaTable = metaTableDoc(metaEntries)
    val anchors = sqlAnchors(record)

    val bindArgsText = record.bindArgs?.takeIf { it.isNotEmpty() }?.let(::formatBindArgsText)
    val rowsText = record.rows?.takeIf { it.isNotEmpty() }?.let(::formatRowsText)
    val omittedBindRows = (record.bindArgs?.size ?: 0) - (bindArgsText?.size ?: 0)
    val omittedRows = (record.rows?.size ?: 0) - (rowsText?.size ?: 0)

    // 区块文档预计算(组合期;@Composable 文档构建器不得进入 LazyListScope DSL 作用域):
    val sqlTitle = stringResource(R.string.section_sql)
    val dbPathTitle = stringResource(R.string.section_db_path)
    val bindArgsTitle = stringResource(R.string.section_bind_args)
    val resultRowsTitle = stringResource(R.string.section_result_rows)
    val resultTitle = stringResource(R.string.section_result_text)
    val paramsTitle = stringResource(R.string.section_params)
    val stackTitle = stringResource(R.string.section_stack)

    val sqlDoc = record.sql?.let { textSectionDoc(sqlTitle, it, lang = "sql") }
    val dbPathDoc = record.dbPath?.let { textSectionDoc(dbPathTitle, it) }
    val bindArgsDoc =
        bindArgsText?.let { lines ->
            val trailers =
                if (omittedBindRows > 0) {
                    listOf(res.getQuantityString(R.plurals.rows_more, omittedBindRows, omittedBindRows))
                } else {
                    emptyList()
                }
            textSectionDoc(bindArgsTitle, lines.joinToString("\n"), trailerLines = trailers)
        }
    val rowsDoc =
        rowsText?.let { lines ->
            val trailers =
                if (omittedRows > 0) {
                    listOf(res.getQuantityString(R.plurals.rows_more, omittedRows, omittedRows))
                } else {
                    emptyList()
                }
            textSectionDoc(resultRowsTitle, lines.joinToString("\n"), trailerLines = trailers)
        }
    val resultDoc = record.resultText?.let { textSectionDoc(resultTitle, it) }
    val paramsDoc =
        record.params?.takeIf { it.isNotEmpty() }?.let { params ->
            val formatted = formatParamsText(params)
            val trailers =
                buildList {
                    if (formatted.truncatedValues > 0) add(res.getString(R.string.params_truncated))
                    if (formatted.omittedEntries > 0) {
                        add(res.getQuantityString(R.plurals.params_omitted, formatted.omittedEntries, formatted.omittedEntries))
                    }
                }
            textSectionDoc(paramsTitle, formatted.lines.joinToString("\n"), trailerLines = trailers)
        }
    val stackDoc =
        if (record.stack.isEmpty()) {
            null
        } else {
            val frames = record.stack.take(STACK_MAX_FRAMES)
            val omittedFrames = record.stack.size - STACK_MAX_FRAMES
            val trailer =
                if (omittedFrames > 0) {
                    listOf(res.getQuantityString(R.plurals.stack_more, omittedFrames, omittedFrames))
                } else {
                    emptyList()
                }
            textSectionDoc(stackTitle, frames.joinToString("\n"), trailerLines = trailer)
        }

    val book = remember(record.id) { AnchorBookkeeping(listState) }
    val scrollScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        book.cursor = 0
        item(key = "hero") { SqlHero(record) }
        book.advance(1)
        item(key = "anchors") {
            DetailAnchorRow(anchors = anchors, onAnchorClick = { book.scrollTo(it, scrollScope) })
        }
        book.advance(1)
        book.mark("meta")
        book.advance(metaTableItems("meta", metaTable))

        sqlDoc?.let { doc ->
            book.mark("sql")
            book.advance(markdownSectionItems("sql", doc, actions))
        }
        dbPathDoc?.let { doc ->
            book.mark("dbpath")
            book.advance(markdownSectionItems("dbpath", doc, actions))
        }
        bindArgsDoc?.let { doc ->
            book.mark("bindargs")
            book.advance(markdownSectionItems("bindargs", doc, actions))
        }
        rowsDoc?.let { doc ->
            book.mark("rows")
            book.advance(markdownSectionItems("rows", doc, actions))
        }
        resultDoc?.let { doc ->
            book.mark("result")
            book.advance(markdownSectionItems("result", doc, actions))
        }
        paramsDoc?.let { doc ->
            book.mark("params")
            book.advance(markdownSectionItems("params", doc, actions))
        }
        stackDoc?.let { doc ->
            book.mark("stack")
            book.advance(markdownSectionItems("stack", doc, actions))
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * 行为记录详情内容(V27 一等公民;V26 markdown 装配同款):
 * Hero → 锚点(命令/内容/参数/栈)→ 元数据表 → 命令(bash 围栏)→
 * 内容预览(三态字节代码组)→ 参数 → 调用栈,锚点键与内容一一对应。
 */
@Composable
private fun BehaviorLoadedContent(
    record: BehaviorRecord,
    innerPadding: PaddingValues,
    listState: LazyListState,
    actions: DetailCodeActions,
) {
    val res = LocalContext.current.resources

    val metaEntries =
        listOf(
            Pair(stringResource(R.string.meta_kind), stringResource(behaviorKindLabelRes(record.kind))),
            Pair(stringResource(R.string.meta_operation), behaviorOperationLabel(record.operation)),
            record.path?.let { Pair(stringResource(R.string.meta_path), it) },
            record.destPath?.let { Pair(stringResource(R.string.meta_dest_path), it) },
            record.fileSize?.let { Pair(stringResource(R.string.meta_file_size), StorageFormat.formatBytes(it)) },
            Pair(stringResource(R.string.meta_context), "#${record.contextId}"),
            Pair(stringResource(R.string.meta_thread), record.threadName),
        ).filterNotNull()
    val metaTable = metaTableDoc(metaEntries)
    val anchors = behaviorAnchors(record)

    // 区块文档预计算(组合期;@Composable 文档构建器不得进入 LazyListScope DSL 作用域):
    val commandTitle = stringResource(R.string.section_command)
    val contentTitle = stringResource(R.string.section_content)
    val paramsTitle = stringResource(R.string.section_params)
    val stackTitle = stringResource(R.string.section_stack)

    val commandDoc =
        record.command?.takeIf { it.isNotEmpty() }?.let { argv ->
            textSectionDoc(commandTitle, argv.joinToString("\n"), lang = "bash")
        }
    val contentDoc = record.content?.let { byteSectionDoc(contentTitle, it) }
    val paramsDoc =
        record.params?.takeIf { it.isNotEmpty() }?.let { params ->
            val formatted = formatParamsText(params)
            val trailers =
                buildList {
                    if (formatted.truncatedValues > 0) add(res.getString(R.string.params_truncated))
                    if (formatted.omittedEntries > 0) {
                        add(res.getQuantityString(R.plurals.params_omitted, formatted.omittedEntries, formatted.omittedEntries))
                    }
                }
            textSectionDoc(paramsTitle, formatted.lines.joinToString("\n"), trailerLines = trailers)
        }
    val stackDoc =
        if (record.stack.isEmpty()) {
            null
        } else {
            val frames = record.stack.take(STACK_MAX_FRAMES)
            val omittedFrames = record.stack.size - STACK_MAX_FRAMES
            val trailer =
                if (omittedFrames > 0) {
                    listOf(res.getQuantityString(R.plurals.stack_more, omittedFrames, omittedFrames))
                } else {
                    emptyList()
                }
            textSectionDoc(stackTitle, frames.joinToString("\n"), trailerLines = trailer)
        }

    val book = remember(record.id) { AnchorBookkeeping(listState) }
    val scrollScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        book.cursor = 0
        item(key = "hero") { BehaviorHero(record) }
        book.advance(1)
        item(key = "anchors") {
            DetailAnchorRow(anchors = anchors, onAnchorClick = { book.scrollTo(it, scrollScope) })
        }
        book.advance(1)
        book.mark("meta")
        book.advance(metaTableItems("meta", metaTable))

        commandDoc?.let { doc ->
            book.mark("command")
            book.advance(markdownSectionItems("command", doc, actions))
        }
        contentDoc?.let { doc ->
            book.mark("content")
            book.advance(markdownSectionItems("content", doc, actions))
        }
        paramsDoc?.let { doc ->
            book.mark("params")
            book.advance(markdownSectionItems("params", doc, actions))
        }
        stackDoc?.let { doc ->
            book.mark("stack")
            book.advance(markdownSectionItems("stack", doc, actions))
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}
