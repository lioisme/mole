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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.data.mapping.CryptoDetail
import com.lioisme.modev.data.mapping.RecordDetailModel
import com.lioisme.modev.data.mapping.SqlDetail
import com.lioisme.modev.data.mapping.toJson
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.components.displayAlgorithm
import com.lioisme.modev.ui.components.sqlHeadline
import com.lioisme.modev.ui.logs.detail.CryptoHero
import com.lioisme.modev.ui.logs.detail.DetailAnchorRow
import com.lioisme.modev.ui.logs.detail.MetaGrid
import com.lioisme.modev.ui.logs.detail.SqlHero
import com.lioisme.modev.ui.logs.detail.cryptoAnchors
import com.lioisme.modev.ui.logs.detail.cryptoMetaEntries
import com.lioisme.modev.ui.logs.detail.sqlAnchors
import com.lioisme.modev.ui.logs.detail.sqlMetaEntries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记录详情页(V24 G3 重设计;引擎契约 V7-V21 原样保留于 [RecordDetailSections]):
 * - 信息架构:Hero 身份卡(类别徽章/操作/算法名/方向/验签/时间,首屏一屏判读)
 *   → 锚点芯片行(按内容动态生成,点击平滑滚动至目标区块,索引经装配期簿记
 *   与 DSL 发射严格同源)→ 两列元数据网格 → 数据流区 / 密钥材料区 /
 *   SQL 各段 → 参数 / 调用栈;
 * - 顶栏 = ModevTopBar(标题=算法名恒单行)+ 上一条/下一条(id 投影邻接导航)
 *   + 更多(唯一功能 = 导出 JSON,SAF 后台构建 + 写流,双护栏预检);
 * - 性能红线(LazyColumn 稳定键、4096 字符折叠保险丝、Tier A/B 分档虚拟化、
 *   复制 4M / 导出 32M 闸门)全部由引擎文件承担,本层零改动。
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
     * V15 护栏:超 COPY_MAX_CHARS 字符拒写(ClipboardManager.setText 全量同步
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
        // 详情列表状态:记录切换(上一条/下一条/重试)即回顶,避免滞留旧位置。
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val currentRecordId = state.record?.id
        LaunchedEffect(currentRecordId) { listState.scrollToItem(0) }
        when {
            !state.loaded -> LoadingState(innerPadding)
            state.record == null -> FailedState(innerPadding, onRetry = viewModel::retry)
            else -> LoadedContent(
                record = state.record!!,
                innerPadding = innerPadding,
                listState = listState,
                copyFull = ::copyFull,
            )
        }
    }
}

