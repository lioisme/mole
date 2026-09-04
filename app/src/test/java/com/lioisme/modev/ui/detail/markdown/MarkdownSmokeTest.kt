package com.lioisme.modev.ui.detail.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V26 markdown 渲染库运行时冒烟(Robolectric):
 * 验证 mikepenz multiplatform-markdown-renderer-m3 与本项目
 * Kotlin 2.2.20 / Compose BOM 2025.06.01 / material3 1.4.0 组合的
 * 编译期与组合期兼容性。
 *
 * 已知工具链坑(沉淀 AGENTS.md V26):
 * 1. 库默认在 Dispatchers.Default 异步解析(Load 态渲染空 Box),
 *    Robolectric 下与 waitForIdle 存在竞态——测试与详情页一律传
 *    immediate = true(文档经显示上限钳制,阻塞解析有界);
 * 2. 断言用 substring 形态(标题节点语义文本形态随库版本演进)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renders_heading_without_crash() {
        compose.setContent {
            MaterialTheme {
                val state = rememberMarkdownState(content = "# hello modev", immediate = true)
                Markdown(markdownState = state)
            }
        }
        assertTrue(
            compose.onAllNodesWithText("hello modev", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun renders_code_fence_without_crash() {
        compose.setContent {
            MaterialTheme {
                val state = rememberMarkdownState(content = "```text\nabc\n```", immediate = true)
                Markdown(markdownState = state)
            }
        }
        assertTrue(
            compose.onAllNodesWithText("abc", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }
}
