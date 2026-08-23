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
 * 算法监听设置页验收(V21 T3,自合并页拆分还原):
 * - 设置页「功能」区入口「算法监听」,副标题 = 算法启用词(随开关实时变化,
 *   与 SettingsRepository 单一事实源同源;数据库入口独立存在,互不耦合);
 * - 页面两分区(监听类别 = 总开关 + 12 类别 / 记录选项)齐全;
 * - 记录选项 sheet 编辑(MaxBytes)回写并展示;
 * - 返回栈:设置页 → 算法监听 → 设置页。
 * 开关态持久化,@Before 快照 @After 还原防跨用例污染。
 */
@RunWith(AndroidJUnit4::class)
class AlgorithmSettingsScreenTest {

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

    /** 断言文本节点已组合(本测试库无 assertExists;LazyColumn 窗口内存在)。 */
    private fun existsText(text: String) {
        assertTrue("文本不存在于语义树: $text", rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    private fun openSettingsPage() {
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_settings)).performClick()
        rule.waitForIdle()
        waitForVisible(str(R.string.algorithm_monitor_title))
    }

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText(text))
    }

    @Test
    fun entrySubtitleFollowsSwitch_andPageHasTwoSections() {
        // 设置页:入口「算法监听」+ 独立入口「数据库监听」。
        openSettingsPage()
        waitForVisible(
            str(
                if (settings.switch.cryptoListen) R.string.settings_capability_enabled else R.string.settings_capability_disabled
            ),
        )
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).assertIsDisplayed()
        // 数据库入口行在小屏(360x640dp)落在折叠区,滚动至可见后断言。
        scrollTo(str(R.string.database_monitor_title))
        rule.onNodeWithText(str(R.string.database_monitor_title)).assertIsDisplayed()

        // 进入算法页:①监听类别(总开关 + 12 类别)。
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription(str(R.string.cd_back)).assertCountEquals(1)
        rule.onAllNodesWithContentDescription(str(R.string.cd_filter)).assertCountEquals(0)
        waitForVisible(str(R.string.algorithm_monitor_master))
        rule.onNodeWithText(str(R.string.switch_cipher)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_message_digest)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.switch_mac)).assertIsDisplayed()
        // 类别行在小屏(360x640dp)超出首屏;「监听类别」为单一 LazyColumn item
        //(整段组合,段内行 performScrollToNode 不可靠,实测只滚 161px)。
        // 段中行滚动 0 时已在组合窗口,断言存在即可。
        existsText(str(R.string.switch_signature))
        existsText(str(R.string.switch_key_param))
        existsText(str(R.string.switch_key_generation))
        existsText(str(R.string.switch_key_factory))
        existsText(str(R.string.switch_key_store))
        existsText(str(R.string.switch_key_agreement))
        // ②记录选项:滚到该段(独立 item,跨 item scrollTo 稳定有效),
        // 段尾行(random/base64/webview)进入组合窗口,断言存在;
        // 记录选项行滚动后可见。
        scrollTo(str(R.string.section_record_options))
        rule.waitForIdle()
        existsText(str(R.string.switch_random))
        existsText(str(R.string.switch_base64))
        existsText(str(R.string.switch_webview))
        rule.onNodeWithText(str(R.string.switch_log_stack)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.capture_max_bytes)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.capture_ignore_stack)).assertIsDisplayed()
    }

    @Test
    fun masterToggleUpdatesSubtitle_andSheetEditsRowValue() {
        // 设置页 → 算法监听。
        openSettingsPage()
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).performClick()
        rule.waitForIdle()

        // 算法总开关:关 → 返回设置页,副标题变为「未启用」。
        waitForVisible(str(R.string.algorithm_monitor_master))
        rule.onNodeWithText(str(R.string.algorithm_monitor_master)).performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_back)).performClick()
        rule.waitForIdle()
        assertTrue("关闭总开关后算法位应联动", !settings.switch.cryptoListen)
        waitForVisible(str(R.string.settings_capability_disabled))

        // 再进:记录选项 sheet 编辑 MaxBytes(1234)回写行值。
        rule.onNodeWithText(str(R.string.algorithm_monitor_title)).performClick()
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
        waitForVisible(str(R.string.settings_capability_disabled))
    }
}