/** 详情内容分派(V17 双形态):算法走 CryptoLoadedContent,数据库走 SQL 渲染。 */
@Composable
private fun LoadedContent(
    record: RecordDetailModel,
    innerPadding: PaddingValues,
    listState: LazyListState,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    when (record) {
        is CryptoDetail -> CryptoLoadedContent(record.record, innerPadding, listState, copyFull)
        is SqlDetail -> SqlLoadedContent(record.record, innerPadding, listState, copyFull)
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

/**
 * 算法记录详情内容(V24 重排):
 * hero → 锚点行 → 元数据网格 → 数据流区(input→arrow→output)→ 密钥材料区
 * → 参数 → 调用栈;字节区块引擎([byteSectionItems])原样,返回值承载索引推进。
 */
@Composable
private fun CryptoLoadedContent(
    record: CryptoRecord,
    innerPadding: PaddingValues,
    listState: LazyListState,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
) {
    // 长区块展开态记忆(V14 R3)/ 表示记忆(V19 T5):rememberSaveable 进程重建保持。
    var expandedSections by rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }
    fun sectionExpanded(sectionKey: String): Boolean = expandedSections[sectionKey] == true
    fun setSectionExpanded(
        sectionKey: String,
        value: Boolean,
    ) {
        expandedSections = if (value) expandedSections + (sectionKey to true) else expandedSections - sectionKey
    }
    var byteRepresentations by rememberSaveable { mutableStateOf(mapOf<String, ByteRepresentation>()) }
    fun sectionRepresentation(
        sectionKey: String,
        view: BytesView,
    ): ByteRepresentation = byteRepresentations[sectionKey] ?: view.defaultRepresentation()
    fun setSectionRepresentation(
        sectionKey: String,
        representation: ByteRepresentation,
    ) {
        byteRepresentations = byteRepresentations + (sectionKey to representation)
    }

    val res = LocalContext.current.resources
    val metaEntries = cryptoMetaEntries(record)
    val anchors = cryptoAnchors(record)

    // V16 R2:params 护栏化文本(纯函数 + 本地化省略标记,组合上下文完成,DSL 内只消费)。
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

    val book = remember(record.id) { AnchorBookkeeping(listState) }
    val scrollScope = rememberCoroutineScope()

    LazyColumn(
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
        item(key = "meta") {
            Column(Modifier.padding(top = 10.dp)) { MetaGrid(metaEntries) }
        }
        book.advance(1)

        // V20 R4 数据关系分区:输入 → 流向箭头 → 输出(角色标签由 cryptoFlowRoles 解析);
        // 仅一侧有数据不构成数据流,分区头与流向箭头不渲染。
        val (inputRoleRes, outputRoleRes) = cryptoFlowRoles(record.kind, record.operation, record.direction)
        val hasInput = record.input != null
        val hasOutput = record.output != null
        val hasFlowZone = hasInput && hasOutput
        if (hasFlowZone) {
            book.mark("flow")
            item(key = "zone-dataflow") {
                ZoneHeader(icon = Icons.Filled.SwapVert, titleRes = R.string.section_zone_data_flow)
            }
            book.advance(1)
        }
        if (hasInput) {
            val input = record.input!!
            book.advance(
                byteSectionItems(
                    sectionKey = "input",
                    titleRes = inputRoleRes ?: R.string.section_input,
                    view = input,
                    expanded = sectionExpanded("input"),
                    onExpandedChange = { setSectionExpanded("input", it) },
                    copyFull = copyFull,
                    representation = sectionRepresentation("input", input),
                    onRepresentationChange = { setSectionRepresentation("input", it) },
                ),
            )
        }
        if (hasFlowZone) {
            item(key = "flow-arrow") { DetailFlowArrow() }
            book.advance(1)
        }
        if (hasOutput) {
            val output = record.output!!
            book.advance(
                byteSectionItems(
                    sectionKey = "output",
                    titleRes = outputRoleRes ?: R.string.section_output,
                    view = output,
                    expanded = sectionExpanded("output"),
                    onExpandedChange = { setSectionExpanded("output", it) },
                    copyFull = copyFull,
                    representation = sectionRepresentation("output", output),
                    onRepresentationChange = { setSectionRepresentation("output", it) },
                ),
            )
        }

        // 密钥材料分区:key / keyAlias / iv / salt / aad / publicKey 归组。
        val hasKeyMaterial =
            record.key != null || record.keyAlias != null || record.iv != null ||
                record.salt != null || record.aad != null || record.publicKey != null
        if (hasKeyMaterial) {
            book.mark("keys")
            item(key = "zone-keymaterial") {
                ZoneHeader(icon = Icons.Filled.Key, titleRes = R.string.section_zone_key_material)
            }
            book.advance(1)
            record.key?.let { key ->
                book.advance(
                    byteSectionItems(
                        sectionKey = "key",
                        titleRes = R.string.section_key,
                        view = key,
                        expanded = sectionExpanded("key"),
                        onExpandedChange = { setSectionExpanded("key", it) },
                        copyFull = copyFull,
                        representation = sectionRepresentation("key", key),
                        onRepresentationChange = { setSectionRepresentation("key", it) },
                    ),
                )
            }
            record.keyAlias?.let { alias ->
                book.advance(
                    textSectionItems(
                        sectionKey = "key-alias",
                        titleRes = R.string.section_key_alias,
                        value = alias,
                        expanded = sectionExpanded("key-alias"),
                        onExpandedChange = { setSectionExpanded("key-alias", it) },
                        copyFull = copyFull,
                    ),
                )
            }
            record.iv?.let { iv ->
                book.advance(
                    byteSectionItems(
                        sectionKey = "iv",
                        titleRes = R.string.section_iv,
                        view = iv,
                        expanded = sectionExpanded("iv"),
                        onExpandedChange = { setSectionExpanded("iv", it) },
                        copyFull = copyFull,
                        representation = sectionRepresentation("iv", iv),
                        onRepresentationChange = { setSectionRepresentation("iv", it) },
                    ),
                )
            }
            record.salt?.let { salt ->
                book.advance(
                    byteSectionItems(
                        sectionKey = "salt",
                        titleRes = R.string.section_salt,
                        view = salt,
                        expanded = sectionExpanded("salt"),
                        onExpandedChange = { setSectionExpanded("salt", it) },
                        copyFull = copyFull,
                        representation = sectionRepresentation("salt", salt),
                        onRepresentationChange = { setSectionRepresentation("salt", it) },
                    ),
                )
            }
            record.aad?.let { aad ->
                book.advance(
                    byteSectionItems(
                        sectionKey = "aad",
                        titleRes = R.string.section_aad,
                        view = aad,
                        expanded = sectionExpanded("aad"),
                        onExpandedChange = { setSectionExpanded("aad", it) },
                        copyFull = copyFull,
                        representation = sectionRepresentation("aad", aad),
                        onRepresentationChange = { setSectionRepresentation("aad", it) },
                    ),
                )
            }
            record.publicKey?.let { pk ->
                book.advance(
                    byteSectionItems(
                        sectionKey = "publicKey",
                        titleRes = R.string.section_public_key,
                        view = pk,
                        expanded = sectionExpanded("publicKey"),
                        onExpandedChange = { setSectionExpanded("publicKey", it) },
                        copyFull = copyFull,
                        representation = sectionRepresentation("publicKey", pk),
                        onRepresentationChange = { setSectionRepresentation("publicKey", it) },
                    ),
                )
            }
        }
        if (paramsValue != null) {
            book.mark("params")
            book.advance(
                textSectionItems(
                    sectionKey = "params",
                    titleRes = R.string.section_params,
                    value = paramsValue,
                    expanded = sectionExpanded("params"),
                    onExpandedChange = { setSectionExpanded("params", it) },
                    copyFull = copyFull,
                ),
            )
        }
        if (record.stack.isNotEmpty()) {
            book.mark("stack")
            item(key = "stack-header") {
                StackSectionHeader()
            }
            book.advance(1)
            // V16 R2 护栏:最多渲染 STACK_MAX_FRAMES 帧(近因优先),超出追加本地化标记。
            val frames = record.stack.take(STACK_MAX_FRAMES)
            items(frames.size, key = { index -> "stack-frame-$index" }) { index ->
                StackFrame(frames[index])
            }
            book.advance(frames.size)
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
                book.advance(1)
            }
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * 数据库记录详情内容(V17 T11 引擎复用;V24 同款 hero/锚点/网格装配):
 * SQL 语句 / 路径 / 绑定参数 / 结果集 / 参数 / 调用栈,锚点键与内容一一对应。
 */
@Composable
private fun SqlLoadedContent(
    record: SqlRecord,
    innerPadding: PaddingValues,
    listState: LazyListState,
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
    val metaEntries = sqlMetaEntries(record)
    val anchors = sqlAnchors(record)

    val book = remember(record.id) { AnchorBookkeeping(listState) }
    val scrollScope = rememberCoroutineScope()

    LazyColumn(
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
        item(key = "meta") {
            Column(Modifier.padding(top = 10.dp)) { MetaGrid(metaEntries) }
        }
        book.advance(1)

        record.sql?.let { sql ->
            book.mark("sql")
            book.advance(
                textSectionItems(
                    sectionKey = "sql",
                    titleRes = R.string.section_sql,
                    value = sql,
                    expanded = sectionExpanded("sql"),
                    onExpandedChange = { setSectionExpanded("sql", it) },
                    copyFull = copyFull,
                ),
            )
        }
        record.dbPath?.let { path ->
            book.mark("dbpath")
            book.advance(
                textSectionItems(
                    sectionKey = "dbpath",
                    titleRes = R.string.section_db_path,
                    value = path,
                    expanded = sectionExpanded("dbpath"),
                    onExpandedChange = { setSectionExpanded("dbpath", it) },
                    copyFull = copyFull,
                ),
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
            book.mark("bindargs")
            book.advance(
                textSectionItems(
                    sectionKey = "bindargs",
                    titleRes = R.string.section_bind_args,
                    value = body,
                    expanded = sectionExpanded("bindargs"),
                    onExpandedChange = { setSectionExpanded("bindargs", it) },
                    copyFull = copyFull,
                ),
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
            book.mark("rows")
            book.advance(
                textSectionItems(
                    sectionKey = "rows",
                    titleRes = R.string.section_result_rows,
                    value = body,
                    expanded = sectionExpanded("rows"),
                    onExpandedChange = { setSectionExpanded("rows", it) },
                    copyFull = copyFull,
                ),
            )
        }
        record.resultText?.let { text ->
            book.mark("result")
            book.advance(
                textSectionItems(
                    sectionKey = "result",
                    titleRes = R.string.section_result_text,
                    value = text,
                    expanded = sectionExpanded("result"),
                    onExpandedChange = { setSectionExpanded("result", it) },
                    copyFull = copyFull,
                ),
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
            book.mark("params")
            book.advance(
                textSectionItems(
                    sectionKey = "params",
                    titleRes = R.string.section_params,
                    value = body,
                    expanded = sectionExpanded("params"),
                    onExpandedChange = { setSectionExpanded("params", it) },
                    copyFull = copyFull,
                ),
            )
        }
        if (record.stack.isNotEmpty()) {
            book.mark("stack")
            item(key = "sql-stack-header") { StackSectionHeader() }
            book.advance(1)
            val frames = record.stack.take(STACK_MAX_FRAMES)
            items(frames.size, key = { index -> "sql-stack-frame-$index" }) { index ->
                StackFrame(frames[index])
            }
            book.advance(frames.size)
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
                book.advance(1)
            }
        }
        item(key = "bottom-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

/** 分区小节标题(调用栈等非卡片化区块共用):primary titleSmall SemiBold。 */
@Composable
private fun StackSectionHeader() {
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.section_stack),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 堆栈帧(等宽小字,原生可选中)。 */
@Composable
private fun StackFrame(frame: String) {
    SelectionContainer {
        Text(
            frame,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 详情分区头(V20 R4):通栏 tonal 条(图标 + titleSmall SemiBold),
 * 与区块标题(primary 文字)形成两级标题层级。
 * 独立 LazyColumn item(不包裹整区,保持虚拟化)。
 */
@Composable
private fun ZoneHeader(icon: ImageVector, titleRes: Int) {
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("detail-zone"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
