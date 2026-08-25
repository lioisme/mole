package com.lioisme.modev.ui.logs.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.TipIconButton
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * V26 详情页 markdown 渲染层(库:mikepenz multiplatform-markdown-renderer-m3):
 *
 * - 全部内容区块经 [RecordMarkdownContent] 单管线渲染;自定义仅限库公开扩展点
 *   (`markdownComponents(codeFence=)`),解析/排版/表格/引用块一律走库默认组件;
 * - 代码组 = 连续 text/base64/hex 围栏([CodeGroupExtractor] 聚合),标签页承接
 *   原三态分段语义(文案复用 section_repr_* 资源,选中态 rememberSaveable);
 * - 单围栏退化为单个 [CodeBlockCard];卡片限高滚动(DeepSeek/豆包式代码卡),
 *   复制/全屏按钮经 [DetailFenceHandle] 操作全量文本(护栏在屏幕层统一执行);
 * - immediate = true:文档经显示上限钳制有界,同步解析消除加载闪烁,
 *   且规避异步解析与测试帧推进的竞态(已知工具链坑,沉淀 AGENTS.md V26)。
 */

/** 围栏全量文本句柄(ordinal 寻址,由装配层按生成顺序对齐提供)。 */
class DetailFenceHandle(
    val label: String,
    val language: String,
    val fullText: String,
)

/** 屏幕级动作出口:复制(4M 护栏 + Snackbar)/ 全屏查看。 */
class DetailCodeActions(
    val onCopyFull: (handle: DetailFenceHandle) -> Unit,
    val onFullscreen: (handle: DetailFenceHandle) -> Unit,
)

private val LocalDetailFences = compositionLocalOf<List<DetailFenceHandle>> { emptyList() }
private val LocalDetailCodeActions = compositionLocalOf<DetailCodeActions?> { null }

/**
 * 区块 markdown 渲染入口:单一 [com.mikepenz.markdown.m3.Markdown] 管线。
 *
 * @param handles 与文档内围栏生成序严格对齐的全量文本句柄(ordinal 寻址)
 */
@Composable
fun RecordMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    handles: List<DetailFenceHandle> = emptyList(),
    actions: DetailCodeActions? = null,
) {
    CompositionLocalProvider(
        LocalDetailFences provides handles,
        LocalDetailCodeActions provides actions,
    ) {
        val state = rememberMarkdownState(content = markdown, immediate = true)
        Markdown(
            markdownState = state,
            modifier = modifier,
            components =
                markdownComponents(
                    codeFence = ::ModevMarkdownCodeFence,
                ),
        )
    }
}

/** 库扩展点桥接:CODE_FENCE 节点 → 代码组 / 单代码卡。 */
@Composable
private fun ModevMarkdownCodeFence(model: MarkdownComponentModel) {
    // 文档级索引按 content 记忆(同一文档重组零重扫);节点自身经偏移定位。
    // 围栏恒为顶层块(生成器契约),parent 即文档根;兜底自指保证扫描非空。
    val index = remember(model.content) { buildFenceIndex(model.node.parent ?: model.node, model.content) }
    val entry = index.locate(model.node) ?: return
    val info = entry.info ?: return
    val fences = LocalDetailFences.current
    val actions = LocalDetailCodeActions.current
    val handle = fences.getOrNull(entry.ordinal)

    val placement = entry.placement
    if (placement == null || placement.runSize <= 1) {
        CodeBlockCard(
            languageLabel = (info.language ?: "").uppercase(),
            displayContent = info.code,
            handle = handle,
            actions = actions,
            testTagKey = "md-${entry.ordinal}",
        )
        return
    }
    if (placement.indexInRun > 0) {
        // 组内非首成员:已由首成员整组渲染,消费掉不重复发射。
        return
    }
    val firstOrdinal = placement.runFirstOrdinal
    var selectedTab by rememberSaveable(key = "md-tab-$firstOrdinal") { mutableIntStateOf(0) }
    val members = (placement.runFirstOrdinal until placement.runFirstOrdinal + placement.runSize)
        .mapNotNull { ordinal -> index[ordinal]?.let { ordinal to it } }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        TabRow(
            selectedTabIndex = selectedTab.coerceIn(0, members.size - 1),
            modifier = Modifier.fillMaxWidth().testTag("detail-code-tabs"),
        ) {
            members.forEachIndexed { tabIndex, (_, fenceEntry) ->
                val lang = fenceEntry.info?.language
                Tab(
                    selected = selectedTab == tabIndex,
                    onClick = { selectedTab = tabIndex },
                    text = {
                        Text(
                            stringResource(langLabelRes(lang)),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        val activeIndex = selectedTab.coerceIn(0, members.size - 1)
        val (activeOrdinal, activeEntry) = members[activeIndex]
        val activeInfo = activeEntry.info ?: return
        CodeBlockCard(
            languageLabel = (activeInfo.language ?: "").uppercase(),
            displayContent = activeInfo.code,
            handle = fences.getOrNull(activeOrdinal),
            actions = actions,
            testTagKey = "md-$activeOrdinal",
        )
    }
}

private fun langLabelRes(language: String?): Int =
    when (language) {
        MarkdownLangs.TEXT -> R.string.section_repr_text
        MarkdownLangs.BASE64 -> R.string.section_repr_base64
        MarkdownLangs.HEX -> R.string.section_repr_hex
        else -> R.string.section_repr_hex
    }

/**
 * 代码卡(DeepSeek 式限高代码区):header(语言标签 + 复制 + 全屏)+
 * 双向滚动的可选中等宽正文;高度上限 320dp,完整内容经句柄出口承载。
 */
@Composable
internal fun CodeBlockCard(
    languageLabel: String,
    displayContent: String,
    handle: DetailFenceHandle?,
    actions: DetailCodeActions?,
    testTagKey: String,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail-code-card"),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = languageLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).testTag("detail-code-lang"),
                )
                TipIconButton(
                    tooltip = stringResource(R.string.section_copy),
                    enabled = handle != null && actions != null,
                    onClick = { handle?.let { actions?.onCopyFull(it) } },
                ) {
                    IconCopy()
                }
                Spacer(Modifier.width(4.dp))
                TipIconButton(
                    tooltip = stringResource(R.string.detail_fullscreen),
                    enabled = handle != null && actions != null && handle.fullText.isNotEmpty(),
                    onClick = { handle?.let { actions?.onFullscreen(it) } },
                ) {
                    IconFullscreen()
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SelectionContainer {
                Text(
                    text = displayContent,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun IconCopy() {
    Icon(
        Icons.Filled.ContentCopy,
        contentDescription = stringResource(R.string.section_copy),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IconFullscreen() {
    Icon(
        Icons.Filled.Fullscreen,
        contentDescription = stringResource(R.string.detail_fullscreen),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
