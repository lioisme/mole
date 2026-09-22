package com.lioisme.modev

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.ui.MainActivity
import com.lioisme.modev.ui.theme.ThemeSeeds
import com.lioisme.modev.ui.theme.ThemeSettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // V41 起主题色板行在设置页(根页只有应用列表),必须先导航 —— 缺此前置时
        // 本类 4 例全部「找不到节点」(V45 前端轮实锤)。
        rule.openSettings(str(R.string.cd_settings))
        rule.clickScrolledIntoView(hasText(str(R.string.theme_color)))
        rule.awaitAnchor("theme-color-sheet", "点「主题色」行应打开色板 sheet")
    }

    @Test
    fun colorSheet_rendersTwentySwatches_gridAndWallpaperRow() {
        rule.waitForIdle()
        openColorSheet()

        // v8 形态:20 色固定数量全量渲染(非惰性网格,无滚动丢失)。
        rule.onAllNodesWithTag("color-swatch").assertCountEquals(ThemeSeeds.OPTIONS.size)
        assertEquals("色板预设应为 20 色", 20, ThemeSeeds.OPTIONS.size)
        // 壁纸行在(选中态 = 当前 useWallpaper)。sheet 内容全量组合,断言存在即可
        // (20 色网格之下是否可见随屏高而变,显示性断言在模拟器上不稳)。
        assertTrue(
            "壁纸行应在色板内",
            rule.onAllNodesWithText(str(R.string.system_wallpaper)).fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "「自选主色」行应在色板内",
            rule.onAllNodesWithText(str(R.string.pick_accent)).fetchSemanticsNodes().isNotEmpty(),
        )
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

        rule.clickScrolledIntoView(hasText(str(R.string.system_wallpaper)))
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

    /**
     * V46 R2/D7 几何锁:色点网格**横纵步长等值且非零**。
     * 修复前 `ColorSheet` 的网格 Column 无 verticalArrangement(四行圆点上下相切
     * = 用户报的「配色之间没有间距」);修复后两向均取 ColorSwatchGap 单点。
     */
    @Test
    fun colorSheet_swatchGridHasEqualGaps() {
        rule.waitForIdle()
        openColorSheet()
        val nodes = rule.onAllNodesWithTag("color-swatch").fetchSemanticsNodes()
        assertEquals("20 色行主序渲染", ThemeSeeds.OPTIONS.size, nodes.size)

        val a = nodes[0]
        val b = nodes[1] // 同行右邻
        val c = nodes[5] // 下一行同列(chunked(5) 行主序)
        // 密度取 rule.density(ComposeTestRule 提供;SemanticsNode 在本版本无该属性)
        val hGap = with(rule.density) { (b.boundsInRoot.left - a.boundsInRoot.right).toDp() }
        val vGap = with(rule.density) { (c.boundsInRoot.top - a.boundsInRoot.bottom).toDp() }
        assertTrue("纵向步长必须 > 0(实测 $vGap)", vGap > 4.dp)
        assertFalse("色点不得相切(实测 $vGap)", vGap <= 0.dp)
        assertTrue(
            "横纵步长应等值(实测 h=$hGap v=$vGap)",
            kotlin.math.abs(hGap.value - vGap.value) < 1f,
        )
    }
}
