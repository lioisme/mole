package com.lioisme.modev

import androidx.compose.ui.test.assert
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
import com.lioisme.modev.core.model.DeviceProfile
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
    private fun assertTextAbsent(text: String) {
        assertTrue("屏上不得再有 \"$text\"", !rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertTextOnScreen(text: String) {
        assertTrue("屏上必须找到 \"$text\"", rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty())
    }

    /** 视口外节点的 performClick 会被静默丢弃 ⇒ 一律经探针先滚进视口。 */
    private fun click(tag: String) {
        rule.clickScrolledIntoView(hasTestTag(tag))
        rule.waitForIdle()
    }

    /**
     * 选择 sheet 的列表是 LazyColumn(只组合视口内行,V48 选型库把整表
     * verticalScroll 换成懒列表):目标行可能压根没组合,`performScrollTo` 对未组合
     * 节点零办法 ⇒ 先按关键词过滤再点 —— 这也正是产品给长名单设计的通路(搜索框 +
     * 「请缩小搜索范围」截断提示),测试走同一条路才不掩盖真实行为。
     */
    private fun filterSheet(query: String) {
        rule.awaitAnchor("sheet-search-input", "选择 sheet 必须自带搜索框")
        rule.onNodeWithTag("sheet-search-input").performTextReplacement(query)
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

    /** 在库里落一行(经契约写入口,不走 UI),返回主键。 */
    private fun seedProfile(profile: DeviceProfile): String = runBlocking {
        requireNotNull(profiles().create("点选档", "", profile)).id
    }

    private fun openEditor(id: String) {
        openLibrary()
        click("profile-row-$id")
        rule.awaitAnchor("profile-identity-card", "点画像行应进参数编辑器")
    }

    /**
     * 机型库整条点选通路(V47 WP-5b):行 → 品牌 sheet → 型号 sheet → 四项身份同时落值,
     * 且原有 PRODUCT 不被猜写(库里没有该项)。
     */
    @Test
    fun devicePreset_twoTaps_fillTheIdentityCoherently() {
        val id = seedProfile(
            DeviceProfile(model = "keep-me", product = "raven", board = "raven"),
        )
        openEditor(id)

        click("profile-device-pick")
        rule.awaitAnchor("profile-device-brand-sheet", "点「选择真实机型」应开品牌 sheet")
        filterSheet("xiaomi")
        click("profile-device-brand-xiaomi")
        rule.awaitAnchor("profile-device-model-sheet", "选完品牌应进型号 sheet")
        filterSheet("2211133C")
        click("profile-device-model-2211133C")

        assertTextOnScreen("2211133C")
        assertTextOnScreen("fuxi")
        assertTextOnScreen("Xiaomi")
        assertTextOnScreen("raven")
        // 手填的原型号必须被整组换掉:它不在屏上才是「四项同时落值」的证据
        assertTextAbsent("keep-me")
    }

    /**
     * V48 C2 设备档:「选择 Android 版本」必须一次写全 release + SDK 两个字段
     * (可分开可写 = 允许存出 15/34 这种自相矛盾画像,#31⑤)。
     */
    @Test
    fun androidVersionPicking_writesReleaseAndSdkTogether() {
        val id = seedProfile(DeviceProfile(model = "keep-me"))
        openEditor(id)

        click("profile-android-pick")
        rule.awaitAnchor("profile-android-sheet", "「选择 Android 版本」行必须开版本 sheet")
        filterSheet("15")
        click("profile-android-option-15")
        rule.waitForIdle()

        rule.onNodeWithTag("profile-row-${ProfileField.ANDROID_VERSION.name}")
            .assert(hasText("15", substring = true))
        rule.onNodeWithTag("profile-row-${ProfileField.SDK_INT.name}")
            .assert(hasText("35", substring = true))
    }

    /**
     * V48 C3 设备档:运营商一行选完 ⇒ PLMN + 名 + 国码三项同落(1597 条走懒列表,
     * 目标行只能经 sheet 自带搜索框抵达 —— 这条通路本身就是产品给长名单的答复)。
     */
    @Test
    fun carrierPicking_fillsPlmnNameAndCountryAtOnce() {
        val id = seedProfile(DeviceProfile(model = "keep-me"))
        openEditor(id)

        click("profile-carrier-pick")
        rule.awaitAnchor("profile-carrier-sheet", "「选择真实运营商」行必须开运营商 sheet")
        filterSheet("46000")
        click("profile-carrier-option-46000")
        rule.waitForIdle()

        rule.onNodeWithTag("profile-row-${ProfileField.SIM_OPERATOR.name}")
            .assert(hasText("46000", substring = true))
        rule.onNodeWithTag("profile-row-${ProfileField.SIM_OPERATOR_NAME.name}")
            .assert(hasText("China Mobile", substring = true))
        rule.onNodeWithTag("profile-row-${ProfileField.SIM_COUNTRY.name}")
            .assert(hasText("CN", substring = true))
    }

    /** 枚举字段(语言)选择即写值;sheet 内「改为手动填写」仍能落到文本输入通路。 */
    @Test
    fun enumeratedField_pickerSetsValue_andManualEscapeWorks() {
        // 库里不留空行是契约(#30③:create 对零字段正文恒返回 null)⇒ 夹具必须带一项。
        val id = seedProfile(DeviceProfile(model = "keep-me"))
        openEditor(id)

        click("profile-row-${ProfileField.LANGUAGE.name}")
        rule.awaitAnchor("profile-enum-LANGUAGE", "语言行应开枚举 sheet(而非文本输入)")
        filterSheet("ja")
        click("profile-option-LANGUAGE-ja")
        assertTextOnScreen("ja")

        click("profile-row-${ProfileField.LANGUAGE.name}")
        click("profile-option-LANGUAGE-manual")
        rule.awaitAnchor("profile-input-LANGUAGE", "选「改为手动填写」必须落到文本输入 sheet")
    }

    // ------------------------------------------------------------------ 库

    @Test
    fun library_seedsEveryBuiltin_asDistinctRows() {
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
