package com.lioisme.modev

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugHotSwitchTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        LocaleManager.setLanguage(ctx, LocaleManager.TAG_SYSTEM)
        rule.waitForIdle()
    }

    private fun log(tag: String, msg: String) = android.util.Log.i("HSD-$tag", msg)

    private fun dumpCds(tag: String, desc: String) {
        val nodes = rule.onAllNodes(hasContentDescription(desc)).fetchSemanticsNodes()
        log(tag, "cd[$desc] count=${nodes.size} " + nodes.joinToString { n -> "(${n.boundsInRoot.left.toInt()},${n.boundsInRoot.top.toInt()},${n.boundsInRoot.width.toInt()}x${n.boundsInRoot.height.toInt()})" })
    }

    @Test
    fun debug_dumpTreeAfterSwitch() {
        LocaleManager.setLanguage(ctx, LocaleManager.TAG_ZH)
        rule.waitForIdle()
        val zhBack = LocaleManager.wrap(ctx).getString(R.string.cd_back)
        rule.onNodeWithText(LocaleManager.wrap(ctx).getString(R.string.apps_search)).performClick()
        rule.waitForIdle()
        dumpCds("ZH", zhBack)
        rule.onNodeWithContentDescription(zhBack).assertIsDisplayed()
        log("ZH", "displayed ok")

        LocaleManager.setLanguage(ctx, LocaleManager.TAG_EN)
        rule.waitForIdle()
        Thread.sleep(1500)
        rule.waitForIdle()
        dumpCds("EN", "Back")
        dumpCds("EN", zhBack)
        dumpCds("EN", LocaleManager.wrap(ctx).getString(R.string.cd_filter))
        try {
            rule.onNodeWithContentDescription("Back").assertIsDisplayed()
            log("EN", "Back displayed ok")
        } catch (t: Throwable) {
            log("EN", "Back assert failed: " + t.message)
        }
    }
}
