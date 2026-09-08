package com.lioisme.modev

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 语言热切换验收测试(T10,2026-08-17 V9;V16 R5 扩展搜索 Dialog 语境):
 * R1 契约:语言切换**零 Activity 重建**(ActivityLifecycleCallbacks 断言
 * onDestroy 不触发)+ 导航栈保留(返回仍落应用列表)+ 全站文本即时切换。
 * V16 R5:官方全屏搜索为独立 Dialog 窗口,断言其内部文本(返回按钮
 * contentDescription,仅 Dialog 内存在)随热切换即时更替(CompositionLocalProvider
 * 覆写 LocalContext 路径)。
 * 结束经 [LocaleManager.setLanguage] 恢复跟随系统,不污染后续用例。
 */
@RunWith(AndroidJUnit4::class)
class LanguageHotSwitchTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    @After
    fun tearDown() {
        LocaleManager.setLanguage(ctx, LocaleManager.TAG_SYSTEM)
        rule.waitForIdle()
    }

    @Test
    fun languageSwitch_keepsActivityAndNavStack_andUpdatesTexts() {
        val app = ctx.applicationContext as Application
        var destroyed = 0
        val callback =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) {
                    destroyed++
                }
            }
        app.registerActivityLifecycleCallbacks(callback)
        try {
            // 应用列表 → 设置(搜索条左上角设置按钮,V18 唯一入口)。
            rule.onNodeWithContentDescription(str(R.string.cd_settings)).performClick()
            rule.waitForIdle()

            // 语言 → English。
            rule.onNodeWithText(str(R.string.language)).performClick()
            rule.waitForIdle()
            rule.onNodeWithText(str(R.string.lang_en)).performClick()
            rule.waitForIdle()

            // 零重建:Activity 未被销毁,设置页仍在前台且文本即时切换。
            // V41 T6:语言行并入「General」分区(旧「Language & Region」分区标题退役)。
            assertEquals(0, destroyed)
            rule.onNodeWithText("Settings").assertIsDisplayed()
            rule.onNodeWithText("General").assertIsDisplayed()

            // 导航栈保留:返回仍落应用列表(筛选按钮唯一)。切换后 UI 已是英文,
            // 测试侧 str() 经未包装的 Application 上下文仍解析中文——此处须用
            // 英文字面量断言(与上方 "Settings" 同风格,2026-08-17 V9.1 修正)。
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Filter").assertIsDisplayed()
        } finally {
            app.unregisterActivityLifecycleCallbacks(callback)
        }
    }

    @Test
    fun expandedSearchDialog_followsHotSwitchedLanguage() {
        // 切中文后展开搜索:占位符经包装上下文取当前语言文本(设备默认语言未知,不硬编码)。
        LocaleManager.setLanguage(ctx, LocaleManager.TAG_ZH)
        rule.waitForIdle()
        rule.onNodeWithText(LocaleManager.wrap(ctx).getString(R.string.apps_search)).performClick()
        rule.waitForIdle()
        // 返回按钮仅存在于展开 Dialog 内(折叠条无返回语义)→ 唯一定位 Dialog 内文本。
        rule.onNodeWithContentDescription(LocaleManager.wrap(ctx).getString(R.string.cd_back)).assertIsDisplayed()

        // 热切换英文(Dialog 保持展开,零重建):Dialog 内文本即时更替。
        LocaleManager.setLanguage(ctx, LocaleManager.TAG_EN)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun jaKo_hotSwitch_rendersNativeTexts_zeroRecreation() {
        // V42 T5:ja/ko 新增语言热切换(资源解析驱动断言,不硬编码日韩字面量;
        // 零重建契约同 R1——生命周期计数法沿用)。
        val app = ctx.applicationContext as Application
        var destroyed = 0
        val callback =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) {
                    destroyed++
                }
            }
        app.registerActivityLifecycleCallbacks(callback)
        try {
            // ja 切换:设置页标题即时渲染日文。
            LocaleManager.setLanguage(ctx, LocaleManager.TAG_JA)
            rule.waitForIdle()
            assertEquals(0, destroyed)
            rule.onNodeWithText(LocaleManager.wrap(ctx).getString(R.string.settings_title)).assertIsDisplayed()

            // ja → ko 直切:全站文本即时更替(多语言链路无中间回退)。
            LocaleManager.setLanguage(ctx, LocaleManager.TAG_KO)
            rule.waitForIdle()
            assertEquals(0, destroyed)
            rule.onNodeWithText(LocaleManager.wrap(ctx).getString(R.string.settings_title)).assertIsDisplayed()

            // 语言选择面板出现 ja/ko 自名选项(选择器完备性)。
            rule.onNodeWithText(str(R.string.language)).performClick()
            rule.waitForIdle()
            rule.onNodeWithText("日本語").assertIsDisplayed()
            rule.onNodeWithText("한국어").assertIsDisplayed()
            // 关闭面板(点选当前语言外的取消路径:直接按返回)。
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            rule.waitForIdle()
        } finally {
            app.unregisterActivityLifecycleCallbacks(callback)
        }
    }

    @Test
    fun jaKo_uiSelection_appliesAndUpdatesTitle() {
        // V42 T5 补强:经语言面板 **UI 点选** 日本語/한국어(非编程直设)——
        // 选择行 → 热切换生效 → 标题即时更替 → localeFlow 单源同步。
        rule.waitForIdle()
        rule.onNodeWithContentDescription(str(R.string.cd_settings)).performClick()
        rule.waitForIdle()

        // 语言行(当前跟随系统,行标签为设备默认语言;经未包装上下文解析)。
        rule.onNodeWithText(str(R.string.language)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("日本語").performClick()
        rule.waitForIdle()
        // 切换后设置页标签即时日文化(经包装上下文解析 ja 文案)。
        assertEquals(LocaleManager.TAG_JA, LocaleManager.currentLanguageTag)
        rule.onNodeWithText(LocaleManager.wrap(ctx).getString(R.string.settings_title)).assertIsDisplayed()

        // 再次打开面板(行标签已是 ja)→ 点选 한국어。
        rule.onNodeWithText(LocaleManager.wrap(ctx).getString(R.string.language)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("한국어").performClick()
        rule.waitForIdle()
        assertEquals(LocaleManager.TAG_KO, LocaleManager.currentLanguageTag)
        rule.onNodeWithText(LocaleManager.wrap(ctx).getString(R.string.settings_title)).assertIsDisplayed()
    }
}