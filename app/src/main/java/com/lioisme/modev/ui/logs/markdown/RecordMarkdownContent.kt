package com.lioisme.modev.ui.logs.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.TipIconButton
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxTheme

/**
 * V28 详情页 markdown 渲染层(纯 markdown 化重构):
 *
 * - 全部内容区块经 [RecordMarkdownContent] 单管线渲染;自定义仅限两类公开扩展点:
 *   `markdownTypography()`(M3 字阶显式映射,标题收敛为 titleMedium/titleSmall 级,
 *   取代库默认 display* 巨型标题)+ `markdownComponents(codeFence=)`(代码块);
 * - 代码块扁平化(V28 去卡):surfaceContainerLowest 圆角容器 + 细描边,零 tonal
 *   elevation,顶部微型行(语言标签 + 复制/全屏出口);
 * - 语法高亮(V28 D2/D3):分词完全由 SnipMeDev Highlights 引擎承载(json→JS 分词、
 *   bash/shell→SHELL;零手搓 tokenizer),主题色经 [modevSyntaxTheme] 与 Material
 *   colorScheme 同源映射(亮暗自适应);超过 [HIGHLIGHT_MAX_CHARS] 优雅降级纯 mono;
 * - 代码组 = 连续 text/base64/hex 围栏([CodeGroupExtractor] 聚合),标签页承接
 *   多表示切换语义(选中态 rememberSaveable);单围栏退化单代码块;
 * - immediate = true:文档经显示上限钳制有界,同步解析消除加载闪烁,
 *   且规避异步解析与测试帧推进的竞态(AGENTS.md V26 沉淀坑)。
 */

/** 高亮降级阈值:≤32Ki 同步分词(毫秒级);超限纯 mono 直渲(巨量围栏不值得)。 */
internal const val HIGHLIGHT_MAX_CHARS = 32 * 1024

/** 围栏语言 → Highlights 分词语言映射(null = 纯 mono;json 经 JS 分词器覆盖结构着色)。 */
internal fun highlightSyntaxLanguage(language: String?): SyntaxLanguage? =
    when (language) {
        MarkdownLangs.JSON -> SyntaxLanguage.JAVASCRIPT
        "bash", "sh", "shell" -> SyntaxLanguage.SHELL
        else -> null
    }

/**
 * M3 同源语法主题:Highlights 语法角色 ↔ Material colorScheme 角色一一映射。
 * 视觉契约:高亮配色与 App 主题同源(动态取色/暗色模式自动跟随),
 * 不引入第三方固定配色板。
 */
private fun modevSyntaxTheme(scheme: androidx.compose.material3.ColorScheme): SyntaxTheme =
    SyntaxTheme(
        key = "modev-m3",
        code = scheme.onSurfaceVariant.toArgb(),
        keyword = scheme.primary.toArgb(),
        string = scheme.tertiary.toArgb(),
        literal = scheme.secondary.toArgb(),
        comment = scheme.outline.toArgb(),
        metadata = scheme.tertiary.toArgb(),
        multilineComment = scheme.outline.toArgb(),
        punctuation = scheme.outline.toArgb(),
        mark = scheme.onSurface.toArgb(),
    )

/**
 * Highlights 引擎分词 → AnnotatedString(同步;SpanStyle 应用逻辑与库官方
 * `-code` 模块 glue 同构,分词完全由引擎承载)。
 */
internal fun highlightAnnotatedString(
    code: String,
    language: SyntaxLanguage,
    theme: SyntaxTheme,
): AnnotatedString {
    val highlights =
        Highlights.Builder()
            .code(code)
            .language(language)
            .theme(theme)
            .build()
            .getHighlights()
    return buildAnnotatedString {
        append(code)
        highlights.forEach { highlight ->
            when (highlight) {
                is ColorHighlight -> addStyle(
                    SpanStyle(color = Color(highlight.rgb)),
                    highlight.location.start,
                    highlight.location.end,
                )
                is BoldHighlight -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    highlight.location.start,
                    highlight.location.end,
                )
            }
        }
    }
}

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
            typography = detailMarkdownTypography(),
            modifier = modifier,
            components =
                markdownComponents(
                    codeFence = ::ModevMarkdownCodeFence,
                ),
        )
    }
}

