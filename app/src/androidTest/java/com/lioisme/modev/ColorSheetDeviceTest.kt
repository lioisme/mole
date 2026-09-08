package com.lioisme.modev

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.ui.MainActivity
import com.lioisme.modev.ui.theme.ThemeSeeds
import com.lioisme.modev.ui.theme.ThemeSettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主题色板设备验收(V42 T4 v8 形态回退):
 * - ColorSheet 打开后 20 个圆形色点(`color-swatch`)按 5 列网格渲染;
 * - 点选色点即写 ThemeConfig(useWallpaper=false + customSeedColor)且选择即生效不关弹层;
 * - 「跟随系统壁纸」行恢复壁纸模式(useWallpaper=true);
 * - sheet 关闭路径正常(onDismissRequest 收口)。
 * 前置/卫生:@After 恢复壁纸模式(V41 工厂默认)。
 */
@RunWith(AndroidJUnit4::class)
class ColorSheetDeviceTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = ctx.getString(id)

    @After
    fun tearDown() {
        // 恢复壁纸跟随(生产默认),不污染后续用例的主题态。
        ThemeSettingsManager.update(
            ThemeSettingsManager.themeConfig.value.copy(useWallpaper = true),
            ctx,
        )
    }

    private fun openColorSheet() {
        rule.onNodeWithText(str(R.string.theme_color)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("theme-color-sheet").assertIsDisplayed()
    }

    @Test
    fun colorSheet_rendersTwentySwatches_gridAndWallpaperRow() {
        rule.waitForIdle()
        openColorSheet()

        // v8 形态:20 色固定数量全量渲染(非惰性网格,无滚动丢失)。
        rule.onAllNodesWithTag("color-swatch").assertCountEquals(ThemeSeeds.OPTIONS.size)
        assertEquals("色板预设应为 20 色", 20, ThemeSeeds.OPTIONS.size)
        // 壁纸行在(选中态 = 当前 useWallpaper)。
        rule.onNodeWithText(str(R.string.system_wallpaper)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.pick_accent)).assertIsDisplayed()
    }

    @Test
    fun colorSheet_selectSwatch_persistsSeed_andStaysOpen() {
        rule.waitForIdle()
        openColorSheet()

        val picked = ThemeSeeds.OPTIONS[3]
        rule.onAllNodesWithTag("color-swatch")[3].performClick()
        rule.waitForIdle()

        val config = ThemeSettingsManager.themeConfig.value
        assertEquals("点选即写 useWallpaper=false", false, config.useWallpaper)
        assertEquals("点选即写 customSeedColor", picked, config.customSeedColor)
        // 选择即生效不关弹层(v8 语义):sheet 与色点仍在。
        rule.onNodeWithTag("theme-color-sheet").assertIsDisplayed()
        rule.onAllNodesWithTag("color-swatch").assertCountEquals(ThemeSeeds.OPTIONS.size)
    }

    @Test
    fun colorSheet_wallpaperRow_restoresFollowWallpaper() {
        rule.waitForIdle()
        // 先落入种子模式,再经壁纸行恢复。
        ThemeSettingsManager.update(
            ThemeSettingsManager.themeConfig.value.copy(useWallpaper = false),
            ctx,
        )
        assertTrue(ThemeSettingsManager.themeConfig.value.useWallpaper.not())
        openColorSheet()

        rule.onNodeWithText(str(R.string.system_wallpaper)).performClick()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline &&
            !ThemeSettingsManager.themeConfig.value.useWallpaper
        ) {
            Thread.sleep(100)
        }
        assertEquals("壁纸行应恢复跟随壁纸", true, ThemeSettingsManager.themeConfig.value.useWallpaper)
    }

    @Test
    fun colorSheet_dismiss_closesSheet() {
        rule.waitForIdle()
        openColorSheet()
        // 物理返回键收口 ModalBottomSheet(onDismissRequest → hide 动画 → 移除)。
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline &&
            rule.onAllNodesWithTag("theme-color-sheet").fetchSemanticsNodes().isNotEmpty()
        ) {
            Thread.sleep(100)
            rule.waitForIdle()
        }
        rule.onAllNodesWithTag("theme-color-sheet").assertCountEquals(0)
    }
}
