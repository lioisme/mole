package com.lioisme.modev.ui.logs.markdown

import com.lioisme.modev.core.util.BytesView

/**
 * V26 详情页 markdown 工厂(纯 Kotlin,零 Compose/UI 依赖,JVM 直测):
 *
 * - 记录区块 → 规范 markdown 文本(标题 ### / GFM 表格 / 围栏代码块);
 * - 字节区块多表示 = 连续 text/base64/hex 围栏(空行分隔,渲染层由
 *   [CodeGroupExtractor] 聚合为代码组标签页);单表示退化为单个代码块;
 * - 显示上限 [MARKDOWN_DISPLAY_MAX_CHARS]:超限仅显示前缀 + 截断标记行
 *   (标记文案由调用方本地化后经 [capMarkerFormatter] 注入,工厂零硬编码文案);
 * - 完整内容不经 markdown 承载——复制/全屏走 [DetailFenceHandle](全量文本 +
 *   护栏在 UI 层统一执行),markdown 仅负责「所见」。
 */

/** 单代码块显示上限(承接原 SELECTABLE_CAP_CHARS 128 KiB 语义):单 Text 有界布局成本可控。 */
const val MARKDOWN_DISPLAY_MAX_CHARS = 128 * 1024

/** 代码组围栏语言标签(渲染契约:仅此集合内的连续围栏聚合为代码组)。 */
object MarkdownLangs {
    const val TEXT = "text"
    const val BASE64 = "base64"
    const val HEX = "hex"
}

/** 内容含 N 个连续反引号 → 围栏长度 = N+1(≥3),保证内容永不破坏围栏边界(纯函数)。 */
fun mdFenceRunLength(content: String): Int {
    var maxRun = 0
    var run = 0
    for (c in content) {
        if (c == '`') {
            run++
            if (run > maxRun) maxRun = run
        } else {
            run = 0
        }
    }
    return (maxRun + 1).coerceAtLeast(3)
}

/** 安全围栏代码块(纯函数):按内容反引号最长 run 自适应围栏长度。 */
fun mdFence(
    lang: String,
    content: String,
): String {
    val ticks = "`".repeat(mdFenceRunLength(content))
    return buildString {
        append(ticks).append(lang).append('\n')
        append(content)
        if (content.isNotEmpty() && !content.endsWith('\n')) append('\n')
        append(ticks)
    }
}

/** GFM 表格单元格转义:管道符转义、换行折叠为空格(防表格结构被值破坏)。 */
fun mdEscapeCell(value: String): String =
    value.replace("\r", "").replace("\n", " ").replace("|", "\\|")

/** GFM 表格(紧凑管道格式):单元格零内边距空格 —— 解析器会保留管道前空格
 *  为单元格尾随字符(V26 设备实测:'| Cell |' → 文本 'Cell '),紧凑形态
 *  '|Cell|' 保证语义文本精确等于单元格内容,选择/断言零噪声。 */
fun mdTable(
    headers: List<String>,
    rows: List<List<String>>,
): String = buildString {
    append("|")
    append(headers.joinToString("|") { mdEscapeCell(it) })
    append("|\n|")
    append(headers.joinToString("|") { "---" })
    append("|\n")
    rows.forEach { row ->
        append("|")
        append(row.joinToString("|") { mdEscapeCell(it) })
        append("|\n")
    }
}

/** 标题文本净化:换行折叠为空格(标题恒单行)。 */
private fun mdHeadingText(text: String): String = text.replace("\r", "").replace('\n', ' ')

/** 截断显示切分(纯函数):≤ 上限原样;超过截前缀并标记。 */
data class CappedDisplay(val display: String, val totalChars: Int, val cappedByLimit: Boolean)

fun capDisplay(value: String): CappedDisplay =
    if (value.length <= MARKDOWN_DISPLAY_MAX_CHARS) {
        CappedDisplay(value, value.length, cappedByLimit = false)
    } else {
        CappedDisplay(value.take(MARKDOWN_DISPLAY_MAX_CHARS), value.length, cappedByLimit = true)
    }

