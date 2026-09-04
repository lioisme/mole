package com.lioisme.modev.ui.detail.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.ui.detail.byteSectionDoc
import com.lioisme.modev.ui.detail.markdownSectionItems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 本地复现宿主(CodeBlockCard 单围栏渲染诊断)。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarkdownCardRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private fun view(hex: String) =
        BytesView(hex = hex, base64 = null, text = null, totalLength = hex.length / 2, truncated = false)

    @Test
    fun hexOnly_singleFence_rendersCodeContent() {
        val hex = "ab".repeat(20)
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    // 文档装配须在组合作用域(@Composable 构建器)
                    val doc = byteSectionDoc("KEY", view(hex))
                    LazyColumn(Modifier.fillMaxSize()) {
                        markdownSectionItems(sectionKey = "test", doc = doc, actions = null)
                    }
                }
            }
        }
        val nodes = compose.onAllNodesWithText(hex).fetchSemanticsNodes()
        if (nodes.isEmpty()) {
            println("DIAG_TREE_START")
            println(compose.onRoot().printToString())
            println("DIAG_TREE_END")
        }
        assertTrue("hex 内容应在语义树(count=${nodes.size})", nodes.isNotEmpty())
    }
}
