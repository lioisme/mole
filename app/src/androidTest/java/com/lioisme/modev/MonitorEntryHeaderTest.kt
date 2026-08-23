package com.lioisme.modev

import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 应用监听入口页头部验收(V20:「查看记录」头部职责迁入):
 * - 头部 = AppHeaderRow(应用名 + 包名 + 双状态点 + 一键启动 + 更多菜单);
 * - 更多菜单 = 应用级危险操作(移出作用域/清空该应用记录,确认对话框不触发);
 * - 两记录入口(算法/数据库)与每类型计数副标题;
 * - 顶栏返回回根页。
 * 前置:开启「显示 Xposed 模块」使被测包行可见,@After 复位。
 */
@RunWith(AndroidJUnit4::class)
class MonitorEntryHeaderTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    private fun str(id: Int) = ctx.getString(id)

    @After
    fun restore() {
        AppListSettings.setShowModules(false, ctx)
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    /** 等待被测包行出现并进入应用监听入口页(与 MonitorListScreensTest 同款轮询)。 */
    private fun openMonitorEntry() {
        AppListSettings.setShowModules(true, ctx)
        rule.waitForIdle()
        val deadline = System.currentTimeMillis() + 10_000
        var rowFound = false
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
            if (scrollables.isNotEmpty()) {
                try {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(ownPackage))
                    rowFound = true
                    break
                } catch (_: AssertionError) {
                    // 目标行未加载/被模块显隐过滤,下一轮重试。
                }
            }
            Thread.sleep(100)
        }
        assertTrue("等待「显示 Xposed 模块」生效后被测包行应出现", rowFound)
        rule.onNodeWithText(ownPackage).performClick()
        rule.waitForIdle()
    }

    @Test
    fun entryHeader_hasLaunchButton_andDangerMenu_andTwoRecordEntries() {
        rule.waitForIdle()
        openMonitorEntry()

        // 一键启动(惰性解析异步出现 → 有界轮询)。
        val launchDeadline = System.currentTimeMillis() + 10_000
        var hasLaunch = false
        while (System.currentTimeMillis() < launchDeadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithTag("app-launch").fetchSemanticsNodes().isNotEmpty()) {
                hasLaunch = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("入口页头部应渲染一键启动按钮", hasLaunch)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)

        // 更多菜单:应用级危险操作(清空该应用记录恒在;移出作用域仅对已作用域应用)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).performClick()
        rule.onNodeWithText(str(R.string.detail_clear_records)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.record_more)).assertCountEquals(1)
        pressBack()
        rule.onNodeWithText(str(R.string.detail_clear_records)).assertDoesNotExist()

        // 两记录入口(单应用列表)+ 顶栏返回。
        rule.onNodeWithText(str(R.string.algorithm_records_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.database_records_title)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        pressBack()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
    }
}