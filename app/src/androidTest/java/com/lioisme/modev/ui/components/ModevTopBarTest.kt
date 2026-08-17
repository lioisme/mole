package com.lioisme.modev.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 定向测试(ModevTopBar,2026-08-15 UI 重构 Phase 2):标题 / 返回(语言无关定位)/ actions 槽;
 * 2026-08-16 V3:showBack 参数移除,顶部栏恒显示返回(四界面导航的统一物理约束)。
 * 2026-08-17 V7:标题恒单行省略(固定高度守卫)——长标题单行高度内渲染。
 */
@RunWith(AndroidJUnit4::class)
class ModevTopBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersTitle_andBackInvokesCallback_andActionsRendered() {
        val backCd = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.cd_back)
        var backClicked = false
        rule.setContent {
            MaterialTheme {
                ModevTopBar(
                    title = "Detail Title",
                    onBack = { backClicked = true },
                    actions = {
                        Icon(Icons.Default.Star, contentDescription = "action-icon")
                    },
                )
            }
        }
        rule.onNodeWithText("Detail Title").assertIsDisplayed()
        rule.onNodeWithContentDescription(backCd).assertIsDisplayed()
        rule.onNodeWithContentDescription("action-icon").assertIsDisplayed()
        rule.onNodeWithContentDescription(backCd).performClick()
        rule.runOnIdle { assertTrue(backClicked) }
    }

    @Test
    fun longTitleStaysSingleLineWithinFixedHeight() {
        val longTitle =
            "AES/GCM/NoPadding with a very long provider configuration suffix that must never wrap"
        rule.setContent {
            MaterialTheme {
                ModevTopBar(title = longTitle, onBack = {})
            }
        }
        // 单行省略守卫:标题节点边界高度 ≤ 32dp(两行 ≈ 56dp 必失败),AppBar 不被撑高。
        val height = rule.onNodeWithText(longTitle).fetchSemanticsNode().boundsInRoot.height
        assertTrue("long title must stay single line (height=$height)", height <= 32f)
    }
}
