package com.lioisme.modev.ui.detail.markdown

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 代码组提取器契约(V26,纯 JVM):
 * - 工厂生成的连续 text/base64/hex 围栏聚合为单一代码组(空行分隔不中断链);
 * - 非组语言围栏(sql)与被普通块级内容隔断的围栏自成孤立条目;
 * - ordinal 与文档围栏出现序严格一致(全量句柄寻址键);
 * - 围栏代码体提取与库渲染偏移规则一致。
 */
class CodeGroupExtractorTest {

    private fun index(md: String): MarkdownFenceIndex = buildFenceIndex(parse(md), md)

    private fun parse(md: String) = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(md)

    @Test
    fun consecutive_group_fences_form_single_run() {
        val md =
            """
            ```text
            hello
            ```

            ```base64
            aGVsbG8=
            ```

            ```hex
            deadbeef
            ```
            """.trimIndent()
        val idx = index(md)
        assertEquals(3, idx.size)
        // 全部归入同一组:runFirstOrdinal=0,size=3
        for (ordinal in 0..2) {
            val placement = idx[ordinal]?.placement
            assertNotNull("ordinal $ordinal should be in a group", placement)
            assertEquals(0, placement!!.runFirstOrdinal)
            assertEquals(3, placement.runSize)
            assertEquals(ordinal, placement.indexInRun)
        }
    }

    @Test
    fun non_group_language_fence_stays_isolated() {
        val md =
            """
            ```sql
            SELECT 1
            ```
            """.trimIndent()
        val idx = index(md)
        assertEquals(1, idx.size)
        assertNull(idx[0]!!.placement)
        assertEquals("sql", idx[0]!!.info!!.language)
    }

    @Test
    fun paragraph_between_fences_breaks_the_run() {
        val md =
            """
            ```text
            a
            ```

            some paragraph

            ```hex
            ab
            ```
            """.trimIndent()
        val idx = index(md)
        assertEquals(2, idx.size)
        // text:新组起点;hex:被段落隔断 → 自成单元素组(runSize=1,渲染退化为单卡)
        assertEquals(0, idx[0]!!.placement!!.runFirstOrdinal)
        assertEquals(1, idx[0]!!.placement!!.runSize)
        assertEquals(1, idx[1]!!.placement!!.runFirstOrdinal)
        assertEquals(0, idx[1]!!.placement!!.indexInRun)
        assertEquals(1, idx[1]!!.placement!!.runSize)
    }

    @Test
    fun ordinals_follow_document_order() {
        val md =
            """
            ### 标题

            ```hex
            01
            ```

            段落

            ```sql
            SELECT 2
            ```

            ```text
            t
            ```
            """.trimIndent()
        val idx = index(md)
        assertEquals(3, idx.size)
        assertEquals(MarkdownLangs.HEX, idx[0]!!.info!!.language)
        assertEquals("01", idx[0]!!.info!!.code.trim())
        assertEquals("sql", idx[1]!!.info!!.language)
        assertNull(idx[1]!!.placement)
        // text 与 sql 被段落隔断:text 自成新组起点(孤立单元素组)
        assertNotNull(idx[2]!!.placement)
        assertEquals(0, idx[2]!!.placement!!.indexInRun)
        assertEquals(1, idx[2]!!.placement!!.runSize)
    }

    @Test
    fun fence_info_extracts_code_body() {
        val md = "```base64\nYWJj\n```"
        val idx = index(md)
        assertEquals("base64", idx[0]!!.info!!.language)
        assertEquals("YWJj", idx[0]!!.info!!.code.trim())
    }
}
