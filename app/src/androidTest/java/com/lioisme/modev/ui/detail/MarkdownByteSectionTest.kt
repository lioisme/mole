package com.lioisme.modev.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.ChunkedText
import com.lioisme.modev.ui.detail.markdown.DetailCodeActions
import com.lioisme.modev.ui.detail.markdown.DetailFenceHandle
import com.lioisme.modev.ui.detail.markdown.FullscreenTextDialog
import com.lioisme.modev.ui.detail.markdown.MARKDOWN_DISPLAY_MAX_CHARS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V26 markdown 详情引擎验收(取代 V14-V19 ByteSectionTest 三态/Tier 契约):
 * ①短内容单围栏全量在树,无标签页;
 * ②三表示 → 代码组三标签(text/base64/hex 文案复用 section_repr_*),
 *   默认选中文本页,非选中内容不在树;切换标签即换页;
 * ③旧记录(base64=null 且无文本)仅 Hex 标签;
 * ④超限(>128Ki 字符)显示前缀 + 截断标记行,复制句柄仍承载全量;
 * ⑤全屏查看器:尾块经滚动可达,复制=全量,关闭返回。
 * 注:代码卡体位于限高滚动容器内,跨设备视口差异下以「存在性」为断言契约,
 * 视觉正确性由人工走查承担(docs §7 手动脚本)。
 */
