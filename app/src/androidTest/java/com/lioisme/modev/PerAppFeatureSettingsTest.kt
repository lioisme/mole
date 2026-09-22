package com.lioisme.modev

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V37 双域 UI 插桩验收:
 * - 每应用「功能设置」页:干预类别行即写即持久化;画像 sheet 选择落偏差;
 * - 全局「关键词库」页:新增条目 → 库持久化;
 * - 监听页不再渲染功能域行(双域边界)。
 */
@RunWith(AndroidJUnit4::class)
class PerAppFeatureSettingsTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val ownPackage = "com.lioisme.modev"

    private fun settings(): SettingsRepository =
        (ctx.applicationContext as ModevApp).container.settingsRepo

    private fun str(id: Int) = ctx.getString(id)

    @Before
    fun setUp() {
        AppListSettings.setShowModules(true)
        runBlocking { settings().removeOverride(ownPackage) }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        runBlocking {
            settings().removeOverride(ownPackage)
            settings().setGlobalFeature(FeatureConfig())
            (ctx.applicationContext as ModevApp).container.keywordLibrary.clear()
        }
        AppListSettings.setShowModules(false)
    }

    private fun openMonitorEntry() {
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
    }

    private fun openFeatureSettings() {
        openMonitorEntry()
        rule.onNodeWithTag("monitor-entry-feature-settings").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.feature_settings_hint)).assertIsDisplayed()
    }

    private fun awaitPersisted(assertion: (AppListenOverride) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        var last: AppListenOverride = AppListenOverride.DEFAULT
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            last = settings().overrideFor(ownPackage)
            if (assertion(last)) return
            Thread.sleep(100)
        }
        throw AssertionError("偏差未在期限内持久化:kinds=${last.kinds} profile=${last.spoofProfileId}")
    }

    @Test
    fun featurePage_rendersGroups_andProcessGuardTogglePersists() {
        openFeatureSettings()

        // 功能卡分区存在(组标题与 master 行同文案「进程守护」,锚定必须走 testTag)。
        rule.onNodeWithTag("feature-group-header-PROCESS_GUARD")
            .performScrollTo().assertIsDisplayed()
        // 退出拦截默认关(功能域默认全关)。
        rule.onNodeWithTag("feature-row-EXIT_INTERCEPT")
            .performScrollTo().assertIsDisplayed().assertIsOff()
        rule.onNodeWithTag("feature-row-EXIT_INTERCEPT").performClick()
        awaitPersisted { it.kinds["process:EXIT_INTERCEPT"] == true }
        rule.onNodeWithTag("feature-row-EXIT_INTERCEPT").assertIsOn()
    }

    @Test
    fun featurePage_listenDomainRowsAbsent() {
        openFeatureSettings()
        // 双域边界:监听类别行(CIPHER)不得出现在功能页。
        assertTrue(
            "监听类别不得混入功能页",
            rule.onAllNodes(hasText(str(R.string.kind_cipher))).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * V47 WP-5:每应用画像行改绑**画像库**(旧三常量档 sheet 退役)。
     * 选项 testTag 携带库主键(内置档 = `builtin:<seedId>`),绑定即写偏差 spoofProfileId。
     */
    @Test
    fun featurePage_profileSheet_selectsProfile() {
        openFeatureSettings()
        rule.onNodeWithTag("feature-profile-picker").performScrollTo().performClick()
        rule.waitForIdle()
        val xiaomiId = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.XIAOMI_13)
        rule.onNodeWithTag("feature-profile-option-$xiaomiId")
            .performScrollTo().performClick()
        awaitPersisted { it.spoofProfileId == xiaomiId }
    }

    /**
     * 缺失态必须显式上屏(V47 WP-5 关掉静默回落):绑一个库里不存在的 id,
     * 画像行尾栏应渲染「画像缺失」红字,而不是回落成任何档名。
     */
    @Test
    fun featurePage_missingBinding_isSurfaced() {
        runBlocking {
            settings().setOverride(ownPackage, AppListenOverride(spoofProfileId = "no-such-profile"))
        }
        openFeatureSettings()
        rule.onNodeWithTag("feature-profile-picker").performScrollTo()
        rule.onNodeWithTag("feature-profile-binding")
            .assertIsDisplayed()
            .assertTextEquals(str(R.string.profile_binding_missing))
    }

    @Test
    fun listenPage_featureRowsAbsent() {
        openMonitorEntry()
        rule.onNodeWithTag("monitor-entry-listen-settings").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.listen_settings_hint)).assertIsDisplayed()
        // 功能域 master 行(进程守护)不得出现在监听页。
        assertTrue(
            "功能 master 不得混入监听页",
            rule.onAllNodes(hasText(str(R.string.listen_row_master_process)))
                .fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun keywordLibrary_addEntry_persists() {
        openMonitorEntry()
        rule.onNodeWithTag("monitor-entry-feature-settings").performScrollTo().performClick()
        rule.waitForIdle()
        // 空库引导 → 打开关键词库。
        rule.onNodeWithTag("feature-keyword-goto-library").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText(str(R.string.keywords_empty_title)).assertIsDisplayed()
        rule.onNodeWithTag("keywords-add").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("keywords-input").performTextReplacement("检测到模拟器")
        rule.onNodeWithTag("keywords-confirm").performClick()
        rule.waitForIdle()
        val library = (ctx.applicationContext as ModevApp).container.keywordLibrary
        val deadline = System.currentTimeMillis() + 5_000
        var found = false
        while (System.currentTimeMillis() < deadline && !found) {
            rule.waitForIdle()
            found = library.entries.value.any { it.text == "检测到模拟器" }
            Thread.sleep(100)
        }
        assertTrue("关键词应入库", found)
        assertEquals(
            KeywordMatchMode.CONTAINS,
            library.entries.value.first { it.text == "检测到模拟器" }.matchMode,
        )
    }
}
