package com.lioisme.modev.ui.logs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.ChunkedText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 定向测试(ByteSection,2026-08-17 V13 去折叠契约):
 * - 无折叠按钮:任何数据量下区块恒展开(移除行数阈值折叠交互);
 * - 全量惰性组合:仅可视块在树中,滚动按需创建([ChunkedText] O(1) 取块),
 *   全量 hex/text 永不为单一节点;
 * - 长按任意块复制「整段」(text 块复制全文 / hex 块复制全 hex),宿主回调
 *   收到完整文本,而非仅当前块;
 * - footer 复制按钮([section_copy] 描述)复制完整 hex(无障碍/可发现入口)。
 * 实机 100K 字符单 Text 展开 26s ANR 的回归防线(恒展开 + 惰性分块)。
 */
@RunWith(AndroidJUnit4::class)
class ByteSectionTest {

    @get:Rule
    val rule = createComposeRule()

    /** 最近一次 [copyFull] 回调收到的完整文本(行为级断言宿主整段复制)。 */
    private var lastCopied: String? = null

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun view(hex: String, text: String? = "plain text") = BytesView(
        hex = hex,
        text = text,
        totalLength = hex.length / 2,
        truncated = false,
    )

    /** 长按需跨帧时钟推进(down 后推 700ms 虚拟时间再 up),单纯 longClick() 不推时钟导致长按检测永不触发。 */
    private fun longPressOn(text: String) {
        rule.onNodeWithText(text, substring = false).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(700)
        rule.onNodeWithText(text, substring = false).performTouchInput { up() }
        rule.waitForIdle()
    }

    private fun setSection(
        hex: String,
        text: String? = "plain text",
    ) {
        lastCopied = null
        rule.setContent {
            MaterialTheme {
                var copied by remember { mutableStateOf<String?>(null) }
                lastCopied = copied
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = R.string.section_key,
                        view = view(hex, text),
                        copyFull = { _, fullText -> copied = fullText },
                    )
                }
            }
        }
    }

    @Test
    fun smallData_alwaysExpanded_noToggleButton() {
        val hex = "abcd"
        setSection(hex = hex, text = null)
        // 恒展开:全量 hex 可见;折叠交互彻底移除(无「展开/收起」按钮)
        rule.onNodeWithText(hex).assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.section_expand)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.section_collapse)).assertCountEquals(0)
    }

    @Test
    fun largeData_alwaysExpanded_lazyChunksNotSingleNode() {
        val full = "ab".repeat(5_000) // 10K 字符 → 3 块(4096/4096/1808)
        setSection(full, text = null)
        // 首块组合且可见,全量 hex 永不是单一节点(分块契约核心)
        rule.onNodeWithText(full.take(ChunkedText.DEFAULT_CHUNK_CHARS)).assertIsDisplayed()
        rule.onAllNodesWithText(full).assertCountEquals(0)
        // 无折叠按钮(展开态常驻,无「收起」入口)
        rule.onAllNodesWithText(str(R.string.section_collapse)).assertCountEquals(0)
    }

    @Test
    fun oversizedHex_expandsLazilyChunkByChunk() {
        // 300K 字符,前 150K 为 ab 段、后 150K 为 cd 段
        val full = "ab".repeat(75_000) + "cd".repeat(75_000)
        setSection(full, text = null)
        val chunks = ChunkedText(full)
        val lastChunk = chunks[chunks.size - 1]
        // 惰性:末块未滚动时不在树中(精确匹配,防前缀块子串误命中)
        rule.onAllNodes(hasText(lastChunk, substring = false)).assertCountEquals(0)
        // 滚动到末块后其组合成功(按需创建,无巨型单 Text)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastChunk, substring = false))
        rule.onNode(hasText(lastChunk, substring = false)).assertIsDisplayed()
    }

    @Test
    fun oversizedText_expandsLazilyChunkByChunk() {
        // 300K 可打印文本(hex 600K),文本亦分块惰性组合
        val text = "a".repeat(300_000)
        val hex = "ab".repeat(150_000)
        setSection(hex = hex, text = text)
        val textChunks = ChunkedText(text)
        val lastTextChunk = textChunks[textChunks.size - 1]
        rule.onAllNodes(hasText(lastTextChunk, substring = false)).assertCountEquals(0)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastTextChunk, substring = false))
        rule.onNode(hasText(lastTextChunk, substring = false)).assertIsDisplayed()
    }

    @Test
    fun textChunkLongPress_copiesFullTextNotJustChunk() {
        // 块间互异:块1=ab×2048、块2=cd×2048、块3=ef×904(hex 3 块同长)
        val hex = "ab".repeat(2048) + "cd".repeat(2048) + "ef".repeat(904)
        val text = "T".repeat(300)
        setSection(hex = hex, text = text)
        // 长按 text 块 → 宿主收到整段全文(而非仅当前块)
        longPressOn("T".repeat(300))
        assertEquals(text, lastCopied)
    }

    @Test
    fun hexChunkLongPress_copiesFullHex() {
        // 块间互异:块1=ab×2048、块2=cd×2048、块3=ef×904,精确匹配唯一节点;
        // 4096 字符单块超一屏,先滚动至目标块再长按。
        val full = "ab".repeat(2048) + "cd".repeat(2048) + "ef".repeat(904)
        setSection(full, text = null)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("cd".repeat(2048), substring = false))
        rule.waitForIdle()
        longPressOn("cd".repeat(2048))
        assertEquals(full, lastCopied)
    }

    @Test
    fun copyButton_copiesFullHex() {
        val full = "ab".repeat(5_000)
        setSection(full, text = null)
        // footer 复制按钮(无障碍入口,contentDescription = section_copy)
        rule.onNodeWithContentDescription(str(R.string.section_copy)).performClick()
        rule.waitForIdle()
        assertEquals(full, lastCopied)
    }

    @Test
    fun emptyText_rendersHexChunksOnly_noCopyButtonSpam() {
        val hex = "abcd"
        setSection(hex = hex, text = null)
        rule.onNodeWithText(hex).assertIsDisplayed()
        // 恰一个复制入口(footer),无冗余
        rule.onAllNodesWithContentDescription(str(R.string.section_copy)).assertCountEquals(1)
    }
}