@RunWith(AndroidJUnit4::class)
class MarkdownByteSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    private fun str(
        id: Int,
        vararg args: Any,
    ) = ctx.getString(id, *args)

    /** 最近一次复制回调(label, fullText)。 */
    private var lastCopied: Pair<String, String>? = null

    /** 最近一次全屏请求句柄。 */
    private var lastFullscreen: DetailFenceHandle? = null

    private fun view(
        hex: String,
        text: String? = null,
        base64: String? = null,
    ) = BytesView(hex = hex, base64 = base64, text = text, totalLength = hex.length / 2, truncated = false)

    private fun setSection(v: BytesView) {
        lastCopied = null
        lastFullscreen = null
        rule.setContent {
            MaterialTheme {
                var fullscreen by remember { mutableStateOf<DetailFenceHandle?>(null) }
                val actions =
                    DetailCodeActions(
                        onCopyFull = { handle -> lastCopied = handle.label to handle.fullText },
                        onFullscreen = { handle ->
                            lastFullscreen = handle
                            fullscreen = handle
                        },
                    )
                // 文档装配须在组合作用域(@Composable 构建器禁入 DSL 作用域)
                val doc = byteSectionDoc("KEY", v)
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        markdownSectionItems(sectionKey = "test", doc = doc, actions = actions)
                    }
                    fullscreen?.let { handle ->
                        FullscreenTextDialog(
                            title = handle.label,
                            fullText = handle.fullText,
                            onDismiss = { fullscreen = null },
                            onCopyFull = { text -> lastCopied = handle.label to text },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    /** 有界轮询等待文本出现。 */
    private fun waitForText(text: String): Boolean {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return true
            Thread.sleep(100)
        }
        return false
    }

    @Test
    fun shortContent_singleFence_allVisible_noTabs() {
        val hex = "abcd"
        setSection(view(hex = hex))
        assertTrue(waitForText(hex))
        // 单表示:无代码组标签页(仅一个表示文案,且不构成 TabRow)。
        rule.onAllNodesWithText(str(R.string.section_repr_text)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.section_repr_base64)).assertCountEquals(0)
    }

    @Test
    fun threeRepresentations_groupTabs_defaultTextSelected() {
        val hex = "cd".repeat(20)
        val b64 = "YWJhYmFiYWJhYmFiYWJhYmFiYWI="
        val text = "abababababababababab"
        setSection(view(hex = hex, text = text, base64 = b64))

        // 三标签在树;默认文本页内容在树,其余两页不在树。
        assertTrue(waitForText(str(R.string.section_repr_hex)))
        assertTrue("text 标签应在树", rule.onAllNodesWithText(str(R.string.section_repr_text)).fetchSemanticsNodes().isNotEmpty())
        assertTrue("base64 标签应在树", rule.onAllNodesWithText(str(R.string.section_repr_base64)).fetchSemanticsNodes().isNotEmpty())
        assertTrue("默认文本页内容应在树", waitForText(text))
        rule.onAllNodesWithText(hex).assertCountEquals(0)
        rule.onAllNodesWithText(b64).assertCountEquals(0)

        // 切 Base64 → 仅 b64 内容。
        rule.onNodeWithText(str(R.string.section_repr_base64)).performClick()
        assertTrue(waitForText(b64))
        rule.onAllNodesWithText(text).assertCountEquals(0)

        // 切 Hex → 仅 hex 内容。
        rule.onNodeWithText(str(R.string.section_repr_hex)).performClick()
        assertTrue(waitForText(hex))
        rule.onAllNodesWithText(b64).assertCountEquals(0)
    }

    @Test
    fun oldRecordWithoutBase64AndText_singleHexCard() {
        val hex = "ab".repeat(20)
        setSection(view(hex = hex))
        // 单表示:无代码组标签页(组语言文案零出现),退化为单卡(语言标签 = HEX)。
        rule.onAllNodesWithText(str(R.string.section_repr_text)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.section_repr_base64)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.section_repr_hex)).assertCountEquals(0)
        assertTrue("hex 内容应在树", waitForText(hex))
    }

    @Test
    fun oversizedContent_displayCapped_markerShown_copyStillFull() {
        // 140K 字符 hex > MARKDOWN_DISPLAY_MAX_CHARS(128K):显示前缀 + 截断标记。
        val fullHex = "ab".repeat(70_000)
        setSection(view(hex = fullHex))

        // V27 修复:detail_truncated_marker 为 plurals 资源(计数复数契约),
        // 经 resources.getQuantityString 解析(此前误引 R.string 为历史遗留编译债)。
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val marker = res.getQuantityString(
            R.plurals.detail_truncated_marker,
            fullHex.length,
            MARKDOWN_DISPLAY_MAX_CHARS,
            fullHex.length,
        )
        assertTrue(waitForText(marker))
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(marker, substring = true))

        // 显示上限前缀在树,完整内容不整段上屏。
        assertTrue(rule.onAllNodesWithText(fullHex).fetchSemanticsNodes().isEmpty())

        // 复制按钮 → 全量句柄(完整 hex 进回调,护栏在屏幕层)。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasTestTag("md-code-block"))
        rule.onAllNodesWithContentDescription(str(R.string.section_copy)).onFirst().performClick()
        rule.waitForIdle()
        assertEquals(fullHex.length, lastCopied?.second?.length)
        assertEquals(fullHex, lastCopied?.second)
        assertNull(lastFullscreen)
    }

    @Test
    fun fullscreenButton_opensDialog_fullTailReachable_thenClose() {
        // 300K text(hex 600K):全屏查看器 ChunkedText 虚拟化,尾块滚动可达。
        val text = "a".repeat(300_000)
        val hex = "ab".repeat(150_000)
        setSection(view(hex = hex, text = text))

        // 全屏按钮 = 图标 contentDescription(detail_fullscreen)。
        rule.onNodeWithContentDescription(str(R.string.detail_fullscreen)).performClick()
        rule.waitForIdle()
        // Dialog 打开(退出按钮出现即标志)。
        assertTrue(
            "全屏对话框应打开",
            rule.onAllNodesWithContentDescription(str(R.string.detail_exit_fullscreen)).fetchSemanticsNodes().isNotEmpty(),
        )

        // 尾块经滚动可达(O(1) 取块契约:末块内容精确存在)。
        val chunks = ChunkedText(text)
        val lastChunk = chunks[chunks.size - 1]
        rule.onNodeWithTag("fullscreen-list")
            .performScrollToNode(hasText(lastChunk, substring = false))
        assertTrue(waitForText(lastChunk))

        // 复制(弹层内)= 全量文本。
        rule.onAllNodesWithContentDescription(str(R.string.section_copy)).onFirst().performClick()
        rule.waitForIdle()
        assertEquals(text, lastCopied?.second)

        // 关闭返回。
        rule.onNodeWithContentDescription(str(R.string.detail_exit_fullscreen)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.detail_exit_fullscreen)).assertCountEquals(0)
    }
}
