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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * 应用监听入口页验收(V23 重构后结构):
 * - Hero 卡:作用域 Switch 行(app-scope-row);
 * - 功能入口卡:两记录入口(算法/数据库)+ 监听设置;
 * - 操作卡:一键启动(app-launch)+ 危险行「清空该应用记录」(不触发确认);
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
        AppListSettings.setShowModules(false)
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    /** 等待被测包行出现并进入应用监听入口页(与 MonitorListScreensTest 同款轮询)。 */
    private fun openMonitorEntry() {
        AppListSettings.setShowModules(true)
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

        // Hero 卡(V23):作用域 Switch 行。
        rule.onNodeWithTag("app-scope-row").assertIsDisplayed()

        // 两记录入口(单应用语境)+ 顶栏返回。
        rule.onNodeWithText(str(R.string.algorithm_records_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.database_records_title)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)

        // V37 T3.2:启动/清空 = Appbar actions(图标;testTag 锚定);
        // 启动按钮惰性解析异步出现 → 有界轮询;清空仅断言存在不触发确认。
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
        assertTrue("Appbar 应渲染一键启动动作", hasLaunch)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)
        rule.onAllNodesWithTag("app-clear-records").assertCountEquals(1)

        pressBack()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
    }
}