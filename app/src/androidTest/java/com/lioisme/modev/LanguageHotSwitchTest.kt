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
 * 语言热切换验收测试(T10,2026-08-17 V9):
 * R1 契约:语言切换**零 Activity 重建**(ActivityLifecycleCallbacks 断言
 * onDestroy 不触发)+ 导航栈保留(返回仍落应用列表)+ 全站文本即时切换。
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
            // 应用列表 → 设置(唯一入口)。
            rule.onNodeWithContentDescription(str(R.string.cd_filter)).performClick()
            rule.onNodeWithText(str(R.string.settings_title)).performClick()
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
}