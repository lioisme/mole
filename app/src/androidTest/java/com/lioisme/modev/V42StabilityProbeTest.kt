package com.lioisme.modev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onAllNodesWithText
import com.lioisme.modev.ui.MainActivity
import com.lioisme.modev.i18n.LocaleManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V42 稳定性压力探针(战役 G5 门组成员):
 * 三条 V42 变更链路在循环操作下的状态一致性——
 * 1. 语言热切 ×10(en/zh 交替,零 Activity 重建 + 文案同步);
 * 2. 端口 sheet 校验 ×10(合法/非法交替,sheet 状态机不漂移、偏好最终一致);
 * 3. 主题色板点选 ×10(ThemeConfig 与选中态一致)。
 * 循环次数为冒烟级(压力目的 = 状态机泄漏/漂移检测,非性能基准)。
 */
@RunWith(AndroidJUnit4::class)
class V42StabilityProbeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    @After
    fun tearDown() {
        LocaleManager.setLanguage(ctx, LocaleManager.TAG_SYSTEM)
    }

    /** 有界轮询谓词。 */
    private fun pollUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (condition()) return true
            Thread.sleep(100)
        }
        rule.waitForIdle()
        return condition()
    }

    @Test
    fun stress_languageHotSwitch_x10_zeroRecreationAndTextsFollow() {
        val app = ctx.applicationContext as android.app.Application
        var destroyed = 0
        val callback = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityResumed(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) {
                destroyed++
            }
        }
        app.registerActivityLifecycleCallbacks(callback)
        try {
            // 标题文案在设置页顶栏(V41 根页 IA 后根页不再承载它)——先导航,否则整例「找不到文案」。
            rule.openSettings(str(R.string.cd_settings))
            repeat(10) { round ->
                val tag = if (round % 2 == 0) LocaleManager.TAG_EN else LocaleManager.TAG_ZH
                LocaleManager.setLanguage(ctx, tag)
                // 期望文案必须按**切换后**的语言即时解析(在循环外算一次 = 拿同一文案去比
                // 两种语言的界面,第二轮起必然假红)。
                val expected = LocaleManager.wrap(ctx).getString(R.string.settings_title)
                assertTrue(
                    "第 ${round + 1} 次切换($tag)后标题未随 locale 渲染",
                    pollUntil(5_000) {
                        rule.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
                    },
                )
                assertEquals("热切换不得销毁 Activity(round=$round)", 0, destroyed)
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(callback)
        }
    }

    @Test
    fun stress_portSheet_validationCycle_x10_stateMachineStable() {
        // 端口 sheet 状态机循环:开 → 非法(80,特权错误)→ 关闭;最终开 → 合法保存。
        // 10 轮后 sheet 仍可正常开合、错误态正确复位 = 状态机无漂移。
        val container = (ctx.applicationContext as ModevApp).container
        val settings = container.settingsRepo
        val vm = com.lioisme.modev.ui.settings.ServicesViewModelFactory(container)
            .create(com.lioisme.modev.ui.settings.ServicesViewModel::class.java)
        val currentPort = settings.serverPort.value
        // 端口行在服务页(V41 起不在根页):整例先走一次前置导航,循环内在同屏开合 sheet。
        rule.openServices(str(R.string.cd_settings), str(R.string.services_settings_title))
        var open = false
        repeat(10) { round ->
            if (!open) {
                rule.clickScrolledIntoView(hasText(str(R.string.settings_port)))
                rule.waitForIdle()
                rule.onNodeWithTag("port-setting-sheet").assertIsDisplayed()
                rule.onNodeWithTag("port-input").performTextReplacement("80")
                rule.waitForIdle()
                rule.onNodeWithTag("port-confirm").performClick()
                // 特权错误出现(异步校验落定)。
                assertTrue(
                    "round=$round 特权错误应出现",
                    pollUntil(5_000) {
                        rule.onAllNodesWithText(str(R.string.settings_port_privileged))
                            .fetchSemanticsNodes().isNotEmpty()
                    },
                )
                // 关闭 sheet(取消钮)。
                rule.onNodeWithText(str(R.string.cancel)).performClick()
                assertTrue(
                    "round=$round sheet 应关闭",
                    pollUntil(5_000) {
                        rule.onAllNodesWithTag("port-setting-sheet").fetchSemanticsNodes().isEmpty()
                    },
                )
                // 交替开合法端口确认(仅最后一轮,验证保存链路仍通)。
                if (round == 9) {
                    rule.clickScrolledIntoView(hasText(str(R.string.settings_port)))
                    rule.waitForIdle()
                    rule.onNodeWithTag("port-input").performTextReplacement(currentPort.toString())
                    rule.waitForIdle()
                    rule.onNodeWithTag("port-confirm").performClick()
                    assertTrue(
                        "自身端口豁免应关闭 sheet",
                        pollUntil(5_000) {
                            rule.onAllNodesWithTag("port-setting-sheet").fetchSemanticsNodes().isEmpty()
                        },
                    )
                    assertEquals("校验循环后端口应保持原值", currentPort, settings.serverPort.value)
                    open = true
                }
            }
        }
    }

    @Test
    fun stress_themeSwatchSelection_x10_themeConfigConsistent() {
        // 设置 → 主题色 sheet:10 轮点选不同色点,ThemeConfig 即写即一致;最终恢复壁纸。
        val ctxApp = ctx.applicationContext as ModevApp
        val startSeed = com.lioisme.modev.ui.theme.ThemeSettingsManager.themeConfig.value
        try {
            rule.openSettings(str(R.string.cd_settings))
            rule.clickScrolledIntoView(hasText(str(R.string.theme_color)))
            rule.waitForIdle()
            rule.onNodeWithTag("theme-color-sheet").assertIsDisplayed()
            repeat(10) { round ->
                val idx = round % com.lioisme.modev.ui.theme.ThemeSeeds.OPTIONS.size
                rule.onAllNodesWithTag("color-swatch")[idx].performClick()
                val expected = com.lioisme.modev.ui.theme.ThemeSeeds.OPTIONS[idx]
                assertTrue(
                    "round=$round ThemeConfig 应即时写入选中色",
                    pollUntil(5_000) {
                        val cfg = com.lioisme.modev.ui.theme.ThemeSettingsManager.themeConfig.value
                        !cfg.useWallpaper && cfg.customSeedColor == expected
                    },
                )
            }
        } finally {
            com.lioisme.modev.ui.theme.ThemeSettingsManager.update(
                startSeed.copy(useWallpaper = true),
                ctx,
            )
        }
    }
}
