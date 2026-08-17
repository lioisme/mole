package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 定向测试(SettingsComponents,2026-08-15 UI 重构 Phase 2/5):分区 / 点击项 / 开关 / 只读行。 */
@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun section_rendersTitle() {
        rule.setContent {
            MaterialTheme { SettingsSection(title = "Section Title") {} }
        }
        rule.onNodeWithText("Section Title").assertIsDisplayed()
    }

    @Test
    fun item_clickInvokesCallback_andShowsSubtitle() {
        var clicked = false
        rule.setContent {
            MaterialTheme {
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "Item Title",
                    subtitle = "Item Subtitle",
                    onClick = { clicked = true },
                )
            }
        }
        rule.onNodeWithText("Item Title").assertIsDisplayed()
        rule.onNodeWithText("Item Subtitle").assertIsDisplayed()
        rule.onNodeWithText("Item Title").performClick()
        rule.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun toggleItem_clickFlipsState() {
        var checked by mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                SettingsToggleItem(
                    icon = Icons.Default.Settings,
                    title = "Toggle Title",
                    subtitle = "Toggle Subtitle",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }
        rule.onNodeWithText("Toggle Title").performClick()
        rule.runOnIdle { assertTrue(checked) }
        rule.onNodeWithText("Toggle Title").performClick()
        rule.runOnIdle { assertFalse(checked) }
    }

    @Test
    fun toggleItem_disabled_ignoresClick() {
        var changed = false
        rule.setContent {
            MaterialTheme {
                SettingsToggleItem(
                    icon = Icons.Default.Settings,
                    title = "Toggle Title",
                    checked = false,
                    enabled = false,
                    onCheckedChange = { changed = true },
                )
            }
        }
        rule.onNodeWithText("Toggle Title").assertIsNotEnabled()
        rule.onNodeWithText("Toggle Title").performClick()
        rule.runOnIdle { assertFalse(changed) }
    }

    @Test
    fun infoItem_rendersTitleAndValue() {
        rule.setContent {
            MaterialTheme {
                SettingsInfoItem(
                    icon = Icons.Default.Settings,
                    title = targetContext.getString(R.string.settings_display_section),
                    value = "Info Value",
                )
            }
        }
        rule.onNodeWithText(targetContext.getString(R.string.settings_display_section)).assertIsDisplayed()
        rule.onNodeWithText("Info Value").assertIsDisplayed()
    }

    @Test
    fun infoItem_longValue_doesNotSqueezeTitle() {
        // V11 回归:长 Value 曾按内容全宽测量,把标题挤压成逐字折行(「框架」竖排错位)。
        // 修复后 Value 有界测量(≤50% 行宽省略),标题恒保持单行(bodyLarge lineHeight=24dp)。
        val longValue = "V".repeat(200)
        rule.setContent {
            MaterialTheme {
                SettingsInfoItem(
                    icon = Icons.Default.Settings,
                    title = "框架",
                    value = longValue,
                )
            }
        }
        // 标题单行完整显示(折行则为 48dp,断言失败);长值以两行省略呈现
        rule.onNodeWithText("框架").assertHeightIsEqualTo(24.dp)
        rule.onNodeWithText(longValue).assertIsDisplayed()
    }
}
