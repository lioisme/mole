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
            assertEquals(0, destroyed)
            rule.onNodeWithText("Settings").assertIsDisplayed()
            rule.onNodeWithText("Language & Region").assertIsDisplayed()

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
}