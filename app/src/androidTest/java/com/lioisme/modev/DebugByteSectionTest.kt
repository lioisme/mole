package com.lioisme.modev

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.ui.logs.byteSectionItems
import com.lioisme.modev.ui.logs.defaultRepresentation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugByteSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setSection(
        hex: String,
        text: String? = null,
        initiallyExpanded: Boolean = false,
    ) {
        val v = BytesView(hex = hex, text = text, totalLength = hex.length / 2, truncated = false)
        rule.setContent {
            MaterialTheme {
                var expanded by remember { mutableStateOf(initiallyExpanded) }
                LazyColumn(Modifier.fillMaxSize()) {
                    byteSectionItems(
                        sectionKey = "test",
                        titleRes = com.lioisme.modev.R.string.section_key,
                        view = v,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        copyFull = { _, _ -> },
                        representation = v.defaultRepresentation(),
                        onRepresentationChange = {},
                    )
                }
            }
        }
    }

    @Test
    fun dumpCollapsed() {
        val full = "ab".repeat(5_000)
        setSection(full, text = null)
        rule.waitForIdle()
        val tree = rule.onRoot().printToString()
        android.util.Log.i("BS-DBG", "COLLAPSED TREE:\n" + tree)
    }

    @Test
    fun dumpExpanded() {
        val full = "ab".repeat(5_000)
        setSection(full, text = null, initiallyExpanded = true)
        rule.waitForIdle()
        val tree = rule.onRoot().printToString()
        android.util.Log.i("BS-DBG", "EXPANDED TREE:\n" + tree)
    }
}
