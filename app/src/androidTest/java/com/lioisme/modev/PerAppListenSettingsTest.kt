package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 每应用监听设置页验收(V24 G1 二态偏差模型):
 * - 行内 M3 Switch 直接承载有效配置值(未定制行 = 默认配置位);
 * - 单击整行切换即持久化显式偏差(SP 读回验证;推送链路由 ConfigServerOverrideTest 覆盖);
 * - 「恢复默认配置」行仅在存在自定义时出现;确认后删除偏差回落纯默认配置。
 */
@RunWith(AndroidJUnit4::class)
class PerAppListenSettingsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    private fun settings(): SettingsRepository {
        val app = ctx.applicationContext as com.lioisme.modev.ModevApp
        return app.container.settingsRepo
    }

    @Before
    fun setUp() {
        // 自身是模块默认隐藏:开显隐使详情页入口可达(@After 复位)。
        AppListSettings.setShowModules(true)
        runBlocking { settings().removeOverride(ownPackage) }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        runBlocking { settings().removeOverride(ownPackage) }
        AppListSettings.setShowModules(false)
    }

    private fun openListenSettings() {
        // 行可能在组合窗口外,先滚动定位(与 AppMonitorRedesignTest 同款轮询)。
        val deadline = System.currentTimeMillis() + 10_000
        var opened = false
        while (System.currentTimeMillis() < deadline && !opened) {
            rule.waitForIdle()
            try {
                val scrollables = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
                if (scrollables.isNotEmpty()) {
                    rule.onAllNodes(hasScrollAction()).onFirst()
                        .performScrollToNode(hasText(ownPackage))
                }
                rule.onNodeWithText(ownPackage).performClick()
                opened = true
            } catch (_: AssertionError) {
                Thread.sleep(100)
            }
        }
        check(opened) { "应进入应用详情页" }
        rule.waitForIdle()
        rule.onNodeWithTag("monitor-entry-listen-settings").performScrollTo()
        rule.onNodeWithTag("monitor-entry-listen-settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.listen_settings_hint)).assertIsDisplayed()
    }

    /** 异步写链路轮询:VM 协程落盘后断言存储态(无 waitUntil,手写截止时间循环)。 */
    private fun awaitPersisted(assertion: (AppListenOverride) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        var last: AppListenOverride = AppListenOverride.DEFAULT
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            last = runBlocking { settings().overrideFor(ownPackage) }
            if (assertion(last)) return
            Thread.sleep(100)
        }
        throw AssertionError("偏差未在期限内持久化:kinds=" + last.kinds)
    }

    @Test
    fun switchRows_renderEffectiveValue_andPersistToggle() {
        openListenSettings()

        // CIPHER 默认配置为启用:初始行呈开态(V44 限定键:testTag 带类型线名)。
        val rowTag = "listen-row-crypto-CIPHER"
        rule.onNodeWithTag(rowTag).performScrollTo().assertIsDisplayed().assertIsOn()

        // 单击整行 → 显式停用偏差持久化 + 行值即时翻转。
        rule.onNodeWithTag(rowTag).performClick()
        awaitPersisted { it.kinds["crypto:CIPHER"] == false }
        rule.onNodeWithTag(rowTag).assertIsOff()

        // 再单击 → 回到启用(显式 true 偏差仍存在,非删键)。
        rule.onNodeWithTag(rowTag).performClick()
        awaitPersisted { it.kinds["crypto:CIPHER"] == true }
        rule.onNodeWithTag(rowTag).assertIsOn()
    }

    @Test
    fun resetRow_visibilityFollowsCustomization_andRestoreClearsAll() {
        openListenSettings()

        // 未定制:恢复默认行不存在(降噪契约)。
        assertTrue(
            "零偏差时不得渲染恢复默认行",
            rule.onAllNodes(hasTestTag("listen-reset")).fetchSemanticsNodes().isEmpty(),
        )

        // 预置一项总开关偏差 → 行出现;确认对话框后回落纯默认配置且行消失。
        runBlocking {
            settings().setOverride(ownPackage, AppListenOverride(cryptoListen = false))
        }
        rule.waitForIdle()
        val resetRow = rule.onNodeWithTag("listen-reset").performScrollTo()
        resetRow.assertIsDisplayed()
        resetRow.performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.confirm)).performClick()

        awaitPersisted { it.isDefault }
        rule.waitForIdle()
        assertTrue(
            "恢复默认后该行应消失",
            rule.onAllNodes(hasTestTag("listen-reset")).fetchSemanticsNodes().isEmpty(),
        )
    }

    /** V25 采集微调项 E2E:底部 sheet 编辑 → 偏差持久化(注入侧推送链路另测)。 */
    @Test
    fun optionSheets_editCaptureLimits_andPersistDeviations() {
        openListenSettings()

        // 单字段字节上限:点击选项行 → MaxBytesSheet → 全量替换为 2048 → 确认。
        val maxBytesRow = rule.onNodeWithTag("listen-row-max-bytes").performScrollTo()
        maxBytesRow.assertIsDisplayed()
        maxBytesRow.performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextReplacement("2048")
        rule.onNodeWithText(str(R.string.confirm)).performClick()
        awaitPersisted { it.maxBytesPerField == 2048 }

        // 结果行数上限:同链路验证 IntLimitSheet。
        val rowsRow = rule.onNodeWithTag("listen-row-result-rows").performScrollTo()
        rowsRow.assertIsDisplayed()
        rowsRow.performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextReplacement("50")
        rule.onNodeWithText(str(R.string.confirm)).performClick()
        awaitPersisted { it.maxResultRows == 50 }
    }

    private fun str(id: Int) = ctx.getString(id)
}
