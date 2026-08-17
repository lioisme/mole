package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 定向测试(EmptyState,2026-08-15 UI 重构 Phase 2):规范空态图标 + 文案渲染。 */
@RunWith(AndroidJUnit4::class)
class EmptyStateTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersMessage() {
        rule.setContent {
            MaterialTheme {
                EmptyState(icon = Icons.Default.Info, message = "Nothing here yet")
            }
        }
        rule.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }
}
