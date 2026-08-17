package com.lioisme.modev.ui.logs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.util.BytesView
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 定向测试(ByteSection,2026-08-18 V7 惰性分块契约;2026-08-17 V7 复制收敛):
 * 头部折叠态仅渲染预览(全量 hex 不组合)→ 展开态按块惰性组合(仅可视块
 * 在树中,滚动按需创建)→ 收起回预览;复制回调已随 V7 收敛移除。
 * 实机 100K 字符单 Text 展开 26s ANR 的回归防线。
 */
@RunWith(AndroidJUnit4::class)
class ByteSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun view(hex: String) = BytesView(
        hex = hex,
        text = "plain text",
        totalLength = hex.length / 2,
        truncated = false,
    )

    private fun setSection(
        hex: String,
        expanded: Boolean,
        onToggleExpanded: () -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = R.string.section_key,
                        view = view(hex),
                        exportableNote = false,
                        expanded = expanded,
                        onToggleExpanded = onToggleExpanded,
                    )
                }
            }
        }
    }

    @Test
    fun collapsedShowsPreviewOnly() {
        val full = "ab".repeat(5_000) // 10K 字符 > 预览上限 4096
        setSection(full, expanded = false)
        rule.onAllNodesWithText(full).assertCountEquals(0)
        rule.onNodeWithText(full.take(BytesView.DEFAULT_PREVIEW_CHARS) + "…(+${5_000 - BytesView.DEFAULT_PREVIEW_CHARS / 2} bytes)")
            .assertIsDisplayed()
    }

    @Test
    fun expandComposesFirstChunkAndFullHexNeverSingleNode() {
        val full = "ab".repeat(5_000) // 10K 字符 → 3 块(4096/4096/1808)
        setSection(full, expanded = true)
        // 首块组合且可见
        rule.onNodeWithText(full.take(EXPAND_CHUNK_CHARS)).assertIsDisplayed()
        // 全量 hex 永远不是单一节点(分块契约核心)
        rule.onAllNodesWithText(full).assertCountEquals(0)
        // 折叠态预览不再渲染(展开态头部无预览)
        rule.onAllNodesWithText(full.take(BytesView.DEFAULT_PREVIEW_CHARS) + "…(+${5_000 - BytesView.DEFAULT_PREVIEW_CHARS / 2} bytes)")
            .assertCountEquals(0)
    }

    @Test
    fun oversizedHexExpandsLazilyChunkByChunk() {
        // 300K 字符,前 150K 为 ab 段、后 150K 为 cd 段
        val full = "ab".repeat(75_000) + "cd".repeat(75_000)
        setSection(full, expanded = true)
        val lastChunk = full.chunked(EXPAND_CHUNK_CHARS).last()
        // 惰性:末块未滚动时不在树中(精确匹配,防前缀块子串误命中)
        rule.onAllNodes(hasText(lastChunk, substring = false)).assertCountEquals(0)
        // 滚动到末块后其组合成功(按需创建,无巨型单 Text)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastChunk, substring = false))
        rule.onNode(hasText(lastChunk, substring = false)).assertIsDisplayed()
    }

    @Test
    fun expandButtonTogglesExpandedState() {
        rule.setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf(false) }
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = R.string.section_key,
                        view = view("abcd"),
                        exportableNote = false,
                        expanded = expanded,
                        onToggleExpanded = { expanded = !expanded },
                    )
                }
            }
        }
        rule.onNodeWithText(str(R.string.section_expand)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.section_collapse)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.section_collapse)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.section_expand)).assertIsDisplayed()
    }
}