package com.lioisme.modev.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.ui.logs.markdown.DetailCodeActions
import com.lioisme.modev.ui.logs.markdown.DetailFenceHandle
import com.lioisme.modev.ui.logs.markdown.MarkdownLangs
import com.lioisme.modev.ui.logs.markdown.RecordMarkdownContent
import com.lioisme.modev.ui.logs.markdown.byteSectionMarkdown
import com.lioisme.modev.ui.logs.markdown.capDisplay
import com.lioisme.modev.ui.logs.markdown.metaTableMarkdown
import com.lioisme.modev.ui.logs.markdown.textSectionMarkdown

/**
 * 详情区块渲染引擎(V26 markdown 化重构,取代 V7-V21 三态/Tier A/B 引擎):
 *
 * - 每个内容区块 = 单一 markdown 文档(标题 ### / 字节脚注 / 围栏代码块 /
 *   元数据 GFM 表)+ 单个 LazyColumn item([RecordMarkdownContent] 渲染,
 *   mikepenz multiplatform-markdown-renderer-m3 承载);
 * - 字节区块多表示 = 连续 text/base64/hex 围栏(渲染层聚合为代码组标签页),
 *   单表示退化为单个代码卡;卡片限高滚动,复制/全屏经 [DetailFenceHandle]
 *   操作全量文本;
 * - 显示上限 MARKDOWN_DISPLAY_MAX_CHARS(128 KiB)由工厂钳制「所见」,
 *   完整内容恒可复制/全屏查看(护栏见 [COPY_MAX_CHARS]/[EXPORT_MAX_CHARS]);
 * - 条目组合函数返回其向宿主 LazyColumn 贡献的 item 数(V24 锚点索引簿记契约,
 *   markdown 区块恒为 1)。
 */

/** 复制护栏(V15,不变):超此字符数拒写剪贴板(ClipboardManager 同步物化,防主线程卡死)。 */
internal const val COPY_MAX_CHARS = 4 * 1024 * 1024

/** 导出护栏(V15,不变):超此字符数拒绝落盘(SAF 写流虽可行,但 32M+ JSON 无实际用途)。 */
internal const val EXPORT_MAX_CHARS = 32 * 1024 * 1024

/** 区块文档束:markdown 文本 + 与围栏生成序对齐的全量句柄。 */
data class SectionDoc(val markdown: String, val handles: List<DetailFenceHandle>)

/**
 * 字节区块文档(纯装配,@Composable 仅解析资源):
 * 标题 + 字节统计行(含捕获侧截断标记)+ 各可用表示围栏(text/base64/hex 序)+
 * 超限截断标记;句柄按同一顺序提供全量表示文本。
 */
@Composable
internal fun byteSectionDoc(title: String, view: BytesView): SectionDoc {
    val res = LocalContext.current.resources
    return remember(view, title) {
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
            markdown = byteSectionMarkdown(title, view, footerLine = footer, capMarkerFormatter = capMarker),
            handles = handles,
        )
    }
}

/** 文本区块文档:单 text/sql 围栏 + 尾注段落(省略计数等本地化标记)。 */
@Composable
internal fun textSectionDoc(
    title: String,
    value: String,
    lang: String = MarkdownLangs.TEXT,
    trailerLines: List<String> = emptyList(),
): SectionDoc {
    val res = LocalContext.current.resources
    return remember(value, title, lang, trailerLines) {
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
                ),
            handles = listOf(DetailFenceHandle(label = title, language = lang, fullText = value)),
        )
    }
}

/** 元数据表文档(GFM 两列表;替代原生 MetaGrid,V26 D1 边界决策)。 */
@Composable
internal fun metaTableDoc(entries: List<Pair<String, String>>): String {
    val fieldHeader = stringResource(R.string.meta_table_field)
    val valueHeader = stringResource(R.string.meta_table_value)
    return remember(entries, fieldHeader, valueHeader) {
        metaTableMarkdown(listOf(fieldHeader, valueHeader), entries)
    }
}

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

/** 元数据表条目(单 item)。 */
internal fun LazyListScope.metaTableItems(sectionKey: String, tableMarkdown: String): Int {
    item(key = "section-$sectionKey", contentType = "md-section") {
        RecordMarkdownContent(markdown = tableMarkdown)
    }
    return 1
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

/**
 * 详情分区头(V20 R4):通栏 tonal 条(图标 + titleSmall SemiBold),
 * 与区块标题(primary 文字)形成两级标题层级。
 * 独立 LazyColumn item(不包裹整区,保持虚拟化)。
 */
@Composable
internal fun ZoneHeader(icon: ImageVector, titleRes: Int) {
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

/** 供锚点簿记与显示上限联动使用的展示上限(capDisplay 已内聚,此处仅再导出语义)。 */
internal val DETAIL_DISPLAY_CAP_CHARS = com.lioisme.modev.ui.logs.markdown.MARKDOWN_DISPLAY_MAX_CHARS
