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
 * 监听设置合并页验收(V19 T4,合并自算法/数据库监听两页):
 * - 设置页「功能」区单入口「监听设置」,副标题 = 算法/数据库启用态组合文案
 *   (随开关实时变化,与 SettingsRepository 单一事实源同源);
 * - 合并页四分区(算法监听 / 记录选项 / 数据库监听 / 结果闸门)齐全;
 * - 记录选项 sheet 编辑(MaxBytes)回写并展示;
 * - 返回栈:设置页 → 监听设置 → 设置页。
 * 开关态持久化(SharedPreferences + 远端镜像),@Before 快照 @After 还原防跨用例污染。
 */
@RunWith(AndroidJUnit4::class)
class MonitorSettingsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val settings: SettingsRepository
        get() = (ctx.applicationContext as com.lioisme.modev.ModevApp).container.settings

    private var initialSwitch: HookSwitch? = null

    private fun str(id: Int) = ctx.getString(id)

    /** 期望副标题 = 当前开关态的启用词组合(与生产渲染同源)。 */
    private fun expectedSummary(s: HookSwitch): String =
        ctx.getString(
            R.string.settings_monitor_summary,
            ctx.getString(
                if (s.cryptoListen) R.string.settings_capability_enabled else R.string.settings_capability_disabled
            ),
            ctx.getString(
                if (s.dbListen) R.string.settings_capability_enabled else R.string.settings_capability_disabled
            ),
        )

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
        waitForVisible(str(R.string.monitor_settings_title))
    }

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(text))
    }

    @Test
    fun entry_subtitleFollowsSwitch_andPageHasFourSections() {
        // 设置页:单入口「监听设置」+ 组合副标题(当前开关态)。
        openSettingsPage()
        waitForVisible(expectedSummary(settings.switch))
        rule.onAllNodes(hasText(str(R.string.algorithm_monitor_title))).assertCountEquals(0)

        // 进入合并页:四分区齐全(逐区滚动断言)。
        rule.onNodeWithText(str(R.string.monitor_settings_title)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)

        // ①算法监听:总开关 + 类别开关。
        waitForVisible(str(R.string.algorithm_monitor_master))
        rule.onNodeWithText(str(R.string.switch_cipher)).assertIsDisplayed()
        // ②记录选项。
        scrollTo(str(R.string.section_record_options))
        rule.onNodeWithText(str(R.string.switch_log_stack)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.capture_max_bytes)).assertIsDisplayed()
        // ③数据库监听。
        scrollTo(str(R.string.database_monitor_master))
        rule.onNodeWithText(str(R.string.switch_db_query)).assertIsDisplayed()
        // ④结果闸门。
        scrollTo(str(R.string.db_max_result_rows))
        rule.onNodeWithText(str(R.string.db_max_result_chars)).assertIsDisplayed()
    }

    @Test
    fun masterToggleUpdatesSubtitle_andSheetsEditRowValue() {
        // 设置页 → 监听设置。
        openSettingsPage()
        rule.onNodeWithText(str(R.string.monitor_settings_title)).performClick()
        rule.waitForIdle()

        // 算法总开关:关 → 返回设置页,副标题算法位变为「未启用」。
        waitForVisible(str(R.string.algorithm_monitor_master))
        rule.onNodeWithText(str(R.string.algorithm_monitor_master)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        val disabledSummary = expectedSummary(settings.switch)
        assertTrue("关闭总开关后副标题应联动", !settings.switch.cryptoListen)
        waitForVisible(disabledSummary)

        // 再进:记录选项 sheet 编辑 MaxBytes(1234)回写行值。
        rule.onNodeWithText(str(R.string.monitor_settings_title)).performClick()
        rule.waitForIdle()
        scrollTo(str(R.string.capture_max_bytes))
        rule.onNodeWithText(str(R.string.capture_max_bytes)).performClick()
        rule.waitForIdle()
        waitForVisible(str(R.string.capture_max_bytes_dialog_title))
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNode(hasSetTextAction()).performTextInput("1234")
        rule.onNodeWithText(str(R.string.confirm)).performClick()
        rule.waitForIdle()
        waitForVisible("1234")
        assertTrue("sheet 确认后配置应落库", settings.switch.maxBytesPerField == 1234)

        // 返回设置页:副标题仍显示算法「未启用」(开关态未被 sheet 编辑污染)。
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        waitForVisible(expectedSummary(settings.switch))
    }
}