package com.lioisme.modev.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.ChunkedText
import com.lioisme.modev.ui.components.TipIconButton
import com.webtoonscorp.android.readmore.material3.ReadMoreText

/**
 * 详情区块渲染引擎(V7-V21 沉淀,V24 自 RecordDetailScreen 机械拆分,逻辑零改动):
 * - 三态表示(文本/Base64/Hex)单一路径;折叠判定与分档以所选表示字符数为基准;
 * - >4096 字符自动收起(ReadMoreText 有界预览,零大布局);
 * - 展开态按 [sectionRenderMode] 双档:Tier A(≤128K)整段单 SelectionContainer /
 *   Tier B(>128K)[ChunkedText] 分块惰性虚拟化(O(1) 取块,零物化);
 * - 复制护栏 [COPY_MAX_CHARS] / 导出护栏 [EXPORT_MAX_CHARS];
 * - 条目组合函数返回其向宿主 LazyColumn 贡献的 item 数(V24 锚点索引簿记,
 *   与 DSL 发射严格同源,杜绝索引漂移)。
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

/**
 * 字节区块头部(V14:恒展示,不随折叠态变化;V16 R3 复制菜单化;V19 T5 三态分段):
 * 标题 + 表示分段(SingleChoiceSegmentedButtonRow,按可用表示收敛:无文本隐藏
 * 文本段、旧记录无 Base64 隐藏 Base64 段)+ footer(字节数(含捕获侧截断标记)
 * + 复制菜单按钮)。
 * 复制菜单 = 文本 / Base64 / Hex 三项(V19 T5:移除「文本+Hex」合并项;文本/Base64
 * 缺失或超 [COPY_MAX_CHARS] 的项隐藏,不留空白项,不产生死按钮;Hex 恒在)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ByteSectionHeader(
    title: String,
    view: BytesView,
    representation: ByteRepresentation,
    available: List<ByteRepresentation>,
    onRepresentationChange: (ByteRepresentation) -> Unit,
    onCopyText: (() -> Unit)?,
    onCopyBase64: (() -> Unit)?,
    onCopyHex: () -> Unit,
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
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        available.forEachIndexed { index, repr ->
            SegmentedButton(
                selected = representation == repr,
                onClick = { onRepresentationChange(repr) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = available.size),
            ) {
                Text(
                    stringResource(
                        when (repr) {
                            ByteRepresentation.TEXT -> R.string.section_repr_text
                            ByteRepresentation.BASE64 -> R.string.section_repr_base64
                            ByteRepresentation.HEX -> R.string.section_repr_hex
                        }
                    ),
                    maxLines = 1,
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.bytes_count, view.totalLength) +
                if (view.truncated) stringResource(R.string.bytes_truncated) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        // Hex 恒可复制,复制入口恒展示(菜单内按表示缺失/超护栏隐藏条目)。
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
            onCopyBase64?.let { copy ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.section_copy_base64)) },
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
        }
    }
}

/**
 * 在宿主 LazyColumn 中组合字节区块全部条目(V14 R3/V15 双层渲染;V19 T5 三态):
 * - 内容 = 所选表示(文本/Base64/Hex)单一路径,折叠判定与分档以
 *   [BytesView.representationChars] 为基准(短 text 恒全量,不回退折叠预览);
 * - 短内容(≤ 4096 字符):头部单条 + 整段单 Text(Tier A 无折叠控件,恒全量);
 * - 长内容(> 4096 字符):默认收起——头部 + 单条预览(前 4096 字符有界文本,
 *   ReadMoreText 组件库渲染「… 展开更多」内联链接);展开后按 [sectionRenderMode]:
 *   Tier A(≤ 128K 字符)= 整段单 SelectionContainer 原生选择,系统「全选」一次覆盖
 *   整段;Tier B(> 128K 字符)= 所选表示分块(惰性,仅可视块组合,[ChunkedText]
 *   O(1) 取块,零物化)+ 尾部「收起」按钮。
 * 复制:footer 复制菜单(V16 R3;V19 T5 三项 = 文本/Base64/Hex,超 4M 或表示缺失项
 * 隐藏);Tier A 整段原生选择;Tier B 分块原生选择(V14 R2 替代 V13 长按整段复制)。
 *
 * @return 本次向宿主 LazyColumn 贡献的 item 数(2/2/3 或 chunks+2;锚点索引簿记)。
 */
internal fun LazyListScope.byteSectionItems(
    sectionKey: String,
    titleRes: Int,
    view: BytesView,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
    representation: ByteRepresentation,
    onRepresentationChange: (ByteRepresentation) -> Unit,
): Int {
    item(key = "section-$sectionKey-header") {
        ByteSectionHeader(
            title = stringResource(titleRes),
            view = view,
            representation = representation,
            available = view.availableRepresentations(),
            onRepresentationChange = onRepresentationChange,
            // V16 R3:菜单项按可用性收敛——表示缺失 / 超复制护栏的项隐藏
            onCopyText =
                view.text
                    ?.takeIf { it.length <= COPY_MAX_CHARS }
                    ?.let { text -> { copyFull(titleRes, text) } },
            onCopyBase64 =
                view.base64
                    ?.takeIf { it.length <= COPY_MAX_CHARS }
                    ?.let { b64 -> { copyFull(titleRes, b64) } },
            onCopyHex = { copyFull(titleRes, view.hex) },
        )
    }
    val reprChars = view.representationChars(representation)
    val exceeds = reprChars > ChunkedText.DEFAULT_CHUNK_CHARS
    if (!exceeds) {
        // 短内容:整段单 Text 恒全量展示(Tier A),无折叠控件。
        item(key = "section-$sectionKey-content") { SelectableSectionContent(view, representation) }
        return 2
    }
    if (!expanded) {
        item(key = "section-$sectionKey-preview") {
            SectionPreview(view = view, representation = representation, onExpand = { onExpandedChange(true) })
        }
        return 2
    }
    var emitted = 1 // header 已计
    if (sectionRenderMode(reprChars) == SectionRenderMode.SINGLE_SELECTABLE) {
        // Tier A:整段单 SelectionContainer,一次手势选中全部。
        item(key = "section-$sectionKey-content") { SelectableSectionContent(view, representation) }
        emitted += 1
    } else {
        // Tier B:分块虚拟化(跨 item 连续选择为平台限制,复制按钮承载全选语义)。
        emitted += byteSectionChunks(sectionKey, view, representation)
    }
    item(key = "section-$sectionKey-collapse") {
        TextButton(onClick = { onExpandedChange(false) }) {
            Text(stringResource(R.string.section_collapse))
        }
    }
    return emitted + 1
}

