package com.lioisme.modev.ui.logs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.data.mapping.BehaviorDetail
import com.lioisme.modev.data.mapping.CryptoDetail
import com.lioisme.modev.data.mapping.PrefsDetail
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
import com.lioisme.modev.ui.components.operationLabel
import com.lioisme.modev.ui.components.prefsKindLabelRes
import com.lioisme.modev.ui.components.prefsOperationLabel
import com.lioisme.modev.ui.components.prefsOperationLabelRes
import com.lioisme.modev.ui.components.sqlHeadline
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.sqlOperationLabel
import com.lioisme.modev.ui.logs.detail.behaviorAnchors
import com.lioisme.modev.ui.logs.detail.cryptoAnchors
import com.lioisme.modev.ui.logs.detail.prefsAnchors
import com.lioisme.modev.ui.logs.detail.sqlAnchors
import com.lioisme.modev.ui.logs.markdown.DetailCodeActions
import com.lioisme.modev.ui.logs.markdown.DetailFenceHandle
import com.lioisme.modev.ui.logs.markdown.FLOW_ARROW_LINE
import com.lioisme.modev.ui.logs.markdown.FullscreenTextDialog
import com.lioisme.modev.ui.logs.markdown.MarkdownLangs
import com.lioisme.modev.ui.logs.markdown.mdSectionHeading
import com.lioisme.modev.ui.settings.StorageFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记录详情页(V28 纯 markdown 化重构):
 * - 信息架构:Appbar 以下全域 100% markdown 承载 —— 身份区(## 标题 + 粗体上下文行,
 *   取代 V24 原生 Hero 卡)→ 目录(TOC markdown 链接列表,anchor:// 经 LocalUriHandler
 *   拦截滚动,取代 V24 FilterChip 锚点行)→ 内容节(H2 节根 + 围栏/GFM 表,
 *   ZoneHeader 色带与流向图标行退役为纯 markdown);
 * - 代码块扁平化(去卡)+ Highlights 引擎语法高亮(json/bash,M3 同源主题色,
 *   >32Ki 降级);params/bindArgs/rows 以真实 JSON 承载(D3);
 * - 顶栏 = ModevTopBar(标题=算法名恒单行)+ 上一条/下一条(id 投影邻接导航)
 *   + 更多(唯一功能 = 导出 JSON,SAF 后台构建 + 写流,双护栏预检);
 * - 性能红线:显示上限 128Ki(markdown 有界)/ 全屏 ChunkedText 虚拟化 /
 *   复制 4M / 导出 32M / 高亮 32Ki,数值语义与 V15/V28 一致。
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
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                            writer.write(json)
                        }
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
                is PrefsDetail -> estimatePrefsJsonChars(current.record)
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
                            is PrefsDetail -> current.record.toJson()
                        }
                    }
                }
            json.onSuccess { text ->
                if (text.length > EXPORT_MAX_CHARS) {
                    // V28 修复:二次拒绝同样携带格式参(F9 存量缺陷,占位符缺参曾渲染字面量)。
                    showSnack(context.getString(R.string.export_too_large, text.length))
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
                title = stringResource(R.string.record_title),
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

/** 详情内容分派(V17 双形态 + V27 行为;V28 装配统一 identity→toc→节序列)。 */
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
        is PrefsDetail -> PrefsLoadedContent(record.record, innerPadding, listState, actions)
    }
}

/** 行为详情顶栏标题(路径 basename / 命令首段 / 操作名,恒非空)。 */
@Composable
internal fun behaviorHeadline(record: BehaviorRecord): String {
    record.path?.trimEnd('/')?.ifEmpty { "/" }?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }?.let { return it }
    record.commandDisplay()?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return stringResource(behaviorOperationLabelRes(record.operation))
}

/** 偏好详情顶栏标题(配置键 / 文件 basename / 操作名,恒非空)。 */
@Composable
internal fun prefsHeadline(record: PrefsRecord): String {
    record.key?.takeIf { it.isNotBlank() }?.let { return it }
    record.path?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }?.let { return it }
    return stringResource(prefsOperationLabelRes(record.operation))
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
 * 普通 HashMap 承载(非快照态,不参与失效循环);点击时读取最近一次装配结果。
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

