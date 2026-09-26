package com.lioisme.modev.ui.detail

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.ui.detail.detail.DetailAnchor
import com.lioisme.modev.ui.detail.markdown.DetailCodeActions
import com.lioisme.modev.ui.detail.markdown.DetailFenceHandle
import com.lioisme.modev.ui.detail.markdown.MarkdownLangs
import com.lioisme.modev.ui.detail.markdown.RecordMarkdownContent
import com.lioisme.modev.ui.detail.markdown.RecordTocContent
import com.lioisme.modev.ui.detail.markdown.TocEntry
import com.lioisme.modev.ui.detail.markdown.byteSectionMarkdown
import com.lioisme.modev.ui.detail.markdown.capDisplay
import com.lioisme.modev.ui.detail.markdown.identityMarkdown
import com.lioisme.modev.ui.detail.markdown.metaTableMarkdown
import com.lioisme.modev.ui.detail.markdown.mdSectionHeading
import com.lioisme.modev.ui.detail.markdown.textSectionMarkdown
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 详情区块渲染引擎(V28 纯 markdown 化,取代 V26 双层装配):
 *
 * - Appbar 以下全域:信息内容 100% markdown 承载(身份区 / 元数据 / 内容节);
 *   目录(TOC)为**导航控件**([RecordTocContent],D2.1 决策——渲染库链接语义
 *   节点 0×0 bounds 不可达,交互可靠性优先),标签与区块标题同源;
 *   原生 Hero 卡、FilterChip 锚点行、ZoneHeader 色带、流向图标行全部退役;
 * - 每个内容区块 = 1 LazyColumn item(虚拟化粒度不变);锚点簿记契约平移:
 *   mark/advance 与 DSL 发射严格同序,markdown 区块恒贡献 1 item;
 * - 字节区块多表示 = 连续 text/base64/hex 围栏(渲染层聚合为代码组标签页),
 *   完整内容经 [DetailFenceHandle] 出口承载(护栏见 [COPY_MAX_CHARS]/[EXPORT_MAX_CHARS]);
 * - TOC 导航行点击 → 屏幕簿记 animateScrollToItem(LazyColumn 显式
 *   state=listState 契约由 AnchorScrollRoboTest 回归钉守);
 */

/** 复制护栏(V15,不变):超此字符数拒写剪贴板(ClipboardManager 同步物化,防主线程卡死)。 */
internal const val COPY_MAX_CHARS = 4 * 1024 * 1024

/** 导出护栏(V15,不变):超此字符数拒绝落盘(SAF 写流虽可行,但 32M+ JSON 无实际用途)。 */
internal const val EXPORT_MAX_CHARS = 32 * 1024 * 1024

/** 区块文档束:markdown 文本 + 与围栏生成序对齐的全量句柄。 */
@Immutable
data class SectionDoc(val markdown: String, val handles: List<DetailFenceHandle>)

/** 详情时间戳格式(每次调用按当前 Locale 构造;语言切换重建后不残留旧 locale,V15 契约)。 */
internal fun detailTimeFmt(): SimpleDateFormat =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

/**
 * 字节区块文档(纯装配,@Composable 仅解析资源):
 * 标题 + 字节统计行(含捕获侧截断标记)+ 各可用表示围栏(text/base64/hex 序)+
 * 超限截断标记;句柄按同一顺序提供全量表示文本。
 */
@Composable
internal fun byteSectionDoc(
    title: String,
    view: BytesView,
    headingLevel: Int = 3,
): SectionDoc {
    val res = LocalContext.current.resources
    return remember(view, title, headingLevel) {
        val footer =
            buildString {
                append(res.getString(R.string.bytes_count, view.totalLength))
                if (view.truncated) append(res.getString(R.string.bytes_truncated))
            }
        val capMarker = { displayed: Int, total: Int ->
            res.getQuantityString(R.plurals.detail_truncated_marker, total, displayed, total)
        }
        val inputs =
            listOf(
                MarkdownLangs.TEXT to view.text,
                MarkdownLangs.BASE64 to view.base64,
                MarkdownLangs.HEX to view.hex,
            ).filter { it.second != null }
        val handles =
            inputs.map { (lang, value) ->
                DetailFenceHandle(label = title, language = lang, fullText = value!!)
            }
        SectionDoc(
            markdown =
                byteSectionMarkdown(
                    title,
                    view,
                    footerLine = footer,
                    capMarkerFormatter = capMarker,
                    headingLevel = headingLevel,
                ),
            handles = handles,
        )
    }
}

