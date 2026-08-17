package com.lioisme.modev

import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.ui.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 界面导航验收测试(T4,2026-08-16 V3;2026-08-17 V7 五需求同步;V10 设置分级同步):
 * R1(信息架构:应用列表→应用记录→记录详情;设置页→二级设置页:捕获设置/数据管理/
 * 服务与诊断,往返可达)、
 * R2(应用列表/应用记录 AppBar 内仅一个 SearchBar)、
 * R3(SearchBar 内仅一个筛选按钮,无返回/清除)、
 * V7 变更:应用行无运行按钮(根页 app-launch=0)、详情页顶栏更多菜单承载导出,
 * V10 变更:捕获设置全局化(入口移至设置页,记录页筛选菜单仅算法类别)、
 * 记录页「更多」菜单承载应用级危险操作(移出作用域/清空该应用日志)、
 * 置顶开关入筛选菜单、设置页含「数据管理/服务与诊断」入口。
 * 数据依赖断言(应用行/记录行)以 assumeTrue 防御,空机不红。
 */
@RunWith(AndroidJUnit4::class)
class FourScreensNavTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    /** 零依赖返回键(avoid espresso-core,与现有 androidTest 依赖集一致)。 */
    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test
    fun rootHasOnlyFilterButton_andFilterMenuNavigatesToSettings_andBack() {
        rule.waitForIdle()
        // R2/R3:根页 AppBar 即折叠 SearchBar,其内仅一个筛选按钮;根页无返回按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(0)
        // V7:根页应用行无运行按钮(启动入口仅保留于应用记录页头部)。
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)

        // 筛选菜单:含「仅作用域 / 系统应用 / 置顶 / 设置」四项。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.apps_only_scoped)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.apps_system)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_pin_scoped)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()

        // 仅作用域筛选生效后菜单关闭(过滤语义无异常即可)。
        rule.onNodeWithText(str(R.string.apps_only_scoped)).performClick()
        rule.waitForIdle()

        // 设置入口:再次打开菜单 → 设置 → 设置页(唯一入口的端到端可达性)。
        // 应用列表可能含「设置」同名行,点击限定在弹出菜单内(isPopup)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onAllNodes(hasText(str(R.string.settings_title)) and hasAnyAncestor(isPopup())).onFirst().performClick()
        rule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()
        pressBack()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
    }

    @Test
    fun appRowOpensAppRecords_andDangerMenu_andGlobalCaptureSettingsNavigates() {
        rule.waitForIdle()
        assumeTrue(rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty())

        // V7:头部「启动」按钮仅对可启动应用渲染(launchable==true && enabled)。
        // 逐个进入应用记录页寻找带启动按钮的应用;可见行全部不可启动时上滑列表
        // (模拟器首屏常为无启动入口的系统组件)继续寻找;仍找不到则跳过本用例,
        // 保证启动按钮/危险操作/全局捕获设置断言只在有效语境下执行。
        var openedLaunchable = false
        var swipes = 0
        while (!openedLaunchable && swipes < 10) {
            // 每轮重取行数:惰性列表滚动回收可能改变可见行数,防索引越界(2026-08-17 V12 防御)
            var i = 0
            while (i < rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().size) {
                rule.onAllNodesWithTag("app-row")[i].performClick()
                rule.waitForIdle()
                // 应用记录页标志:AppBar 仅 SearchBar;返回按钮置于 SearchBar 内部左侧
                // (LIDE 同款,二级页有返回语义)→ 全页恰 1 个返回按钮;筛选按钮 1 个。
                rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
                rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
                if (rule.onAllNodesWithTag("app-launch").fetchSemanticsNodes().isNotEmpty()) {
                    openedLaunchable = true
                    break
                }
                // 该应用无启动入口,返回后尝试下一个。
                pressBack()
                rule.waitForIdle()
                i++
            }
            if (!openedLaunchable && swipes < 9) {
                rule.onAllNodes(hasScrollAction()).onFirst()
                    .performTouchInput { swipeUp() }
                rule.waitForIdle()
            }
            swipes++
        }
        assumeTrue(openedLaunchable)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)

        // 更多菜单:应用级危险操作(移出作用域仅对已作用域应用出现;清空该应用日志恒在),
        // 仅断言存在不触发。
        rule.onNodeWithContentDescription(str(R.string.record_more)).performClick()
        rule.onNodeWithText(str(R.string.detail_clear_logs)).assertIsDisplayed()
        if (rule.onAllNodesWithText(str(R.string.detail_remove_scope)).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText(str(R.string.detail_remove_scope)).assertIsDisplayed()
        }
        pressBack()

        // 筛选菜单:全部算法类别(V10 起无「捕获设置」入口,配置已全局化)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.kind_cipher)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.kind_key_generation)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.capture_settings_title)).assertDoesNotExist()
        pressBack()
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)

        // 回根页 → 筛选菜单 → 设置页(捕获设置全局入口)。
        pressBack()
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onAllNodes(hasText(str(R.string.settings_title)) and hasAnyAncestor(isPopup())).onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.capture_settings_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.data_settings_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.diagnostics_settings_title)).assertIsDisplayed()

        // 捕获设置全局页:开关区 + 全局提示,无筛选按钮。
        rule.onNodeWithText(str(R.string.capture_settings_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.switch_cipher)).assertIsDisplayed()
        // 记录选项区在 LazyColumn 折叠区,滚动至可见后断言。
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(str(R.string.switch_log_stack)))
        rule.onNodeWithText(str(R.string.switch_log_stack)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)
        pressBack()

        // 返回设置页 → 数据管理页:总记录行 + 清空全部日志(危险块),不触发。
        rule.onNodeWithText(str(R.string.data_settings_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.settings_total_records)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_clear_all_logs)).assertIsDisplayed()
        pressBack()

        // 返回设置页 → 服务与诊断页:端口行 + 管线统计折叠头。
        rule.onNodeWithText(str(R.string.diagnostics_settings_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.settings_port)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_stats_section)).assertIsDisplayed()
        pressBack()

        // 返回根页:唯一筛选按钮、无运行按钮。
        pressBack()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)
    }

    @Test
    fun recordRowOpensRecordDetail_andMoreMenuHasSingleExport_andBackReturns() {
        rule.waitForIdle()
        assumeTrue(rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty())

        rule.onAllNodesWithTag("app-row").onFirst().performClick()
        rule.waitForIdle()
        assumeTrue(rule.onAllNodesWithTag("record-row").fetchSemanticsNodes().isNotEmpty())

        rule.onAllNodesWithTag("record-row").onFirst().performClick()
        rule.waitForIdle()

        // 记录详情页标志:更多按钮 + 返回;顶栏无复制/分享直挂按钮(V7 收敛)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).assertIsDisplayed()

        // 更多菜单唯一功能 = 导出 JSON(仅断言存在,不触发 SAF 落盘)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).performClick()
        rule.onNodeWithText(str(R.string.record_export)).assertIsDisplayed()
        pressBack()

        pressBack()
        // 返回记录页:头部启动按钮仍在(V7 保留;仅对可启动应用渲染)。
        if (rule.onAllNodesWithTag("app-launch").fetchSemanticsNodes().isNotEmpty()) {
            rule.onAllNodesWithTag("app-launch").assertCountEquals(1)
        }
        pressBack()
        // 返回根页:唯一筛选按钮、无运行按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)
    }
}