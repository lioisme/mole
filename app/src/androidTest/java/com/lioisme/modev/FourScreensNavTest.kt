package com.lioisme.modev

import android.view.KeyEvent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.ui.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 四界面导航验收测试(T4,2026-08-16 V3;2026-08-17 V7 五需求同步):
 * R1(信息架构:应用列表→算法列表→算法详情 + 捕获设置 + 全局设置,往返可达)、
 * R2(应用列表/算法列表 AppBar 内仅一个 SearchBar)、
 * R3(SearchBar 内仅一个筛选按钮,无返回/清除)、
 * V7 变更:应用行无运行按钮(根页 app-launch=0)、算法列表捕获设置入口
 * 位于筛选菜单内、详情页顶栏更多菜单承载导出(仅断言菜单项存在,不触发落盘)。
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
        // V7:根页应用行无运行按钮(启动入口仅保留于算法列表页头部)。
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)

        // 筛选菜单:含「仅作用域 / 系统应用 / 设置」三项。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.apps_only_scoped)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.apps_system)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()

        // 仅作用域筛选生效后菜单关闭(过滤语义无异常即可)。
        rule.onNodeWithText(str(R.string.apps_only_scoped)).performClick()
        rule.waitForIdle()

        // 设置入口:再次打开菜单 → 设置 → 设置页(唯一入口的端到端可达性)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.settings_title)).performClick()
        rule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()
        pressBack()
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
    }

    @Test
    fun appRowOpensAlgorithmList_andCaptureSettingsEntryNavigates() {
        rule.waitForIdle()
        assumeTrue(rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty())

        rule.onAllNodesWithTag("app-row").onFirst().performClick()
        rule.waitForIdle()

        // 算法列表标志:AppBar 仅 SearchBar;返回按钮置于 SearchBar 内部左侧
        // (LIDE 同款,二级页有返回语义)→ 全页恰 1 个返回按钮;头部保留启动按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)

        // 筛选菜单:全部算法类别 + 底部「捕获设置」入口(V7 移入菜单)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.kind_cipher)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.kind_key_generation)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.detail_tab_settings)).assertIsDisplayed()
        pressBack()

        // 捕获设置独立成页:筛选菜单 → 捕获设置(开关 + 危险操作,无搜索/筛选)。
        rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
        rule.onNodeWithText(str(R.string.detail_tab_settings)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.switch_cipher)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_log_stack)).assertExists()
        // 捕获设置页:ModevTopBar 返回 1 个、无筛选按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)

        pressBack()
        // 返回记录列表页后仍是唯一筛选按钮,头部启动按钮仍在。
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)

        pressBack()
        // 返回根页后仍是唯一筛选按钮,且无运行按钮。
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

        // 算法详情页标志:更多按钮 + 返回;顶栏无复制/分享直挂按钮(V7 收敛)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).assertIsDisplayed()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).assertIsDisplayed()

        // 更多菜单唯一功能 = 导出 JSON(仅断言存在,不触发 SAF 落盘)。
        rule.onNodeWithContentDescription(str(R.string.record_more)).performClick()
        rule.onNodeWithText(str(R.string.record_export)).assertIsDisplayed()
        pressBack()

        pressBack()
        // 返回记录列表页:头部启动按钮仍在(V7 保留)。
        rule.onAllNodesWithTag("app-launch").assertCountEquals(1)
        pressBack()
        // 返回根页:唯一筛选按钮、无运行按钮。
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(1)
        rule.onAllNodesWithTag("app-launch").assertCountEquals(0)
    }
}