// region V28 共享装配片段(identity / toc / 时间戳行)

/** 身份区上下文行的分隔符(locale 无关中点,非 CJK,i18n 契约安全)。 */
private const val ACCENT_SEPARATOR = " · "

/** 类别·操作(+可选方向/验签)上下文行拼装(标签已本地化)。 */
private fun accentLine(vararg labels: String?): String =
    labels.filterNotNull().filter { it.isNotBlank() }.joinToString(ACCENT_SEPARATOR)

/** 时间戳元数据行(detailTimeFmt 按 Locale 重建契约见 [detailTimeFmt])。 */
@Composable
private fun timestampEntry(timestamp: Long): Pair<String, String> {
    val context = LocalContext.current
    val timeFmt = remember(context) { detailTimeFmt() }
    return Pair(stringResource(R.string.meta_timestamp), timeFmt.format(java.util.Date(timestamp)))
}

// endregion

/**
 * 算法记录详情内容(V28 纯 markdown 装配):
 * identity → toc → 元数据表 → 数据流节(input→↓→output,H2 节根随首文档)→
 * 密钥材料节(key 文档带 H2 节根,余字段 H3)→ 参数(json 围栏)→ 调用栈;
 * 每个区块恒 1 item([markdownSectionItems])。
 */
@Composable
internal fun CryptoLoadedContent(
    record: CryptoRecord,
    innerPadding: PaddingValues,
    listState: LazyListState,
    actions: DetailCodeActions,
) {
    val res = LocalContext.current.resources

    val metaEntries =
        listOfNotNull(
            Pair(stringResource(R.string.meta_kind), stringResource(kindLabelRes(record.kind))),
            record.direction?.let { Pair(stringResource(R.string.meta_direction), directionLabel(it)) },
            record.provider?.let { Pair(stringResource(R.string.meta_provider), it) },
            record.processName?.let { Pair(stringResource(R.string.meta_process), it) },
            Pair(stringResource(R.string.meta_context), "#${record.contextId}"),
            Pair(stringResource(R.string.meta_thread), record.threadName),
            timestampEntry(record.timestamp),
            record.verifyResult?.let {
                Pair(
                    stringResource(R.string.meta_verify),
                    stringResource(if (it) R.string.verify_ok else R.string.verify_fail),
                )
            },
            if (record.keyExportable) Pair(stringResource(R.string.meta_key_exportable), stringResource(R.string.confirm)) else null,
        )
    val metaTitle = stringResource(R.string.section_metadata)
    val metaTable = metaSectionDoc(metaTitle, metaEntries)

    // 身份区:算法名主标题 + 类别·操作·方向·验签 上下文行(取代原生 Hero 卡)。
    val accent =
        accentLine(
            stringResource(kindLabelRes(record.kind)),
            operationLabel(record.operation),
            record.direction?.let { directionLabel(it) },
            record.verifyResult?.let { stringResource(if (it) R.string.verify_ok else R.string.verify_fail) },
        )
    val identityMarkdown = identityDoc(headline = record.displayAlgorithm(), accentLine = accent)

    // TOC 与簿记共用同一锚点清单(可见性与可滚动性同源)。
    val anchors = cryptoAnchors(record)
    val tocList = tocEntries(anchors)

    // V28 D3:params → json 围栏(继承护栏计数,trailer 本地化标记不变)。
    val paramsTitle = stringResource(R.string.section_params)
    val paramsDoc =
        record.params?.takeIf { it.isNotEmpty() }?.let { bundle ->
            val formatted = formatParamsJson(bundle)
            val trailers =
                buildList {
                    if (formatted.truncatedValues > 0) add(res.getString(R.string.params_truncated))
                    if (formatted.omittedEntries > 0) {
                        add(res.getQuantityString(R.plurals.params_omitted, formatted.omittedEntries, formatted.omittedEntries))
                    }
                }
            textSectionDoc(paramsTitle, formatted.json!!, lang = MarkdownLangs.JSON, trailerLines = trailers)
        }

    val flowRoles = cryptoFlowRoles(record.kind, record.operation, record.direction)
    val inputRole = stringResource(flowRoles.first ?: R.string.section_input)
    val outputRole = stringResource(flowRoles.second ?: R.string.section_output)
    val hasInput = record.input != null
    val hasOutput = record.output != null
    val hasFlowZone = hasInput && hasOutput
    val zoneFlowTitle = stringResource(R.string.section_zone_data_flow)

    // 区块文档预计算(组合期;@Composable 文档构建器不得进入 LazyListScope DSL 作用域):
    val keyTitle = stringResource(R.string.section_key)
    val keyAliasTitle = stringResource(R.string.section_key_alias)
    val ivTitle = stringResource(R.string.section_iv)
    val saltTitle = stringResource(R.string.section_salt)
    val aadTitle = stringResource(R.string.section_aad)
    val publicKeyTitle = stringResource(R.string.section_public_key)
    val stackTitle = stringResource(R.string.section_stack)
    val zoneKeysTitle = stringResource(R.string.section_zone_key_material)

    // 数据流节:H2 节根随 input 文档首发射,箭头段落内联于 input 尾部(V28 去图标 item)。
    val inputDoc =
        record.input?.let {
            val inner = byteSectionDoc(inputRole, it)
            SectionDoc(
                markdown =
                    mdSectionHeading(zoneFlowTitle) +
                        inner.markdown + "\n" +
                        FLOW_ARROW_LINE + "\n\n",
                handles = inner.handles,
            )
        }
    val outputDoc = record.output?.let { byteSectionDoc(outputRole, it) }
    val keyDoc =
        record.key?.let {
            val inner = byteSectionDoc(keyTitle, it)
            SectionDoc(markdown = mdSectionHeading(zoneKeysTitle) + inner.markdown, handles = inner.handles)
        }
    val keyAliasDoc = record.keyAlias?.let { textSectionDoc(keyAliasTitle, it, headingLevel = 3) }
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
        book.advance(identityItem(identityMarkdown))
        book.advance(tocSectionItems(tocList, onAnchorClick = { book.scrollTo(it, scrollScope) }))
        book.mark("meta")
        book.advance(metaTableItems("meta", metaTable))

        // 数据流节:input(H2 节根 + 尾部箭头段)→ output;仅双侧齐备构成数据流。
        inputDoc?.let { doc ->
            book.mark("flow")
            book.advance(markdownSectionItems("input", doc, actions))
        }
        outputDoc?.let { doc ->
            book.advance(markdownSectionItems("output", doc, actions))
        }

        // 密钥材料节:key 文档承载 H2 节根,余字段 H3 归组。
        val hasKeyMaterial =
            keyDoc != null || keyAliasDoc != null || ivDoc != null ||
                saltDoc != null || aadDoc != null || publicKeyDoc != null
        if (hasKeyMaterial) {
            keyDoc?.let { doc ->
                book.mark("keys")
                book.advance(markdownSectionItems("key", doc, actions))
            }
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
 * 数据库记录详情内容(V28 纯 markdown 装配):
 * identity → toc → 元数据表 → SQL(sql 围栏)→ 路径 → 绑定参数(json)→
 * 结果集(ndjson)→ 结果文本 → 参数(json)→ 调用栈。
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
        listOfNotNull(
            Pair(stringResource(R.string.meta_kind), stringResource(sqlKindLabelRes(record.kind))),
            Pair(stringResource(R.string.meta_operation), sqlOperationLabel(record.operation)),
            record.dbPath?.let { Pair(stringResource(R.string.meta_db_path), it) },
            Pair(stringResource(R.string.meta_context), "#${record.contextId}"),
            Pair(stringResource(R.string.meta_thread), record.threadName),
            timestampEntry(record.timestamp),
            record.rowCount?.let { Pair(stringResource(R.string.meta_rows), it.toString()) },
            record.affectedCount?.let { Pair(stringResource(R.string.meta_affected), it.toString()) },
            record.rowId?.let { Pair(stringResource(R.string.meta_row_id), it.toString()) },
        )
    val metaTitle = stringResource(R.string.section_metadata)
    val metaTable = metaSectionDoc(metaTitle, metaEntries)

    val accent =
        accentLine(
            stringResource(sqlKindLabelRes(record.kind)),
            sqlOperationLabel(record.operation),
        )
    val identityMarkdown = identityDoc(headline = record.databaseName, accentLine = accent)

    val anchors = sqlAnchors(record)
    val tocList = tocEntries(anchors)

    // V28 D3:bindArgs → json 数组、rows → NDJSON(逐行独立对象)。
    val bindArgsJson = record.bindArgs?.takeIf { it.isNotEmpty() }?.let(::formatBindArgsJson)
    val rowsJson = record.rows?.takeIf { it.isNotEmpty() }?.let(::formatRowsNdjson)

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
        bindArgsJson?.let { bound ->
            val trailers =
                if (bound.omittedRows > 0) {
                    listOf(res.getQuantityString(R.plurals.rows_more, bound.omittedRows, bound.omittedRows))
                } else {
                    emptyList()
                }
            textSectionDoc(bindArgsTitle, bound.json!!, lang = MarkdownLangs.JSON, trailerLines = trailers)
        }
    val rowsDoc =
        rowsJson?.let { bounded ->
            val trailers =
                if (bounded.omittedRows > 0) {
                    listOf(res.getQuantityString(R.plurals.rows_more, bounded.omittedRows, bounded.omittedRows))
                } else {
                    emptyList()
                }
            textSectionDoc(resultRowsTitle, bounded.lines.joinToString("\n"), lang = MarkdownLangs.JSON, trailerLines = trailers)
        }
    val resultDoc = record.resultText?.let { textSectionDoc(resultTitle, it) }
    val paramsDoc =
        record.params?.takeIf { it.isNotEmpty() }?.let { params ->
            val formatted = formatParamsJson(params)
            val trailers =
                buildList {
                    if (formatted.truncatedValues > 0) add(res.getString(R.string.params_truncated))
                    if (formatted.omittedEntries > 0) {
                        add(res.getQuantityString(R.plurals.params_omitted, formatted.omittedEntries, formatted.omittedEntries))
                    }
                }
            textSectionDoc(paramsTitle, formatted.json!!, lang = MarkdownLangs.JSON, trailerLines = trailers)
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
        book.advance(identityItem(identityMarkdown))
        book.advance(tocSectionItems(tocList, onAnchorClick = { book.scrollTo(it, scrollScope) }))
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
 * 行为记录详情内容(V28 纯 markdown 装配):
 * identity → toc → 元数据表 → 命令(bash 围栏)→ 内容预览(三态字节代码组)→
 * 参数(json)→ 调用栈。
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
        listOfNotNull(
            Pair(stringResource(R.string.meta_kind), stringResource(behaviorKindLabelRes(record.kind))),
            Pair(stringResource(R.string.meta_operation), behaviorOperationLabel(record.operation)),
            record.path?.let { Pair(stringResource(R.string.meta_path), it) },
            record.destPath?.let { Pair(stringResource(R.string.meta_dest_path), it) },
            record.fileSize?.let { Pair(stringResource(R.string.meta_file_size), StorageFormat.formatBytes(it)) },
            Pair(stringResource(R.string.meta_context), "#${record.contextId}"),
            Pair(stringResource(R.string.meta_thread), record.threadName),
            timestampEntry(record.timestamp),
        )
    val metaTitle = stringResource(R.string.section_metadata)
    val metaTable = metaSectionDoc(metaTitle, metaEntries)

    val accent =
        accentLine(
            stringResource(behaviorKindLabelRes(record.kind)),
            behaviorOperationLabel(record.operation),
        )
    val identityMarkdown = identityDoc(headline = behaviorHeadline(record), accentLine = accent)

    val anchors = behaviorAnchors(record)
    val tocList = tocEntries(anchors)

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
            val formatted = formatParamsJson(params)
            val trailers =
                buildList {
                    if (formatted.truncatedValues > 0) add(res.getString(R.string.params_truncated))
                    if (formatted.omittedEntries > 0) {
                        add(res.getQuantityString(R.plurals.params_omitted, formatted.omittedEntries, formatted.omittedEntries))
                    }
                }
            textSectionDoc(paramsTitle, formatted.json!!, lang = MarkdownLangs.JSON, trailerLines = trailers)
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
        book.advance(identityItem(identityMarkdown))
        book.advance(tocSectionItems(tocList, onAnchorClick = { book.scrollTo(it, scrollScope) }))
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

// region V28 偏好详情(PrefsLoadedContent)

/**
 * 偏好记录详情内容(V28 一等公民;纯 markdown 装配同三兄弟):
 * identity → toc → 元数据表 → 键值(json/text 围栏,fileContentCapture 闸门)→
 * 参数(json)→ 调用栈,锚点键与内容一一对应。
 */
@Composable
private fun PrefsLoadedContent(
    record: PrefsRecord,
    innerPadding: PaddingValues,
    listState: LazyListState,
    actions: DetailCodeActions,
) {
    val res = LocalContext.current.resources

    val metaEntries =
        listOfNotNull(
            Pair(stringResource(R.string.meta_kind), stringResource(prefsKindLabelRes(record.kind))),
            Pair(stringResource(R.string.meta_operation), prefsOperationLabel(record.operation)),
            record.path?.let { Pair(stringResource(R.string.meta_pref_file), it) },
            record.key?.let { Pair(stringResource(R.string.meta_pref_key), it) },
            record.value?.let { view ->
                Pair(
                    stringResource(R.string.meta_file_size),
                    StorageFormat.formatBytes(view.totalLength.toLong()),
                )
            },
            Pair(stringResource(R.string.meta_context), "#${record.contextId}"),
            Pair(stringResource(R.string.meta_thread), record.threadName),
            timestampEntry(record.timestamp),
        )
    val metaTitle = stringResource(R.string.section_metadata)
    val metaTable = metaSectionDoc(metaTitle, metaEntries)

    val accent =
        accentLine(
            stringResource(prefsKindLabelRes(record.kind)),
            prefsOperationLabel(record.operation),
        )
    val identityMarkdown = identityDoc(headline = prefsHeadline(record), accentLine = accent)

    val anchors = prefsAnchors(record)
    val tocList = tocEntries(anchors)

    // 区块文档预计算(组合期;@Composable 文档构建器不得进入 LazyListScope DSL 作用域):
    val valueTitle = stringResource(R.string.section_value)
    val paramsTitle = stringResource(R.string.section_params)
    val stackTitle = stringResource(R.string.section_stack)

    // 键值节:JSON 序列化形态(stringSet/getAll)走 json 围栏高亮,其余 text;
    // 截断标记经 BytesView.truncated 承载(捕获侧已钳制)。
    val valueDoc =
        record.value?.let { view ->
            val body = view.text
            if (body.isNullOrEmpty()) {
                null
            } else {
                val lang = if (record.params?.get("type") == "stringSet" || record.params?.get("type") == "map") {
                    MarkdownLangs.JSON
                } else {
                    MarkdownLangs.TEXT
                }
                val trailers =
                    if (view.truncated) listOf(res.getString(R.string.bytes_truncated)) else emptyList()
                textSectionDoc(valueTitle, body, lang = lang, trailerLines = trailers)
            }
        }
    val paramsDoc =
        record.params?.takeIf { it.isNotEmpty() }?.let { params ->
            val formatted = formatParamsJson(params)
            val trailers =
                buildList {
                    if (formatted.truncatedValues > 0) add(res.getString(R.string.params_truncated))
                    if (formatted.omittedEntries > 0) {
                        add(res.getQuantityString(R.plurals.params_omitted, formatted.omittedEntries, formatted.omittedEntries))
                    }
                }
            textSectionDoc(paramsTitle, formatted.json!!, lang = MarkdownLangs.JSON, trailerLines = trailers)
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
        book.advance(identityItem(identityMarkdown))
        book.advance(tocSectionItems(tocList, onAnchorClick = { book.scrollTo(it, scrollScope) }))
        book.mark("meta")
        book.advance(metaTableItems("meta", metaTable))

        valueDoc?.let { doc ->
            book.mark("value")
            book.advance(markdownSectionItems("value", doc, actions))
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
// endregion