/**
 * 文本区块文档:单围栏(text/sql/json/bash)+ 尾注段落(省略计数等本地化标记)。
 * @param headingLevel 标题层级(V28:独立节根传 2;分区内子表示保持默认 3)
 */
@Composable
internal fun textSectionDoc(
    title: String,
    value: String,
    lang: String = MarkdownLangs.TEXT,
    trailerLines: List<String> = emptyList(),
    headingLevel: Int = 2,
): SectionDoc {
    val res = LocalContext.current.resources
    return remember(value, title, lang, trailerLines, headingLevel) {
        val capMarker = { displayed: Int, total: Int ->
            res.getQuantityString(R.plurals.detail_truncated_marker, total, displayed, total)
        }
        SectionDoc(
            markdown =
                textSectionMarkdown(
                    title = title,
                    value = value,
                    lang = lang,
                    capMarkerFormatter = capMarker,
                    trailerLines = trailerLines,
                    headingLevel = headingLevel,
                ),
            handles = listOf(DetailFenceHandle(label = title, language = lang, fullText = value)),
        )
    }
}

/** 元数据表文档(H2 节根标题 + GFM 两列表;V28 起带节标题,与全页层级统一)。 */
@Composable
internal fun metaSectionDoc(title: String, entries: List<Pair<String, String>>): String {
    val fieldHeader = stringResource(R.string.meta_table_field)
    val valueHeader = stringResource(R.string.meta_table_value)
    return remember(title, entries, fieldHeader, valueHeader) {
        mdSectionHeading(title) + metaTableMarkdown(listOf(fieldHeader, valueHeader), entries)
    }
}

/** 身份区文档:`## headline` + 粗体上下文行(类别 · 操作 · 方向/校验)。 */
@Composable
internal fun identityDoc(headline: String, accentLine: String?): String =
    remember(headline, accentLine) { identityMarkdown(headline, accentLine) }

/** 目录条目预解析(标签本地化;key 与簿记同源)。 */
@Composable
internal fun tocEntries(anchors: List<DetailAnchor>): List<TocEntry> =
    anchors.map { TocEntry(label = stringResource(it.labelRes), key = it.key) }

/**
 * 区块条目组合:单一 markdown item。
 *
 * @return 本次向宿主 LazyColumn 贡献的 item 数(恒 1,锚点索引簿记)。
 */
internal fun LazyListScope.markdownSectionItems(
    sectionKey: String,
    doc: SectionDoc,
    actions: DetailCodeActions?,
): Int {
    item(key = "section-$sectionKey", contentType = "md-section") {
        RecordMarkdownContent(markdown = doc.markdown, handles = doc.handles, actions = actions)
    }
    return 1
}

/** 元数据表条目(单 item,无句柄——表格不含围栏)。 */
internal fun LazyListScope.metaTableItems(sectionKey: String, sectionMarkdown: String): Int {
    item(key = "section-$sectionKey", contentType = "md-section") {
        RecordMarkdownContent(markdown = sectionMarkdown)
    }
    return 1
}

/** 身份区条目(单 item,无句柄/动作)。 */
internal fun LazyListScope.identityItem(markdown: String): Int {
    item(key = "identity", contentType = "md-section") {
        RecordMarkdownContent(markdown = markdown)
    }
    return 1
}

/** 目录条目:导航行直连簿记滚动(RecordTocContent,D2.1 决策),单 item。 */
internal fun LazyListScope.tocSectionItems(
    entries: List<TocEntry>,
    onAnchorClick: (String) -> Unit,
): Int {
    if (entries.isEmpty()) return 0
    item(key = "toc", contentType = "md-section") {
        RecordTocContent(entries = entries, onAnchorClick = onAnchorClick)
    }
    return 1
}
