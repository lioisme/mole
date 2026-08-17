package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 定向测试(TipIconButton,2026-08-15 UI 重构 Phase 2):单击回调 / 长按 tooltip / 禁用态。 */
@RunWith(AndroidJUnit4::class)
class TipIconButtonTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun click_invokesCallback() {
        var clicked = false
        rule.setContent {
            MaterialTheme {
                TipIconButton(tooltip = "tooltip", onClick = { clicked = true }) {
                    Icon(Icons.Default.Star, contentDescription = "star")
                }
            }
        }
        rule.onNodeWithContentDescription("star").performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun longPress_showsTooltip() {
        rule.setContent {
            MaterialTheme {
                TipIconButton(tooltip = "tooltip-content", onClick = {}) {
                    Icon(Icons.Default.Star, contentDescription = "star")
                }
            }
        }
        // 800ms 长按:超过 longPressTimeout,触发 tooltip 展示
        rule.onNodeWithContentDescription("star").performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("tooltip-content")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("tooltip-content").assertIsDisplayed()
    }

    @Test
    fun click_doesNotShowTooltip() {
        rule.setContent {
            MaterialTheme {
                TipIconButton(tooltip = "tooltip-content", onClick = {}) {
                    Icon(Icons.Default.Star, contentDescription = "star")
                }
            }
        }
        rule.onNodeWithContentDescription("star").performClick()
        rule.waitForIdle()
        assertFalse(rule.onAllNodes(hasText("tooltip-content")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun disabled_ignoresClick() {
        var clicked = false
        rule.setContent {
            MaterialTheme {
                TipIconButton(tooltip = "tooltip", onClick = { clicked = true }, enabled = false) {
                    Icon(Icons.Default.Star, contentDescription = "star")
                }
            }
        }
        rule.onNodeWithContentDescription("star").assertIsNotEnabled()
        rule.onNodeWithContentDescription("star").performClick()
        rule.runOnIdle { assertFalse(clicked) }
    }
}
