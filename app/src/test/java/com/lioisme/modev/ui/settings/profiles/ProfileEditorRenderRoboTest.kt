package com.lioisme.modev.ui.settings.profiles

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.lioisme.modev.R
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileConflict
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileOptionSource
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.core.model.ProfileValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 画像参数编辑器的**整屏组合渲染**回归(V47 WP-5,Robolectric 全装配驱动)。
 *
 * 本类只测「屏画什么」,不测「点了画什么」:实测确认 Robolectric 里
 * `ModalBottomSheet` 内容能被语义树看到(故锚点存在性可断言),但打在弹层按钮上的
 * 手势不会送达(`performClick` 后表单零变化、弹层不关),与真机行为不同。
 * 因此**点击 → sheet → 校验 → 落值**这条主通路归 androidTest
 * (`ProfileEditorFlowTest`,真机注入档),本类用 VM 写入面驱动同一批呈现判定 ——
 * 两侧合起来才是完整证据,任何一侧单独绿都不算。
 *
 * 这条防线覆盖的是纯派生测试([ProfileEditorStateTest])与栈语义测试
 * ([ProfileBackStackRoboTest])都够不到的一层:第一次把 41 行真画出来,
 * 因而能抓到只在组合期炸的形态(占位符与资源参数量不匹配 = `McpTokensScreenStateTest`
 * 记过的整屏闪退同型)、槽标签反查落空、分区与行序漂移。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileEditorRenderRoboTest {

    @get:Rule
    val compose = createComposeRule()

    private fun str(res: Int, vararg args: Any?): String =
        RuntimeEnvironment.getApplication().getString(res, *args)

    private fun editorVm(
        store: FakeSpoofProfileStore,
        profileId: String = "id-1",
        draftName: String? = null,
    ) = ProfileEditorViewModel(
        profileId = profileId,
        draftName = draftName,
        profiles = store,
        deviceCatalog = FakeDeviceCatalog(FakeDeviceCatalog.READY),
        carrierCatalog = FakeCarrierCatalog(FakeCarrierCatalog.READY),
    )

    /**
     * 库里恒带一行 `id-1`:编辑器的「条目已不在库里 ⇒ 弹栈」是**产品行为**,
     * 空库只会测到那条弹栈分支(屏上一片空,断言全成假绿)。
     */
    private fun storeWith(
        profile: DeviceProfile = DeviceProfile.EMPTY,
        builtinSeedId: String? = null,
    ) = FakeSpoofProfileStore(
        listOf(entry(id = "id-1", profile = profile, builtinSeedId = builtinSeedId)),
    )

    private fun entry(id: String, profile: DeviceProfile, builtinSeedId: String? = null) =
        DeviceProfileEntry(id = id, name = "已存画像", summary = "", profile = profile, builtinSeedId = builtinSeedId)

    private fun showEditor(vm: ProfileEditorViewModel) {
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProfileEditorScreen(viewModel = vm, onBack = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun tagCount(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun assertTagPresent(tag: String, why: String) {
        assertTrue("$why(锚点 $tag)", tagCount(tag) > 0)
    }

    private fun assertTagAbsent(tag: String, why: String) {
        assertEquals("$why(锚点 $tag 不应在屏上)", 0, tagCount(tag))
    }

    private fun assertTextPresent(text: String, why: String) {
        assertTrue(
            "$why(期望文本 \"$text\")",
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // ------------------------------------------------------------------ 一屏结构

    @Test
    fun everyParameter_rendersExactlyOneRowInItsSection() {
        showEditor(editorVm(storeWith()))

        // V49 R-1:分区 ≡ 底层来源族 ⇒ 20 段;且**标题必须把来源 API 印在屏上**
        // (分类若只活在枚举名里,用户仍然要凭记忆判断这一组改的是哪个子系统)。
        val app = RuntimeEnvironment.getApplication()
        assertEquals("分区数即类别数", 20, ProfileKind.entries.size)
        ProfileKind.entries.forEach { kind ->
            assertTagPresent("profile-section-${kind.name}", "分区必须成段渲染")
            assertTextPresent(
                app.getString(
                    profileSectionTitleRes(),
                    app.getString(profileKindLabelRes(kind)),
                    kind.sourceApi,
                ),
                "分区标题须点名来源 API",
            )
        }
        ProfileField.entries.forEach { field ->
            val tag = if (field.type == ProfileValueType.BOOL) {
                "profile-toggle-${field.name}"
            } else {
                "profile-row-${field.name}"
            }
            assertEquals("参数 ${field.name} 必须恰有一行(一行一参数)", 1, tagCount(tag))
        }
        // 空项显式标「未设置」而非留白:用户须能区分「没填」与「填了个空」。
        assertTextPresent(str(R.string.profile_unset), "未填项必须显式标注")
    }

    @Test
    fun builtinRow_editingIt_isDeclaredOnScreen() {
        val pixel = DeviceProfile(brand = "google", model = "Pixel 7 Pro", product = "raven", device = "raven")
        showEditor(editorVm(storeWith(profile = pixel, builtinSeedId = "pixel-7-pro")))
        assertTagPresent("profile-builtin-hint", "编辑内置档必须内联说明脱离账本的后果")
        // 展示名取 seedId 资源,不是存储里的空串。
        assertTextPresent(str(R.string.feature_profile_pixel7pro), "内置档展示名必须走四语资源")
    }

    @Test
    fun draft_rendersWithoutABuiltinWarning() {
        // 草稿在库里没有行:目的地侧不得弹栈,也不得挂内置提示。
        var bounced = false
        compose.setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    ProfileEditorScreen(
                        viewModel = editorVm(FakeSpoofProfileStore(), profileId = "draft:我的档", draftName = "我的档"),
                        onBack = { bounced = true },
                    )
                }
            }
        }
        compose.waitForIdle()
        assertFalse("草稿态不得被当成缺失条目弹栈", bounced)
        assertTagPresent("profile-identity-card", "草稿同样要画出整屏")
        assertTagAbsent("profile-builtin-hint", "草稿不是内置档")
    }

    // ------------------------------------------------------------------ 派生态上屏

    @Test
    fun identityCard_reportsTripleAgainstFingerprint_inThreeStates() {
        val vm = editorVm(
            storeWith(
                profile = DeviceProfile(brand = "google", model = "Pixel 7 Pro", product = "raven", device = "raven"),
            ),
        )
        showEditor(vm)

        assertTagPresent("profile-identity-check", "身份卡必须有对账行")
        assertTextPresent(str(R.string.profile_identity_unknown), "要素不全时报「尚不可判」而非矛盾")

        vm.update(
            ProfileField.FINGERPRINT,
            "google/raven/raven:14/UP1A.231005.00B/10763597:user/release-keys",
        )
        compose.waitForIdle()
        assertTextPresent(str(R.string.profile_identity_ok), "前缀与三元组一致时报一致")

        vm.update(ProfileField.PRODUCT, "tokay")
        compose.waitForIdle()
        assertTextPresent(
            str(R.string.profile_identity_mismatch, "google/raven/raven", "google/tokay/raven"),
            "改产品名后必须点名两侧前缀",
        )
    }

    @Test
    fun invalidInput_namesTheFieldOnItsRowAndDisablesSave() {
        val vm = editorVm(storeWith())
        showEditor(vm)

        vm.update(ProfileField.IMEI, "123")
        compose.waitForIdle()

        assertTextPresent(
            str(R.string.profile_invalid_field, str(R.string.profile_field_imei), str(R.string.profile_error_imei_format)),
            "保存闸必须点名「哪一项 + 为什么」",
        )
        assertTextPresent(str(R.string.profile_error_imei_format), "该行必须带错误说明")
        assertTagPresent("profile-save-invalid", "非法输入时底栏切到错误态")
        // 摘要态(「N 项已填」)让位给错误行:两个形态互斥,同屏不得并存。
        assertEquals(0, tagCount("profile-save-summary"))
    }

    @Test
    fun slotValue_rendersItsLabel_notTheRawNumber() {
        val vm = editorVm(storeWith(profile = DeviceProfile(networkType = 5, screenshotsFlag = 0)))
        showEditor(vm)

        assertTextPresent(str(R.string.profile_slot_network_5g), "5G 槽必须显示槽标签")
        assertTextPresent(str(R.string.profile_slot_screenshot_deny), "截图禁用槽必须显示槽标签")
        assertTextPresent(str(R.string.profile_kind_network), "生效组预览")
    }

    // ------------------------------------------------------------------ 机型库选择(V47 WP-5b)

    @Test
    fun devicePicking_fillsFourIdentityFields_andNeverGuessesProduct() {
        val vm = editorVm(storeWith())
        showEditor(vm)
        assertTagPresent("profile-device-pick", "设备分区首行必须是机型库选择行")

        vm.pickDevice("xiaomi", "2211133C:fuxi")
        compose.waitForIdle()

        val texts = vm.view.value.texts
        assertEquals("Xiaomi", texts[ProfileField.BRAND])
        assertEquals("Xiaomi", texts[ProfileField.MANUFACTURER])
        assertEquals("2211133C", texts[ProfileField.MODEL])
        assertEquals("fuxi", texts[ProfileField.DEVICE])
        // 机型库不提供 PRODUCT/BOARD/HARDWARE ⇒ 编一个「像真的」的猜测值就是自造矛盾画像。
        assertEquals("PRODUCT 不得被猜出来", "", texts[ProfileField.PRODUCT])
        assertEquals("BOARD 不得被猜出来", "", texts[ProfileField.BOARD])
        // 选择必须一次成型:生效组预览随之出现。
        assertTagPresent("profile-group-${ProfileKind.PROFILE_BUILD_IDENTITY.name}", "选完机型即装 Build 组")
    }

    @Test
    fun unavailableCatalog_isNamedOnTheRow_andNeverOpensAnEmptySheet() {
        val vm = ProfileEditorViewModel(
            profileId = "id-1",
            draftName = null,
            profiles = storeWith(),
            deviceCatalog = FakeDeviceCatalog(null),
            carrierCatalog = FakeCarrierCatalog(FakeCarrierCatalog.READY),
        )
        showEditor(vm)
        assertEquals(DeviceCatalogState.Unavailable, vm.catalog.value)
        assertTextPresent(
            str(R.string.profile_catalog_unavailable, str(R.string.profile_catalog_devices)),
            "读不到资产必须点名,不得退化成「库里没机型」",
        )
    }

    // ------------------------------------------- 成对选型(V48 R-3:Android 版本 / 运营商)

    /** 选 Android 版本必须把 release 与 API level **一起**写全(只写一半 = 一台不存在的设备)。 */
    @Test
    fun androidVersionPicking_fillsReleaseAndSdkTogether() {
        val vm = editorVm(storeWith())
        showEditor(vm)
        assertTagPresent("profile-android-pick", "设备分区必须有 Android 版本选择行")

        vm.pickAndroidRelease("14")
        compose.waitForIdle()

        val texts = vm.view.value.texts
        assertEquals("14", texts[ProfileField.ANDROID_VERSION])
        assertEquals("API level 必须同次写全", "34", texts[ProfileField.SDK_INT])
    }

    /** 认不出的 release 不许猜半边(整动作放弃,屏上值保持原样)。 */
    @Test
    fun unknownAndroidRelease_writesNothing() {
        val vm = editorVm(storeWith())
        showEditor(vm)
        vm.pickAndroidRelease("14")
        vm.pickAndroidRelease("99")
        compose.waitForIdle()
        val texts = vm.view.value.texts
        assertEquals("14", texts[ProfileField.ANDROID_VERSION])
        assertEquals("34", texts[ProfileField.SDK_INT])
    }

    /**
     * 选运营商:PLMN + 名 + 国家码一次写全;而数据集里真实存在的多地区国家码串
     * (`AU/CC/CX`)必须**只留空国家码** —— 拆一个两字母凑就是自造值。
     */
    @Test
    fun carrierPicking_fillsThreeFields_andNeverGuessesAMultiRegionCountryCode() {
        val vm = editorVm(storeWith())
        showEditor(vm)
        assertTagPresent("profile-carrier-pick", "SIM 分区首行必须是运营商选择行")

        vm.pickCarrier("46000")
        compose.waitForIdle()
        var texts = vm.view.value.texts
        assertEquals("46000", texts[ProfileField.SIM_OPERATOR])
        assertEquals("China Mobile", texts[ProfileField.SIM_OPERATOR_NAME])
        assertEquals("CN", texts[ProfileField.SIM_COUNTRY])

        vm.pickCarrier("50501")
        compose.waitForIdle()
        texts = vm.view.value.texts
        assertEquals("50501", texts[ProfileField.SIM_OPERATOR])
        assertEquals("Telstra", texts[ProfileField.SIM_OPERATOR_NAME])
        assertEquals("多地区国家码不得进画像", "", texts[ProfileField.SIM_COUNTRY])
    }

    /** 运营商库读不到时同样要上屏点名(与机型库同一红线)。 */
    @Test
    fun unavailableCarrierCatalog_isNamedOnTheRow() {
        val vm = ProfileEditorViewModel(
            profileId = "id-1",
            draftName = null,
            profiles = storeWith(),
            deviceCatalog = FakeDeviceCatalog(FakeDeviceCatalog.READY),
            carrierCatalog = FakeCarrierCatalog(null),
        )
        showEditor(vm)
        assertEquals(CarrierChoiceState.Unavailable, vm.carriers.value)
        assertTextPresent(
            str(R.string.profile_catalog_unavailable, str(R.string.profile_catalog_carriers)),
            "读不到运营商库必须点名",
        )
    }

    /**
     * 密度档 / 核数档改走选择器(V48 R-3):本档只证「候选表已挂上且逐值合法」——
     * 「点开确实出列表」这一层在 Robolectric 打不动弹层手势,归插桩档
     * `ProfileEditorFlowTest.pickerSheets_openWithManualEscape`。
     */
    @Test
    fun densityAndCoreFields_nowHaveCandidateTables() {
        assertTrue(
            "密度档必须有候选表",
            checkNotNull(ProfileOptionSource.optionsFor(ProfileField.DENSITY_DPI)).contains("420"),
        )
        assertTrue(
            "核数档必须有候选表",
            checkNotNull(ProfileOptionSource.optionsFor(ProfileField.CPU_CORES)).contains("8"),
        )
    }

    @Test
    fun groupsAndConflicts_surfaceLiveFromTheCompiledPlan() {
        val vm = editorVm(storeWith())
        showEditor(vm)
        assertTagPresent("profile-groups-empty", "全空时必须明说「什么都不会装」")

        vm.update(ProfileField.LONGITUDE, "12.34")
        compose.waitForIdle()
        assertTagPresent("profile-group-${ProfileKind.PROFILE_LOCATION.name}", "坐标组 chip 随输入出现")
        assertTagPresent(
            "profile-conflict-${ProfileConflict.COORDINATE_HALF_SET.name}",
            "只填一半必须内联点名矛盾(不弹阻断式对话框)",
        )

        vm.writeBool(ProfileField.PASS_CONTACTS, true)
        compose.waitForIdle()
        assertTagPresent("profile-group-${ProfileKind.PROFILE_BLANK_CONTENT.name}", "布尔项一拨即进预览")

        vm.writeBool(ProfileField.PASS_CONTACTS, false)
        compose.waitForIdle()
        assertTagAbsent("profile-group-${ProfileKind.PROFILE_BLANK_CONTENT.name}", "关掉即从预览消失(关 = 取消该项干预)")
    }
}
