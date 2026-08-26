package com.lioisme.modev.ui.logs.markdown

import com.lioisme.modev.core.util.BytesView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * markdown 工厂纯函数契约(V26):
 * - 围栏安全:内容含连续反引号 → 围栏长度 = 最长 run+1(≥3),内容零破坏;
 * - 表格转义:管道符/换行不破坏 GFM 结构;
 * - 字节区块:多表示 = 连续 text/base64/hex 围栏(顺序稳定、空行分隔),
 *   缺失表示省略;超限截断显示前缀并追加标记行;
 * - 文本区块:单围栏 + 尾注段落;元数据表两列对齐。
 */
class RecordMarkdownFactoryTest {

    private fun view(
        hex: String,
        text: String? = null,
        base64: String? = null,
        truncated: Boolean = false,
    ) = BytesView(hex = hex, base64 = base64, text = text, totalLength = hex.length / 2, truncated = truncated)

    // ---- 围栏安全 ----

    @Test
    fun fence_plain_content_uses_triple_ticks() {
        assertEquals("```text\nabc\n```", mdFence(MarkdownLangs.TEXT, "abc"))
    }

    @Test
    fun fence_content_with_backticks_extends_fence() {
        val content = "has ``` inside"
        val md = mdFence("text", content)
        assertTrue(md.startsWith("````text\n"))
        assertTrue(md.trimEnd().endsWith("````"))
        assertTrue(md.contains(content))
    }

    @Test
    fun fence_run_length_counts_longest_run() {
        assertEquals(3, mdFenceRunLength("plain"))
        assertEquals(4, mdFenceRunLength("a```b"))
        assertEquals(5, mdFenceRunLength("````x`"))
        assertEquals(3, mdFenceRunLength(""))
    }

    @Test
    fun fence_empty_content_still_well_formed() {
        assertEquals("```hex\n```", mdFence(MarkdownLangs.HEX, ""))
    }

    // ---- 表格 ----

    @Test
    fun table_renders_header_separator_rows() {
        val md = mdTable(listOf("字段", "值"), listOf(listOf("k1", "v1"), listOf("k2", "v2")))
        val lines = md.trimEnd().split("\n")
        assertEquals("|字段|值|", lines[0])
        assertEquals("|---|---|", lines[1])
        assertEquals("|k1|v1|", lines[2])
        assertEquals("|k2|v2|", lines[3])
    }

    @Test
    fun table_escapes_pipes_and_newlines_in_cells() {
        val md = mdTable(listOf("h"), listOf(listOf("a|b\nc")))
        assertFalse(md.contains("a|b"))
        assertTrue(md.contains("a\\|b c"))
    }

    // ---- capDisplay ----

    @Test
    fun cap_display_below_limit_is_passthrough() {
        val capped = capDisplay("abc")
        assertEquals("abc", capped.display)
        assertEquals(3, capped.totalChars)
        assertFalse(capped.cappedByLimit)
    }

    @Test
    fun cap_display_above_limit_truncates_prefix() {
        val value = "x".repeat(MARKDOWN_DISPLAY_MAX_CHARS + 1)
        val capped = capDisplay(value)
        assertEquals(MARKDOWN_DISPLAY_MAX_CHARS, capped.display.length)
        assertEquals(value.length, capped.totalChars)
        assertTrue(capped.cappedByLimit)
    }

    // ---- byteSectionMarkdown ----

    @Test
    fun byte_section_three_representations_emit_adjacent_fences() {
        val md =
            byteSectionMarkdown(
                title = "输入",
                view = view(hex = "deadbeef", text = "hello", base64 = "aGVsbG8="),
                footerLine = "4 bytes",
            )
        val lines = md.split("\n")
        assertEquals("### 输入", lines[0])
        assertEquals("4 bytes", lines[2])
        assertTrue(lines.contains("```text"))
        assertTrue(lines.contains("```base64"))
        assertTrue(lines.contains("```hex"))
        // 顺序:文本 → Base64 → Hex(生成序 = 句柄寻址契约)
        assertTrue(md.indexOf("```text") < md.indexOf("```base64"))
        assertTrue(md.indexOf("```base64") < md.indexOf("```hex"))
    }

    @Test
    fun byte_section_missing_representations_omitted_hex_always_present() {
        val md = byteSectionMarkdown(title = "T", view = view(hex = "ab"), footerLine = null)
        assertFalse(md.contains("```text\n"))
        assertFalse(md.contains("```base64"))
        assertTrue(md.contains("```hex\nab\n```"))
    }

