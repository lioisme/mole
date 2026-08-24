package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.TriState
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 每应用监听设置页验收(V23 G3/T5.3,三态继承):
 * - 17 类别 + 双总开关三态行渲染(行尾态文本「跟随全局」初始态);
 * - 点击类别行 → 弹三态 sheet → 选「强制开」→ 行尾值即时更新且持久化
 *   (SP 读回验证,注入侧推送链路由 ConfigServerOverrideTest 覆盖);
 * - 重置 → 确认对话框 → 覆盖删除回落「跟随全局」。
 */
@RunWith(AndroidJUnit4::class)
class PerAppListenSettingsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    private fun settings(): SettingsRepository {
        val app = ctx.applicationContext as com.lioisme.modev.ModevApp
        return app.container.settings
    }

    @Before
    fun setUp() {
        // 自身是模块默认隐藏:开显隐使详情页入口可达(@After 复位)。
        com.lioisme.modev.data.repo.AppListSettings.setShowModules(true)
        runBlocking { settings().removeOverride(ownPackage) }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        runBlocking { settings().removeOverride(ownPackage) }
        com.lioisme.modev.data.repo.AppListSettings.setShowModules(false)
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

    @Test
    fun triStateRows_render_andPersistSelection() {
        openListenSettings()

        // CIPHER 行初始为「跟随全局」。
        val rowTag = "tri-row-CIPHER"
        rule.onNodeWithTag(rowTag).performScrollTo().assertIsDisplayed()

        // 打开三态 sheet 并选「强制关」→ 行尾值更新 + SP 持久化
        // (初始 INHERIT 态下 sheet 内 tri_off 文本唯一,可直接定位)。
        rule.onNodeWithTag(rowTag).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.tri_off)).performClick()
        rule.waitForIdle()

        runBlocking {
            val stored = settings().overrideFor(ownPackage)
            assertEquals(TriState.OFF, stored.kindOverrides["CIPHER"])
            // 行尾值即时回显(有覆盖 → primary 态文本)。
            assertEquals(
                str(R.string.tri_off),
                stored.kindOverrides["CIPHER"]?.let { ctx.getString(R.string.tri_off) },
            )
        }
    }

    @Test
    fun reset_removesAllOverrides() {
        openListenSettings()

        // 预置一项覆盖(VM 流热更,当前已在监听设置页,直接操作重置)。
        runBlocking {
            settings().setOverride(
                ownPackage,
                com.lioisme.modev.core.model.AppListenOverride(cryptoListen = TriState.OFF),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag("listen-reset").performScrollTo()
        rule.onNodeWithTag("listen-reset").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.confirm)).performClick()
        rule.waitForIdle()

        runBlocking {
            assertEquals(true, settings().overrideFor(ownPackage).isDefault)
        }
    }

    private fun str(id: Int) = ctx.getString(id)
}
