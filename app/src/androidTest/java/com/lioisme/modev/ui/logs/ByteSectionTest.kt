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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 定向测试(ByteSection,2026-08-18 V14 R2/R3 + V15 双层渲染契约;
 * V19 T5 三态分段):
 * ①短内容(≤4096 字符)无折叠控件,整段单 Text 全量可见;
 * ②>4096 字符默认收起:预览(ReadMoreText,「… 展开更多」)在树、分块不在;
 * ③点「展开更多」→ Tier A(≤128K 字符)整段单 Text 组合(整段可选中),「收起」出现;
 * ④点「收起」→ 回折叠态(预览在树、分块不在);
 * ⑤Tier A 长按整段 → 不触发整段复制回调(SelectionContainer 原生选择);
 * ⑥footer 复制按钮仍复制完整 hex;
 * ⑦>128K 字符(Tier B)展开 → 分块惰性组合,无整段单 Text;
 * ⑧300K hex/text 惰性滚动回归(V11 性能防线:末块未滚动不在树,滚动后组合成功);
 * ⑨V19 T5 三态:分段按钮切换渲染、旧记录(base64=null)隐藏 Base64 段、
 *   Tier B Base64 分块惰性组合。
 * 实机 100K 字符单 Text 展开 26s ANR 的回归防线(收起预览有界 + 展开惰性分块)。
 */
@RunWith(AndroidJUnit4::class)
class ByteSectionTest {

    @get:Rule
    val rule = createComposeRule()