/** 字节区块可用表示的展示顺序(与捕获侧持久化三态一致:文本 / Base64 / Hex)。 */
private fun byteFenceInputs(view: BytesView): List<Pair<String, String?>> = listOf(
    MarkdownLangs.TEXT to view.text,
    MarkdownLangs.BASE64 to view.base64,
    MarkdownLangs.HEX to view.hex,
)

/**
 * 字节区块 markdown 文档:`### 标题` + 脚注行(字节数/截断态,调用方本地化)+
 * 各可用表示的安全围栏(text/base64/hex 相邻发射,供代码组聚合)+
 * 截断标记行(引用块形态,**恒置于全部围栏之后**——标记夹在围栏之间会打断
 * 连续性、使代码组退化为多张单卡,V26 设备实测沉淀)。
 *
 * @param footerLine 已本地化的字节统计行(null = 省略)
 * @param capMarkerFormatter 截断标记格式器((displayed, total) → 文案;null = 不渲染标记)
 */
fun byteSectionMarkdown(
    title: String,
    view: BytesView,
    footerLine: String? = null,
    capMarkerFormatter: ((displayed: Int, total: Int) -> String)? = null,
): String = buildString {
    append("### ").append(mdHeadingText(title)).append("\n\n")
    if (!footerLine.isNullOrEmpty()) {
        append(mdHeadingText(footerLine)).append("\n\n")
    }
    val groupMarkers = mutableListOf<String>()
    byteFenceInputs(view).forEach { (lang, full) ->
        val content = full ?: return@forEach
        val capped = capDisplay(content)
        append(mdFence(lang, capped.display)).append("\n\n")
        val formatter = capMarkerFormatter
        if (capped.cappedByLimit && formatter != null) {
            groupMarkers += formatter(capped.display.length, capped.totalChars)
        }
    }
    // 标记统一后置:围栏间零插入物(EOL 空白除外),代码组聚合契约的前提
    groupMarkers.forEach { marker ->
        append("> ").append(mdHeadingText(marker)).append("\n\n")
    }
}.trimEnd('\n') + "\n"

/**
 * 文本区块 markdown 文档:`### 标题` + 单个 text/sql 围栏 + 尾注段落
 * (省略计数等本地化标记,不进围栏以免污染可复制语义——复制走全量句柄)。
 */
fun textSectionMarkdown(
    title: String,
    value: String,
    lang: String = MarkdownLangs.TEXT,
    capMarkerFormatter: ((displayed: Int, total: Int) -> String)? = null,
    trailerLines: List<String> = emptyList(),
): String = buildString {
    append("### ").append(mdHeadingText(title)).append("\n\n")
    val capped = capDisplay(value)
    append(mdFence(lang, capped.display)).append("\n\n")
    val formatter = capMarkerFormatter
    if (capped.cappedByLimit && formatter != null) {
        append("> ").append(mdHeadingText(formatter(capped.display.length, capped.totalChars))).append("\n\n")
    }
    trailerLines.forEach { line ->
        append(mdHeadingText(line)).append("\n\n")
    }
}.trimEnd('\n') + "\n"

/**
 * 元数据 GFM 表(两列表头由调用方本地化传入;值转义由 [mdTable] 统一处理)。
 * 替代原生两列网格(MetaGrid,V26 决策 D1:语义色随 markdown 化让位,
 * 身份类徽章保留于 Hero 卡)。
 */
fun metaTableMarkdown(
    headers: List<String>,
    entries: List<Pair<String, String>>,
): String = mdTable(headers, entries.map { (label, value) -> listOf(label, value) })

/** 引用块尾注行(独立使用场景:向既有文档追加省略标记)。 */
fun mdQuoteLine(line: String): String = "> ${mdHeadingText(line)}"
