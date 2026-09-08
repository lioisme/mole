package com.lioisme.modev

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.ui.MainActivity
import com.lioisme.modev.data.repo.AppListSettings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AppIcon 底图状态驱动设备锚点(V42 T2):
 * 应用列表真实 Coil 加载(真实 PackageManager Drawable)完成后,占位底图
 * (`app-icon-placeholder`)必须归零——不规则/透明图标不再透出底层色块
 * (V42 R2 主诉的实机验证)。加载中/失败态兜底由 Robolectric
 * AppIconPlaceholderTest 覆盖(fake loader 双路),本类仅锚定真实加载终态。
 */
@RunWith(AndroidJUnit4::class)
class AppIconDeviceTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        AppListSettings.setShowModules(false)
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
    fun appsList_afterRealLoad_placeholderGone_everywhere() {
        // 被测应用默认隐藏(自身为 Xposed 模块),开启显隐保证行渲染。
        AppListSettings.setShowModules(true)
        rule.waitForIdle()
        // 行存在性:app-row 必须出现(设备上已安装应用非空)。
        assertTrue(
            "应用列表应渲染出应用行",
            pollUntil(15_000) {
                rule.onAllNodesWithTag("app-row").fetchSemanticsNodes().isNotEmpty()
            },
        )
        // 真实加载终态:占位底图全部移除(加载中可见窗口由 Robolectric 侧覆盖)。
        assertTrue(
            "真实加载完成后占位底图必须全部移除",
            pollUntil(15_000) {
                rule.onAllNodesWithTag("app-icon-placeholder").fetchSemanticsNodes().isEmpty()
            },
        )
    }
}
