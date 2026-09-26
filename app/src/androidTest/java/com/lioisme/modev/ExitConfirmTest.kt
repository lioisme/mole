package com.lioisme.modev

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 根页双击返回退出验收:
 * 首次返回 → Snackbar「再按一次退出程序」且 Activity 存活;窗口(2s)内再按 → finish。
 *
 * 单用例设计:finish 会销毁 Activity 而进程因 MCP 常驻存活,若拆成多用例,
 * 前一用例 finish 后规则再启动会落入已销毁任务(No compose hierarchies);
 * 超窗重提示分支的状态机逻辑由 JVM 单测 `DoubleBackExitTest` 覆盖,本类只锚定导航层接线。
 * 零依赖返回键(sendKeyDownUpSync,与 [FourScreensNavTest] 同款)。
 */
@RunWith(AndroidJUnit4::class)
class ExitConfirmTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    @Test
    fun firstBack_prompts_secondWithinWindow_finishes() {
        rule.waitForIdle()
        val act = rule.activity

        pressBack()
        assertTrue(
            "首次返回应出现退出提示",
            rule.onAllNodesWithText(ctx.getString(R.string.exit_press_back_again))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue("首次返回不得退出", !act.isFinishing)

        // 窗口(2s)内第二次返回 → finish。
        pressBack()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !act.isFinishing) {
            rule.waitForIdle()
            Thread.sleep(100)
        }
        assertTrue("窗口内二次返回应退出", act.isFinishing)
    }
}