    /** 最近一次 [copyFull] 回调收到的完整文本(行为级断言宿主整段复制)。 */
    private var lastCopied: String? = null

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun view(hex: String, text: String? = "plain text", base64: String? = null) = BytesView(
        hex = hex,
        base64 = base64,
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
        base64: String? = null,
        initiallyExpanded: Boolean = false,
    ) {
        lastCopied = null
        val v = view(hex, text, base64)
        rule.setContent {
            MaterialTheme {
                var copied by remember { mutableStateOf<String?>(null) }
                lastCopied = copied
                var expanded by remember { mutableStateOf(initiallyExpanded) }
                var representation by remember { mutableStateOf(v.defaultRepresentation()) }
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = R.string.section_key,
                        view = v,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        copyFull = { _, fullText -> copied = fullText },
                        representation = representation,
                        onRepresentationChange = { representation = it },
                    )
                }
            }
        }
    }

    @Test
    fun smallData_noFoldControls_allVisible() {
        val hex = "abcd"
        setSection(hex = hex, text = null)
        // ①短内容:整段单 Text 全量可见,无任何折叠控件(展开更多/收起按钮均不在树)
        rule.onNodeWithText(hex).assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.record_see_more)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.section_collapse)).assertCountEquals(0)
    }

    @Test
    fun largeData_defaultCollapsed_previewInTree_contentAbsent() {
        // ②>4096 字符默认收起:10K hex → 预览(前 4096 字符)唯一在树,
        // 内容节点不在树;「展开更多」链接在树。
        val full = "ab".repeat(5_000)
        setSection(full, text = null)
        // ReadMoreText 预览的语义文本仅含可见片段(整段可点,「… 展开更多」并入
        // 同一节点),故用短前缀 + 展开文案断言,全量内容精确匹配必须为 0。
        rule.onAllNodesWithText("ab".repeat(16), substring = true).assertCountEquals(1)
        rule.onAllNodesWithText(full, substring = false).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.record_see_more), substring = true).assertCountEquals(1)
    }

    @Test
    fun clickSeeMore_expandsIntoSingleSelectableSegment() {
        // ③点「展开更多」→ Tier A(≤128K 字符)整段单 Text 组合(10K hex 全量单节点,
        // 系统「全选」可一次覆盖整段),「收起」按钮出现,「展开更多」链接消失。
        val full = "ab".repeat(5_000)
        setSection(full, text = null)
        // 「展开更多」并入预览节点,substring 命中唯一含它的节点;点击即展开。
        rule.onNodeWithText(str(R.string.record_see_more), substring = true).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(full, substring = false).assertCountEquals(1)
        // 「收起」在展开内容末尾,可能位于视口外,先滚动至可见再断言/点击。
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(R.string.section_collapse)))
        rule.onNodeWithText(str(R.string.section_collapse)).assertIsDisplayed()
        rule.onAllNodesWithText(str(R.string.record_see_more), substring = true).assertCountEquals(0)
    }

    @Test
    fun collapseButton_returnsToCollapsedPreview() {
        // ④展开态点「收起」→ 回折叠态:预览唯一在树、内容不在树、展开链接回来。
        val full = "ab".repeat(5_000)
        setSection(full, text = null, initiallyExpanded = true)
        // 「收起」在展开内容末尾(视口外),先滚动至可见再点击。
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(R.string.section_collapse)))
        rule.onNodeWithText(str(R.string.section_collapse)).performClick()
        rule.waitForIdle()
        // 回折叠态:可见预览前缀唯一在树(语义文本仅含可见片段),展开链接并入预览节点。
        rule.onAllNodesWithText("ab".repeat(16), substring = true).assertCountEquals(1)
        rule.onAllNodesWithText(full, substring = false).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.record_see_more), substring = true).assertCountEquals(1)
        rule.onAllNodesWithText(str(R.string.section_collapse)).assertCountEquals(0)
    }

    @Test
    fun tierA_longPress_triggersNoWholeSectionCopy() {
        // ⑤Tier A 展开态长按整段 → 不触发整段复制回调(SelectionContainer 原生选择,
        // 系统选择手柄属平台 UI,不进组合树)。
        val full = "ab".repeat(5_000)
        setSection(full, text = null, initiallyExpanded = true)
        longPressOn(full)
        assertNull(lastCopied)
    }

    @Test
    fun copyButton_opensMenu_copiesFullHex() {
        // ⑥footer 复制按钮(V16 R3 菜单化)仍复制完整 hex,不受折叠影响:
        // 点按钮 → 菜单 → 「复制 Hex」→ 完整 hex 进回调。
        val full = "ab".repeat(5_000)
        setSection(full, text = null)
        rule.onNodeWithContentDescription(str(R.string.section_copy)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.section_copy_hex)).performClick()
        rule.waitForIdle()
        assertEquals(full, lastCopied)
    }

    @Test
    fun copyMenu_copiesTextAndBase64_whenPresent() {
        // V19 T5:表示齐全时菜单含「复制文本/复制 Base64/复制 Hex」三项;
        // 文本项复制纯文本,Base64 项复制 Base64 串(移除 V16 的「文本+Hex」合并项)。
        val full = "ab".repeat(5_000)
        val b64 = "YWJhYmFiYWJhYmFiYWJhYmFiYWI="
        setSection(full, text = "plain text", base64 = b64)
        rule.onNodeWithContentDescription(str(R.string.section_copy)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.section_copy_text)).performClick()
        rule.waitForIdle()
        assertEquals("plain text", lastCopied)

        rule.onNodeWithContentDescription(str(R.string.section_copy)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.section_copy_base64)).performClick()
        rule.waitForIdle()
        assertEquals(b64, lastCopied)
    }

    @Test
    fun copyMenu_hidesTextAndBase64Items_whenAbsent() {
        // V19 T5:文本缺失 + Base64 缺失(旧记录)时「复制文本/复制 Base64」项隐藏
        // (不留空白项),Hex 恒在。
        val full = "ab".repeat(5_000)
        setSection(full, text = null)
        rule.onNodeWithContentDescription(str(R.string.section_copy)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.section_copy_text)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.section_copy_base64)).assertCountEquals(0)
        rule.onNodeWithText(str(R.string.section_copy_hex)).assertIsDisplayed()
    }

    @Test
    fun segmentedButton_threeStates_switchRendersSelectedOnly() {
        // ⑨V19 T5 三态:默认 TEXT(文本内容在树);切 Base64 → 仅 b64 内容;
        // 切 Hex → 仅 hex 内容;非选中表示内容不在树。
        val hex = "cd".repeat(20)
        val b64 = "YWJhYmFiYWJhYmFiYWJhYmFiYWI="
        val text = "abababababababababababababababababababab"
        setSection(hex = hex, text = text, base64 = b64)
        rule.onNodeWithText(text, substring = false).assertIsDisplayed()
        rule.onAllNodesWithText(hex, substring = false).assertCountEquals(0)
        rule.onAllNodesWithText(b64, substring = false).assertCountEquals(0)

        rule.onNodeWithText(str(R.string.section_repr_base64)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(b64, substring = false).assertCountEquals(1)
        rule.onAllNodesWithText(text, substring = false).assertCountEquals(0)

        rule.onNodeWithText(str(R.string.section_repr_hex)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(hex, substring = false).assertCountEquals(1)
        rule.onAllNodesWithText(b64, substring = false).assertCountEquals(0)
    }

    @Test
    fun segmentedButton_oldRecord_hidesBase64Segment() {
        // ⑨V19 T5 旧记录(base64=null)降级双态:分段行仅文本/Hex,复制菜单无 Base64。
        setSection(hex = "ab".repeat(20), text = null)
        rule.onAllNodesWithText(str(R.string.section_repr_base64)).assertCountEquals(0)
        rule.onAllNodesWithText(str(R.string.section_repr_text)).assertCountEquals(0)
        rule.onNodeWithText(str(R.string.section_repr_hex)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.section_copy)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(str(R.string.section_copy_base64)).assertCountEquals(0)
        rule.onNodeWithText(str(R.string.section_copy_hex)).assertIsDisplayed()
    }

    @Test
    fun oversizedBytes_base64AboveCap_keepsChunkedTier() {
        // ⑨V19 T5 Tier B Base64 分块:100K 字节(hex 200K 字符 / b64 ≈ 133K 字符
        // > SELECTABLE_CAP_CHARS)→ 切 Base64 + 展开 → 分块惰性组合,无整段单 Text。
        val hex = "ab".repeat(100_000)
        val bytes = ByteArray(100_000) { 0xAB.toByte() }
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        setSection(hex = hex, text = null, base64 = b64)
        rule.onNodeWithText(str(R.string.section_repr_base64)).performClick()
        rule.waitForIdle()
        // 「展开更多」并入预览节点,substring 命中唯一含它的节点;点击即展开。
        rule.onNodeWithText(str(R.string.record_see_more), substring = true).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(b64, substring = false).assertCountEquals(0)
        val chunks = ChunkedText(b64)
        rule.onAllNodesWithText(chunks[0], substring = false).assertCountEquals(1)
        // 「收起」在 Tier B 内容末尾,先滚动至可见再断言。
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(R.string.section_collapse)))
        rule.onNodeWithText(str(R.string.section_collapse)).assertIsDisplayed()
    }

    @Test
    fun oversizedHex_aboveSelectableCap_keepsChunkedTier() {
        // ⑦>128K 字符(Tier B)展开 → 分块惰性组合(块 0 在树),无整段单 Text。
        val full = "ab".repeat(70_000) // 140K 字符 > SELECTABLE_CAP_CHARS
        setSection(full, text = null, initiallyExpanded = true)
        rule.onAllNodesWithText(full, substring = false).assertCountEquals(0)
        rule.onAllNodesWithText("ab".repeat(2048), substring = false).assertCountEquals(1)
        // 「收起」在 Tier B 内容末尾,先滚动至可见再断言。
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(R.string.section_collapse)))
        rule.onNodeWithText(str(R.string.section_collapse)).assertIsDisplayed()
    }

    @Test
    fun oversizedHex_expandsLazilyChunkByChunk() {
        // ⑧300K hex 惰性滚动回归(展开态):末块未滚动不在树,滚动后组合成功。
        val full = "ab".repeat(75_000) + "cd".repeat(75_000)
        setSection(full, text = null, initiallyExpanded = true)
        val chunks = ChunkedText(full)
        val lastChunk = chunks[chunks.size - 1]
        rule.onAllNodes(hasText(lastChunk, substring = false)).assertCountEquals(0)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastChunk, substring = false))
        rule.onNode(hasText(lastChunk, substring = false)).assertIsDisplayed()
    }

    @Test
    fun oversizedText_expandsLazilyChunkByChunk() {
        // ⑧300K text(hex 600K)惰性滚动回归(展开态)。
        val text = "a".repeat(300_000)
        val hex = "ab".repeat(150_000)
        setSection(hex = hex, text = text, initiallyExpanded = true)
        val textChunks = ChunkedText(text)
        val lastTextChunk = textChunks[textChunks.size - 1]
        rule.onAllNodes(hasText(lastTextChunk, substring = false)).assertCountEquals(0)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastTextChunk, substring = false))
        rule.onNode(hasText(lastTextChunk, substring = false)).assertIsDisplayed()
    }
}