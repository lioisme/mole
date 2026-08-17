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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.ChunkedText
import com.lioisme.modev.core.util.LineEstimate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 定向测试(ByteSection,2026-08-18 V7 惰性分块契约;2026-08-17 V11 折叠语义;
 * 2026-08-17 V12 动效 + 复制):
 * - 小数据(未超行数阈值)默认展开:全量展示 + 「收起」按钮;
 * - 大数据(超行数阈值)默认折叠:预览截断至阈值行数 + 「展开」按钮;
 * - 展开态按 [ChunkedText] 惰性组合(hex 与 text 双路,仅可视块在树中);
 * - 折叠态 text 截断预览,全量文本不组合;
 * - 展开/收起为 220ms 互斥过渡(半程新旧并存,无闪断;全程后旧内容退出完毕);
 * - 折叠预览 / 内联全量 / 分块逐块均包 SelectionContainer(可长按选择复制)。
 * 实机 100K 字符单 Text 展开 26s ANR 的回归防线。
 */
@RunWith(AndroidJUnit4::class)
class ByteSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun str(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun view(hex: String, text: String? = "plain text") = BytesView(
        hex = hex,
        text = text,
        totalLength = hex.length / 2,
        truncated = false,
    )

    /** 组合期实际指标(与生产同源),供断言推算预览截断长度。 */
    private var lastMetrics: FoldMetrics = FoldMetrics(charsPerLineHex = 45, charsPerLineText = 46)

    /** 选中态契约(行为级):长按 SelectionContainer 内文本须弹出复制工具栏(Copy 项)。
 *  AndroidTextToolbar 是 compose 覆盖层(CanvasLayer),查 compose 语义树;无障碍窗口遍历兜底。 */
    private fun assertCopyToolbar() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = System.currentTimeMillis() + 3_000
        var found = false
        fun findInNode(n: android.view.accessibility.AccessibilityNodeInfo, text: String): Boolean {
            if (n.text?.toString() == text || n.contentDescription?.toString() == text) return true
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                if (findInNode(c, text)) return true
            }
            return false
        }
        while (System.currentTimeMillis() < deadline && !found) {
            found = rule.onAllNodes(hasText("Copy", substring = false)).fetchSemanticsNodes().isNotEmpty()
            if (!found) {
                ui.windows.forEach { w ->
                    val root = w.root
                    if (!found && root != null) found = findInNode(root, "Copy")
                }
            }
            if (!found) {
                val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                found = device.findObject(androidx.test.uiautomator.By.text("Copy")) != null
            }
            if (!found) Thread.sleep(100)
        }
        assertTrue("长按须弹出复制工具栏(3s 内)", found)
    }

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
        folded: Boolean,
        onToggleFolded: () -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                val metrics = rememberFoldMetrics()
                lastMetrics = metrics
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = R.string.section_key,
                        view = view(hex, text),
                        metrics = metrics,
                        folded = folded,
                        onToggleFolded = onToggleFolded,
                    )
                }
            }
        }
    }

    @Test
    fun smallDataDefaultsExpanded_showsFullHexAndCollapseButton() {
        val hex = "abcd"
        setSection(hex = hex, text = null, folded = false)
        // 未折叠:全量 hex 单节点可见,按钮为「收起」
        rule.onNodeWithText(hex).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.section_collapse)).assertIsDisplayed()
    }

    @Test
    fun largeDataDefaultsCollapsed_showsThresholdPreviewAndExpandButton() {
        val full = "ab".repeat(5_000) // 10K 字符 > 行数阈值
        setSection(full, text = null, folded = true)
        // 折叠:全量 hex 永不组合
        rule.onAllNodesWithText(full).assertCountEquals(0)
        // 预览截断至阈值行数(非 4096 固定上限)
        val previewChars = LineEstimate.collapsedPreviewChars(full.length, lastMetrics.charsPerLineHex)
        rule.onNodeWithText(full.take(previewChars) + "…(+${5_000 - previewChars / 2} bytes)")
            .assertIsDisplayed()
        rule.onNodeWithText(str(R.string.section_expand)).assertIsDisplayed()
    }

    @Test
    fun toggleFlipsStateAndButtonLabel() {
        rule.setContent {
            MaterialTheme {
                var folded by remember { mutableStateOf(true) }
                val metrics = rememberFoldMetrics()
                lastMetrics = metrics
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = R.string.section_key,
                        view = view("ab".repeat(5_000), text = null),
                        metrics = metrics,
                        folded = folded,
                        onToggleFolded = { folded = !folded },
                    )
                }
            }
        }
        // 默认折叠 → 「展开」;点击后展开 → 首块组合 + 「收起」
        rule.onNodeWithText(str(R.string.section_expand)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("ab".repeat(5_000).take(ChunkedText.DEFAULT_CHUNK_CHARS)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.section_collapse)).assertIsDisplayed()
        // 再点收起 → 回折叠预览 + 「展开」
        rule.onNodeWithText(str(R.string.section_collapse)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.section_expand)).assertIsDisplayed()
    }

    @Test
    fun collapsedTruncatesTextPreview_neverComposesFullText() {
        val text = "T".repeat(600) // 600 字符 > 文本阈值行数(hex 1200 亦超阈值)
        val full = "ab".repeat(600)
        setSection(hex = full, text = text, folded = true)
        val textPreviewChars = LineEstimate.collapsedPreviewChars(text.length, lastMetrics.charsPerLineText)
        rule.onNodeWithText(text.take(textPreviewChars) + "…").assertIsDisplayed()
        rule.onAllNodesWithText(text).assertCountEquals(0)
        rule.onNodeWithText(str(R.string.section_expand)).assertIsDisplayed()
    }

    @Test
    fun oversizedHexExpandsLazilyChunkByChunk() {
        // 300K 字符,前 150K 为 ab 段、后 150K 为 cd 段
        val full = "ab".repeat(75_000) + "cd".repeat(75_000)
        setSection(full, text = null, folded = false)
        val chunks = ChunkedText(full)
        val lastChunk = chunks[chunks.size - 1]
        // 惰性:末块未滚动时不在树中(精确匹配,防前缀块子串误命中)
        rule.onAllNodes(hasText(lastChunk, substring = false)).assertCountEquals(0)
        // 滚动到末块后其组合成功(按需创建,无巨型单 Text)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastChunk, substring = false))
        rule.onNode(hasText(lastChunk, substring = false)).assertIsDisplayed()
    }

    @Test
    fun oversizedTextExpandsLazilyChunkByChunk() {
        // 300K 可打印文本(hex 600K),展开后文本亦分块惰性组合
        val text = "a".repeat(300_000)
        val hex = "ab".repeat(150_000)
        setSection(hex = hex, text = text, folded = false)
        val textChunks = ChunkedText(text)
        val lastTextChunk = textChunks[textChunks.size - 1]
        rule.onAllNodes(hasText(lastTextChunk, substring = false)).assertCountEquals(0)
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(lastTextChunk, substring = false))
        rule.onNode(hasText(lastTextChunk, substring = false)).assertIsDisplayed()
    }

    @Test
    fun expandedNeverComposesFullHexAsSingleNode() {
        val full = "ab".repeat(5_000) // 10K 字符 → 3 块(4096/4096/1808)
        setSection(full, text = null, folded = false)
        // 展开态首块组合且可见,全量 hex 永远不是单一节点(分块契约核心)
        rule.onNodeWithText(full.take(ChunkedText.DEFAULT_CHUNK_CHARS)).assertIsDisplayed()
        rule.onAllNodesWithText(full).assertCountEquals(0)
    }

    @Test
    fun toggle_animates_mutualTransitionAcrossHalfDuration() {
        val full = "ab".repeat(5_000)
        rule.setContent {
            MaterialTheme {
                var folded by remember { mutableStateOf(true) }
                val metrics = rememberFoldMetrics()
                lastMetrics = metrics
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = R.string.section_key,
                        view = view(full, text = null),
                        metrics = metrics,
                        folded = folded,
                        onToggleFolded = { folded = !folded },
                    )
                }
            }
        }
        rule.waitForIdle()
        // 组合期实测指标推算预览串(与生产同源:hex 截断 + 省略后缀)
        val previewChars = LineEstimate.collapsedPreviewChars(full.length, lastMetrics.charsPerLineHex)
        val preview = full.take(previewChars) + "…(+${5_000 - previewChars / 2} bytes)"
        rule.mainClock.autoAdvance = false
        // 展开:按钮文案即时翻转(状态单一事实源),内容走 220ms 互斥过渡
        rule.onNodeWithText(str(R.string.section_expand)).performClick()
        rule.mainClock.advanceTimeBy(16) // 首帧,动画启动
        rule.onNodeWithText(str(R.string.section_collapse)).assertIsDisplayed()
        // 半程(110ms):退出中预览仍在树、进入中首块已组合——互斥过渡无闪断
        rule.mainClock.advanceTimeBy(94)
        rule.onNodeWithText(preview).assertExists()
        rule.onNodeWithText(full.take(ChunkedText.DEFAULT_CHUNK_CHARS)).assertExists()
        // 全程(220ms)后:预览退出完毕,仅展开内容
        rule.mainClock.advanceTimeBy(200)
        rule.onAllNodesWithText(preview).assertCountEquals(0)
        rule.onNodeWithText(full.take(ChunkedText.DEFAULT_CHUNK_CHARS)).assertExists()
        // 反向:收起后头部预览经 220ms 过渡回归;块内容由 animateItem 消失
        // (spring 时长无承诺,不强断中途树状态)——半程断言互斥过渡仍在进行。
        rule.onNodeWithText(str(R.string.section_collapse)).performClick()
        rule.mainClock.advanceTimeBy(16)
        rule.mainClock.advanceTimeBy(94)
        rule.onNodeWithText(preview).assertExists()
        rule.mainClock.advanceTimeBy(200)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onAllNodesWithText(full.take(ChunkedText.DEFAULT_CHUNK_CHARS)).assertCountEquals(0)
        rule.onNodeWithText(preview).assertIsDisplayed()
    }

    @Test
    fun expandedChunks_longPressActivatesSelection() {
        // 块间互异:块1=ab×2048、块2=cd×2048、块3=ef×904,精确匹配唯一节点
        val full = "ab".repeat(2048) + "cd".repeat(2048) + "ef".repeat(904)
        setSection(full, text = null, folded = false)
        longPressOn("ab".repeat(2048))
        assertCopyToolbar()
    }

    @Test
    fun foldedPreview_longPressActivatesSelection() {
        setSection(hex = "abcd", text = "plain text", folded = true)
        longPressOn("abcd")
        assertCopyToolbar()
    }

    @Test
    fun inlineExpandedSmallData_longPressActivatesSelection() {
        setSection(hex = "abcd", text = "plain text", folded = false)
        longPressOn("plain text")
        assertCopyToolbar()
    }
}