/**
 * 详情排版规范(V28 显式字阶映射):
 * 节根 H2 = titleMedium SemiBold / 子节 H3 = titleSmall SemiBold /
 * 正文 bodyMedium(lineHeight 22sp)/ 表格与引用 bodySmall /
 * 代码 13sp Mono(lineHeight 20sp)/ 链接 primary Medium 无下划线。
 */
@Composable
internal fun detailMarkdownTypography(): MarkdownTypography {
    val t = MaterialTheme.typography
    val body = t.bodyMedium.copy(lineHeight = 22.sp)
    val subHeading = t.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    val monoCode = t.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return markdownTypography(
        h1 = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        h2 = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        h3 = subHeading,
        h4 = subHeading,
        h5 = subHeading,
        h6 = subHeading,
        text = body,
        code = monoCode,
        inlineCode = monoCode,
        quote = t.bodySmall.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        ),
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        textLink = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            ),
        ),
        table = t.bodySmall.copy(lineHeight = 18.sp),
    )
}

/** 库扩展点桥接:CODE_FENCE 节点 → 代码组 / 单代码块。 */
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
        MdCodeBlock(
            languageLabel = (info.language ?: "").uppercase(),
            language = info.language,
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
    // 选中态按位置域保存(LazyColumn item key 承担跨滚动恢复;V28 弃用 API 清零:去显式 key)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val members = (placement.runFirstOrdinal until placement.runFirstOrdinal + placement.runSize)
        .mapNotNull { ordinal -> index[ordinal]?.let { ordinal to it } }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        SecondaryTabRow(
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
        MdCodeBlock(
            languageLabel = (activeInfo.language ?: "").uppercase(),
            language = activeInfo.language,
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
 * 扁平代码块(V28 去卡化):圆角浅底容器 + 细描边(非卡片,无 elevation),
 * header = 语言标签(mono micro)+ 复制/全屏 TipIconButton;正文双向限高滚动,
 * json/bash 围栏经 Highlights 引擎着色(M3 同源主题),其余纯 mono。
 */
@Composable
internal fun MdCodeBlock(
    languageLabel: String,
    language: String?,
    displayContent: String,
    handle: DetailFenceHandle?,
    actions: DetailCodeActions?,
    testTagKey: String,
) {
    val scheme = MaterialTheme.colorScheme
    val syntaxLanguage = highlightSyntaxLanguage(language)
    val highlighted =
        if (syntaxLanguage != null && displayContent.isNotEmpty() && displayContent.length <= HIGHLIGHT_MAX_CHARS) {
            val theme = remember(scheme) { modevSyntaxTheme(scheme) }
            remember(displayContent, syntaxLanguage, theme) {
                runCatching { highlightAnnotatedString(displayContent, syntaxLanguage, theme) }.getOrNull()
            }
        } else {
            null
        }
    val monoBody = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(color = scheme.surfaceContainerLowest, shape = CODE_BLOCK_SHAPE)
            .border(width = 1.dp, color = scheme.outlineVariant.copy(alpha = 0.4f), shape = CODE_BLOCK_SHAPE)
            .testTag("md-code-block"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp)
                .testTag(testTagKey),
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
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.section_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
            TipIconButton(
                tooltip = stringResource(R.string.detail_fullscreen),
                enabled = handle != null && actions != null && handle.fullText.isNotEmpty(),
                onClick = { handle?.let { actions?.onFullscreen(it) } },
            ) {
                Icon(
                    Icons.Filled.Fullscreen,
                    contentDescription = stringResource(R.string.detail_fullscreen),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        SelectionContainer {
            if (highlighted != null) {
                Text(
                    text = highlighted,
                    style = monoBody,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            } else {
                Text(
                    text = displayContent,
                    style = monoBody,
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

private val CODE_BLOCK_SHAPE = RoundedCornerShape(8.dp)
