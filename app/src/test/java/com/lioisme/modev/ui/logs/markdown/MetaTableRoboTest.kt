package com.lioisme.modev.ui.logs.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.lioisme.modev.ui.logs.metaSectionDoc
import com.lioisme.modev.ui.logs.metaTableItems
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 本地实证:元数据 GFM 表的单元格语义(标签/值独立 Text?)。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetaTableRoboTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun metaTable_cellsRenderAsSeparateTexts() {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val table = metaSectionDoc("Metadata", listOf("Context" to "#0", "Thread" to "main"))
                    LazyColumn(Modifier.fillMaxSize()) {
                        metaTableItems("meta", table)
                    }
                }
            }
        }
        val ctxCount = compose.onAllNodesWithText("Context").fetchSemanticsNodes().size
        val valCount = compose.onAllNodesWithText("#0").fetchSemanticsNodes().size
        if (ctxCount == 0 || valCount == 0) {
            println("METATABLE_TREE_START")
            println(compose.onRoot().printToString())
            println("METATABLE_TREE_END")
        }
        assertTrue("label Context 应在树(count=$ctxCount)", ctxCount > 0)
        assertTrue("value #0 应在树(count=$valCount)", valCount > 0)
    }
}
