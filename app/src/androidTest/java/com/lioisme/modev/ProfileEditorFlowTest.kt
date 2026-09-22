package com.lioisme.modev

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileGenerators
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.domain.config.SpoofProfileService
import com.lioisme.modev.ui.MainActivity
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 画像库 → **可视化参数编辑器**端到端(V47 WP-5 插桩档)。
 *
 * 为什么这条主通路必须在真机/模拟器上测,而不是 Robolectric:实测(V47 WP-5 控制实验)
 * 同一枚 `ModevInputSheet` 确认按钮,Robolectric 下 `performClick` 后
 * `onSubmit` 与 `onDismiss` 均零触发(弹层内容在语义树里可见,但手势不送达),
 * 而真机正常 —— 即「点了画什么」这一层在 JVM 档**不可证**。
 * 故本包按证据分工:`ProfileEditorRenderRoboTest` 测一屏画什么(41 行 / 三色对账 /
 * 生效组 / 错误点名),本类测点得动(输入闸 → 落值 → 保存 → 回库可见)。
 * 两侧合起来才是完整证据,任何一侧单独绿都不算。
 *
 * 数据纪律:用例自带的 DIY 行按名称在前后各清一次(库是设备持久态,不留跨用例污染)。
 */
@RunWith(AndroidJUnit4::class)
class ProfileEditorFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int): String = ctx.getString(id)

    private fun profiles(): SpoofProfileService =
        (ctx.applicationContext as ModevApp).container.spoofProfiles

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    private fun tagPresent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** 本仓 Compose 测试库缺 `assertExists`,且 SemanticsNodeInteraction.fetchSemanticsNodes 是 internal ⇒ 走集合接收者。 */
    private fun assertTextOnScreen(text: String) {
        assertTrue("屏上必须找到 \"$text\"", rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    /** 视口外节点的 performClick 会被静默丢弃 ⇒ 一律经探针先滚进视口。 */
    private fun click(tag: String) {
        rule.clickScrolledIntoView(hasTestTag(tag))
        rule.waitForIdle()
    }

    private fun purgeDiy() {
        runBlocking {
            // 先补一次内置档:账本同步末尾必回读 ⇒ 内存快照确定已就位(冷进程不等 Flow)。
            profiles().synchronizeBuiltins()
            profiles().profiles.value.filter { it.name == DIY_NAME }
                .forEach { profiles().delete(it.id) }
        }
    }

    @Before
    fun setUp() {
        purgeDiy()
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        purgeDiy()
    }

    private fun openLibrary() {
        rule.openSettings(str(R.string.cd_settings))
        rule.clickScrolledIntoView(hasText(str(R.string.settings_profiles_entry)))
        rule.awaitAnchor("profiles-library-section", "点「画像库」行应进画像库页")
    }

    // ------------------------------------------------------------------ 库

    @Test
    fun library_seedsTheThreeBuiltins_asDistinctRows() {
        openLibrary()
        BuiltinDeviceProfiles.BUILTINS.forEach { spec ->
            rule.awaitAnchor(
                "profile-row-${BuiltinDeviceProfiles.idFor(spec.seedId)}",
                "内置档 ${spec.seedId} 必须各成一行",
            )
        }
        assertTextOnScreen(str(R.string.feature_profile_pixel7pro))
    }

    @Test
    fun library_rowMenu_offersExactlyThreeActions_andEditGoesThrough() {
        openLibrary()
        val id = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO)
        // 行的动作唯一入口 = 尾栏「更多」菜单(V46 R3 形态:行内零动作按钮)。
        assertCount("profile-more-$id", 1, unmerged = true)
        assertCount("profile-edit-$id", 0, unmerged = true)
        assertCount("profile-duplicate-$id", 0, unmerged = true)
        assertCount("profile-delete-$id", 0, unmerged = true)

        click("profile-more-$id")
        listOf("edit", "duplicate", "delete").forEach { action ->
            assertCount("profile-menu-$action-$id", 1)
        }
        click("profile-menu-edit-$id")
        rule.awaitAnchor("profile-identity-card", "菜单「编辑参数」必须进编辑器")
    }

    /**
     * 锚点计数。`unmerged = true` 用于挂在 ListItem 槽位内的标记
     * (尾栏「更多」按钮、行内徽章)——合并树里那些 tag 并给了父节点,直接零命中(AGENTS 实锤条)。
     */
    private fun assertCount(tag: String, expected: Int, unmerged: Boolean = false) {
        org.junit.Assert.assertEquals(
            "锚点 '$tag' 数量",
            expected,
            rule.onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().size,
        )
    }

    // ------------------------------------------------------------------ 新建 → 编辑 → 保存

    @Test
    fun createDiy_sheetToEditor_toValue_toSavedRow() {
        openLibrary()
        val rowsBefore = profiles().profiles.value.size

        click("profiles-create")
        rule.awaitAnchor("profile-create-sheet", "点「新建」应开名称输入 sheet")
        rule.onNodeWithTag("profile-create-input").performTextReplacement(DIY_NAME)
        rule.waitForIdle()
        click("profile-create-confirm")

        // 新建必须是**草稿进编辑器**:库里此时还不该有这一行(不留半途而废的空档)。
        rule.awaitAnchor("profile-identity-card", "确认后应直接进入参数编辑器")
        assertTrue("草稿阶段库里不得多出一行", profiles().profiles.value.size == rowsBefore)

        // 非法输入:确认必须被硬闸,值不得落进编辑态。
        click("profile-row-${ProfileField.IMEI.name}")
        rule.awaitAnchor("profile-input-${ProfileField.IMEI.name}", "点 IMEI 行应开输入 sheet")
        rule.onNodeWithTag("profile-input-field").performTextReplacement("123")
        rule.waitForIdle()
        click("profile-input-confirm")
        assertTrue("非法输入必须实时点名", tagPresent("profile-input-${ProfileField.IMEI.name}"))
        assertTextOnScreen(str(R.string.profile_error_imei_format))

        // 修好:确认放行 → 关 sheet → 行上回显该值。
        val imei = ProfileGenerators.randomImei(Random(23L))
        rule.onNodeWithTag("profile-input-field").performTextReplacement(imei)
        rule.waitForIdle()
        click("profile-input-confirm")
        rule.awaitUntilGone("profile-input-${ProfileField.IMEI.name}", "合法输入确认后 sheet 必须收起")
        assertTextOnScreen(imei)

        // 保存 = 落库一行,且带回执。
        click("profile-save")
        assertTextOnScreen(str(R.string.profile_saved))
        pressBack()
        rule.awaitAnchor("profiles-library-section", "保存后返回应落回画像库")
        assertTextOnScreen(DIY_NAME)
        assertTrue("库里必须真多出一行", profiles().profiles.value.size == rowsBefore + 1)
    }

    // ------------------------------------------------------------------ 槽位与布尔

    @Test
    fun slotSheet_and_boolToggle_take_effect_withoutAChain() {
        val builtinId = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO)
        openLibrary()
        click("profile-row-$builtinId")
        rule.awaitAnchor("profile-identity-card", "点内置档行应进编辑器")

        // 枚举槽:选项集由目录 validate 反推,选中即上行内标签并进生效预览。
        click("profile-row-${ProfileField.NETWORK_TYPE.name}")
        rule.awaitAnchor("profile-choice-${ProfileField.NETWORK_TYPE.name}", "槽位字段必须开单选 sheet")
        click("profile-slot-${ProfileField.NETWORK_TYPE.name}-5")
        assertTextOnScreen(str(R.string.profile_slot_network_5g))
        assertTrue("5G 组 chip 必须随输入出现", tagPresent("profile-group-${ProfileKind.PROFILE_NETWORK.name}"))

        // 布尔项:行内 Switch 零弹层;关掉即从预览消失(关 = 取消该项干预)。
        assertTrue(
            "空白通行证组开局不应在预览里",
            !tagPresent("profile-group-${ProfileKind.PROFILE_BLANK_CONTENT.name}"),
        )
        click("profile-toggle-${ProfileField.PASS_CONTACTS.name}")
        assertTrue("开关一拨即进预览", tagPresent("profile-group-${ProfileKind.PROFILE_BLANK_CONTENT.name}"))
        click("profile-toggle-${ProfileField.PASS_CONTACTS.name}")
        assertTrue("关掉即撤出预览", !tagPresent("profile-group-${ProfileKind.PROFILE_BLANK_CONTENT.name}"))
    }
}

private const val DIY_NAME = "V47 插桩画像"