/**
 * 整段可选中内容(Tier A,V15;V19 T5 单表示):单一 SelectionContainer 包裹所选
 * 表示单 Text,系统长按手柄 / 「全选」一次覆盖整段(满足「同一段文本可选中全部」);
 * 字符数由 [sectionRenderMode] 保证有界(≤ SELECTABLE_CAP_CHARS),
 * 单 Text 布局一次性成本可控,不引入分块碎片化选择。
 */
@Composable
private fun SelectableSectionContent(view: BytesView, representation: ByteRepresentation) {
    val content = view.representationText(representation) ?: return
    val mono = representation != ByteRepresentation.TEXT
    SelectionContainer {
        Text(
            text = content,
            style =
                if (mono) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

/**
 * 字节区块 Tier B 展开态分块(V19 T5 单表示):所选表示按 ChunkedText 分块,
 * 每块包 SelectionContainer 提供原生选择(跨 LazyColumn item 连续选择为
 * 平台限制,Tier A 整段选择承载全量框选);块 key 含表示名,切换表示即换键重组。
 *
 * @return 贡献的 item 数(= 块数)。
 */
private fun LazyListScope.byteSectionChunks(
    sectionKey: String,
    view: BytesView,
    representation: ByteRepresentation,
): Int {
    val content = view.representationText(representation) ?: return 0
    val chunks = ChunkedText(content)
    val mono = representation != ByteRepresentation.TEXT
    items(chunks.size, key = { i -> "section-$sectionKey-${representation.name.lowercase()}-chunk-$i" }) { i ->
        SelectionContainer {
            Text(
                text = chunks[i],
                style =
                    if (mono) {
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                modifier = Modifier.animateItem(),
            )
        }
    }
    return chunks.size
}

/**
 * 长区块收起态预览(V14 R3;V19 T5 单表示):仅喂所选表示前
 * ChunkedText.DEFAULT_CHUNK_CHARS 字符有界文本,ReadMoreText(collapsedMaxLines=4)
 * 渲染「… [record_see_more]」内联链接,点按即展开——预览零大布局、零性能回退。
 * 预览不包 SelectionContainer(原生选择仅在展开态分块上,避免手势冲突)。
 */
@Composable
private fun SectionPreview(
    view: BytesView,
    representation: ByteRepresentation,
    onExpand: () -> Unit,
) {
    val limit = ChunkedText.DEFAULT_CHUNK_CHARS
    val content = view.representationText(representation) ?: return
    val mono = representation != ByteRepresentation.TEXT
    val preview = content.take(limit)
    val style =
        if (mono) {
            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            MaterialTheme.typography.bodyMedium
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
 *   展开按 sectionRenderMode 分档:Tier A 整段单 SelectionContainer 原生选择 /
 *   Tier B ChunkedText 分块虚拟化;尾部「收起」按钮;
 * - 复制:标题行复制按钮 = 完整文本(经 copyFull 4M 护栏)。
 *
 * @return 本次贡献的 item 数(锚点索引簿记)。
 */
internal fun LazyListScope.textSectionItems(
    sectionKey: String,
    titleRes: Int,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    copyFull: (titleRes: Int, fullText: String) -> Unit,
): Int {
    item(key = "section-$sectionKey-header") {
        TextSectionHeader(
            title = stringResource(titleRes),
            onCopy = { copyFull(titleRes, value) },
        )
    }
    val exceeds = value.length > ChunkedText.DEFAULT_CHUNK_CHARS
    if (!exceeds) {
        item(key = "section-$sectionKey-content") { TextSectionContent(value) }
        return 2
    }
    if (!expanded) {
        item(key = "section-$sectionKey-preview") {
            TextSectionPreview(value = value, onExpand = { onExpandedChange(true) })
        }
        return 2
    }
    var emitted = 1
    if (sectionRenderMode(value.length) == SectionRenderMode.SINGLE_SELECTABLE) {
        item(key = "section-$sectionKey-content") { TextSectionContent(value) }
        emitted += 1
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
        emitted += chunks.size
    }
    item(key = "section-$sectionKey-collapse") {
        TextButton(onClick = { onExpandedChange(false) }) {
            Text(stringResource(R.string.section_collapse))
        }
    }
    return emitted + 1
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

/** 数据流流向箭头(V20 R4):输入块与输出块之间,语义 contentDescription = cd_flow。 */
@Composable
internal fun DetailFlowArrow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = stringResource(R.string.cd_flow),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