    @Test
    fun byte_section_oversized_content_appends_cap_marker() {
        val bigText = "y".repeat(MARKDOWN_DISPLAY_MAX_CHARS + 10)
        var markerArgs: Pair<Int, Int>? = null
        val md =
            byteSectionMarkdown(
                title = "T",
                view = view(hex = "00", text = bigText),
                footerLine = null,
                capMarkerFormatter = { d, t ->
                    markerArgs = d to t
                    "MARKER($d/$t)"
                },
            )
        assertTrue(md.contains("```text\n" + "y".repeat(MARKDOWN_DISPLAY_MAX_CHARS) + "\n```"))
        assertTrue(md.contains("> MARKER(${markerArgs?.first}/${markerArgs?.second})"))
        assertEquals(MARKDOWN_DISPLAY_MAX_CHARS to bigText.length, markerArgs)
        // 完整内容不经 markdown 承载(仅前缀显示)
        assertFalse(md.contains("y".repeat(MARKDOWN_DISPLAY_MAX_CHARS + 10)))
    }

    @Test
    fun cap_markers_emitted_after_all_fences_keep_group_adjacent() {
        // V26 设备实测沉淀:标记夹在围栏之间会打断代码组聚合 → 恒置于全部围栏之后
        val bigText = "y".repeat(MARKDOWN_DISPLAY_MAX_CHARS + 5)
        val md =
            byteSectionMarkdown(
                title = "T",
                view = view(hex = "00", text = bigText),
                footerLine = null,
                capMarkerFormatter = { d, _ -> "M($d)" },
            )
        val markerIdx = md.indexOf("> M(")
        assertTrue(markerIdx >= 0)
        // hex 围栏在标记之前(与 text 围栏保持相邻)
        val hexFenceIdx = md.indexOf("```hex")
        assertTrue(hexFenceIdx in 0 until markerIdx)
    }

    // ---- textSectionMarkdown / metaTableMarkdown ----

    @Test
    fun text_section_emits_heading_fence_and_trailers() {
        val md =
            textSectionMarkdown(
                title = "调用堆栈",
                value = "frame.a()\nframe.b()",
                trailerLines = listOf("+3 more"),
            )
        assertTrue(md.startsWith("### 调用堆栈\n\n```text\nframe.a()\nframe.b()\n```\n\n+3 more"))
    }

    @Test
    fun meta_table_two_columns() {
        val md = metaTableMarkdown(listOf("Field", "Value"), listOf("类别" to "Cipher", "线程" to "m#a"))
        assertTrue(md.contains("|Field|Value|"))
        assertTrue(md.contains("|类别|Cipher|"))
        assertTrue(md.contains("|线程|m#a|"))
    }

    // ---- V28 纯 markdown 化:identity / toc / 节标题层级 ----

    @Test
    fun section_heading_defaults_to_h2_and_sanitizes_newlines() {
        assertEquals("## 数据流向\n\n", mdSectionHeading("数据流向"))
        assertEquals("## a b\n\n", mdSectionHeading("a\nb"))
        assertTrue(mdSectionHeading("T", level = 3).startsWith("### T"))
        assertTrue(mdSectionHeading("T", level = 9).startsWith("###### T"))
    }

    @Test
    fun byte_section_heading_level_overridable() {
        val md = byteSectionMarkdown(title = "输入", view = view(hex = "ab"), headingLevel = 2)
        assertTrue(md.startsWith("## 输入\n\n```hex"))
    }

    @Test
    fun text_section_heading_level_overridable() {
        val md = textSectionMarkdown(title = "参数", value = "{}", lang = MarkdownLangs.JSON, headingLevel = 2)
        assertTrue(md.startsWith("## 参数\n\n```json"))
    }

    @Test
    fun identity_markdown_headline_with_optional_accent() {
        assertEquals("## AES/CBC\n", identityMarkdown("AES/CBC"))
        assertEquals(
            "## db.sqlite\n\n**QUERY · DML · 写入**\n",
            identityMarkdown("db.sqlite", "QUERY · DML · 写入"),
        )
    }

    @Test
    fun markdown_langs_json_never_groups_with_adjacent_fences() {
        // 行为级契约:相邻 text/json 围栏中,text 孤立成 runSize=1 的平凡组,
        // json 完全不具组资格(placement=null)——json 永不参与聚合
        val parser = org.intellij.markdown.parser.MarkdownParser(org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor())
        val md = "```text\nabc\n```\n\n```json\n{}\n```"
        val idx = buildFenceIndex(parser.buildMarkdownTreeFromString(md), md)
        assertEquals(2, idx.size)
        assertEquals(1, idx[0]?.placement?.runSize)
        assertNull(idx[1]?.placement)
    }
}
