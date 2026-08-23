package com.lioisme.modev

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.R
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据库监听设置页验收(V21 T3,自合并页拆分还原):
 * - 设置页「功能」区入口「数据库监听」,副标题 = 数据库启用词(随开关实时变化,
 *   与 SettingsRepository 单一事实源同源);
 * - 页面两分区(监听类别 = 总开关 + 5 类别 / 结果闸门)齐全;
 * - 结果闸门 sheet 编辑(行数上限)回写并展示;
 * - 返回栈:设置页 → 数据库监听 → 设置页。
 * 开关态持久化,@Before 快照 @After 还原防跨用例污染。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSettingsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val settings: SettingsRepository
        get() = (ctx.applicationContext as com.lioisme.modev.ModevApp).container.settings

    private var initialSwitch: HookSwitch? = null

    private fun str(id: Int) = ctx.getString(id)

    @Before
    fun snapshot() {
        initialSwitch = settings.switch
    }

    @After
    fun restore() {
        initialSwitch?.let { settings.set(it) }
    }

    /** 有界轮询等待文本出现(异步流/重组)。 */
    private fun waitForVisible(text: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(100)
        }
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun openSettingsPage() {
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_settings)).performClick()
        rule.waitForIdle()
        waitForVisible(str(R.string.database_monitor_title))
    }

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(text))
    }

    @Test
    fun entrySubtitleFollowsSwitch_andPageHasTwoSections() {
        // 设置页:入口「数据库监听」+ 独立入口「算法监听」。
        openSettingsPage()
        waitForVisible(
            str(
                if (settings.switch.dbListen) R.string.settings_capability_enabled else R.string.settings_capability_disabled
            ),
        )
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).assertIsDisplayed()
        // 数据库入口行在小屏(360x640dp)落在折叠区,滚动至可见后断言。
        scrollTo(str(R.string.database_monitor_title))
        rule.onNodeWithText(str(R.string.database_monitor_title)).assertIsDisplayed()

        // 进入数据库页:①监听类别(总开关 + 5 类别)。
        rule.onNodeWithText(str(R.string.database_monitor_title)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)
        waitForVisible(str(R.string.database_monitor_master))
        rule.onNodeWithText(str(R.string.switch_db_open)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_db_query)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_db_dml)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_db_exec)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_db_transaction)).assertIsDisplayed()
        // ②结果闸门。
        scrollTo(str(R.string.db_max_result_rows))
        rule.onNodeWithText(str(R.string.db_max_result_rows)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.db_max_result_chars)).assertIsDisplayed()
    }

    @Test
    fun masterToggleUpdatesSubtitle_andSheetEditsRowValue() {
        // 设置页 → 数据库监听(入口行在小屏折叠区,先滚动至可见再点击)。
        openSettingsPage()
        scrollTo(str(R.string.database_monitor_title))
        rule.onNodeWithText(str(R.string.database_monitor_title)).performClick()
        rule.waitForIdle()

        // 数据库总开关:关 → 返回设置页,副标题变为「未启用」。
        waitForVisible(str(R.string.database_monitor_master))
        rule.onNodeWithText(str(R.string.database_monitor_master)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        assertTrue("关闭总开关后数据库位应联动", !settings.switch.dbListen)
        waitForVisible(str(R.string.settings_capability_disabled))

        // 再进:结果闸门 sheet 编辑行数上限(4321)回写行值。
        rule.onNodeWithText(str(R.string.database_monitor_title)).performClick()
        rule.waitForIdle()
        scrollTo(str(R.string.db_max_result_rows))
        rule.onNodeWithText(str(R.string.db_max_result_rows)).performClick()
        rule.waitForIdle()
        waitForVisible(str(R.string.db_max_result_rows_dialog_title))
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput("4321")
        rule.onNodeWithText(str(R.string.confirm)).performClick()
        rule.waitForIdle()
        waitForVisible("4321")
        assertTrue("sheet 确认后配置应落库", settings.switch.maxResultRows == 4321)

        // 返回设置页:副标题仍显示数据库「未启用」(开关态未被 sheet 编辑污染)。
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        waitForVisible(str(R.string.settings_capability_disabled))
    }
}