package com.lioisme.modev.ui.settings.profiles

import com.lioisme.modev.core.model.BuiltinDeviceProfiles
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.core.model.ProfileResolution
import com.lioisme.modev.core.model.ProfileValues
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画像编辑器**可视态派生**的 JVM 回归(V47 WP-5)。
 *
 * 覆盖的是「屏要画什么」这一层的判定,而不是渲染:身份一致性三色态、生效组实时预览、
 * 保存闸(脏 + 合法 + 至少一项)、整组随机的同源性与排除族、清空、
 * 以及每应用绑定的三态里 **Missing 必须与 Unbound 可区分**(GAP-02 的静默回落红线)。
 *
 * 随机源以固定种子注入 ⇒ 结论可复现(不断言具体随机值,只断言「哪些族被写、哪些恒空」)。
 */
class ProfileEditorStateTest {

    private fun state() = ProfileEditorState(randomProvider = { Random(7L) })

    // ---------------------------------------------------------------- 身份一致性

    @Test
    fun identity_isConsistent_whenFingerprintPrefixMatchesTheTriple() {
        val s = state()
        s.update(ProfileField.BRAND, "google")
        s.update(ProfileField.PRODUCT, "raven")
        s.update(ProfileField.DEVICE, "raven")
        s.update(ProfileField.FINGERPRINT, "google/raven/raven:14/UP1A.231005.00B/10763597:user/release-keys")
        val preview = s.identity()
        assertEquals(ProfileIdentityCheck.Consistent, preview.check)
        assertEquals("google/raven/raven", preview.fingerprintPrefix)
        assertEquals("google/raven/raven", preview.triplePrefix)
    }

    @Test
    fun identity_isMismatched_whenFingerprintBelongsToAnotherDevice() {
        val s = state()
        s.update(ProfileField.BRAND, "google")
        s.update(ProfileField.PRODUCT, "raven")
        s.update(ProfileField.DEVICE, "raven")
        s.update(ProfileField.FINGERPRINT, "Xiaomi/fuxi/fuxi:14/UKQ1.230804.001/V816:user/release-keys")
        assertEquals(ProfileIdentityCheck.Mismatched, s.identity().check)
    }

    @Test
    fun identity_isUnknown_untilBothSidesComplete() {
        val empty = state()
        assertEquals(ProfileIdentityCheck.Unknown, empty.identity().check)

        val onlyTriple = state()
        onlyTriple.update(ProfileField.BRAND, "samsung")
        onlyTriple.update(ProfileField.PRODUCT, "d2sxx")
        onlyTriple.update(ProfileField.DEVICE, "d2s")
        assertEquals(ProfileIdentityCheck.Unknown, onlyTriple.identity().check)

        val malformedFingerprint = state()
        malformedFingerprint.update(ProfileField.BRAND, "samsung")
        malformedFingerprint.update(ProfileField.PRODUCT, "d2sxx")
        malformedFingerprint.update(ProfileField.DEVICE, "d2s")
        malformedFingerprint.update(ProfileField.FINGERPRINT, "samsung d2sxx d2s 14 UP1A user")
        assertEquals(ProfileIdentityCheck.Unknown, malformedFingerprint.identity().check)
    }

    @Test
    fun identity_showsTheModelHeadlineAndStaysEmptyWhenNothingIsSet() {
        assertTrue(state().identity().isEmpty)
        val s = state()
        s.update(ProfileField.MODEL, "Pixel 7 Pro")
        assertEquals("Pixel 7 Pro", s.identity().model)
        assertFalse(s.identity().isEmpty)
    }

    // ---------------------------------------------------------------- 生效组预览

    @Test
    fun groups_appearLiveAsSoonAsAnIdentityPairIsFilled() {
        val s = state()
        assertEquals(emptyList<ProfileKind>(), s.groups())
        s.update(ProfileField.BRAND, "google")
        assertEquals(listOf(ProfileKind.PROFILE_BUILD), s.groups())
        s.update(ProfileField.DENSITY_DPI, "384")
        assertEquals(listOf(ProfileKind.PROFILE_BUILD, ProfileKind.PROFILE_DENSITY), s.groups())
    }

    @Test
    fun groups_hidePresesSpoofGroupInPreview() {
        // 预览必须走计划编译器,而不是「填了几项就报几组」—— 否则屏上广告的是不会装的钩子。
        val s = state()
        s.update(ProfileField.WIFI_SSID, "modev-ap")
        s.update(ProfileField.WIFI_LOCATION_HIDE, "true")
        val groups = s.groups()
        assertTrue(groups.contains(ProfileKind.PROFILE_WIFI_LOCATION_HIDE))
        assertFalse(groups.contains(ProfileKind.PROFILE_WIFI))
    }

    @Test
    fun conflicts_surfaceHalfCoordinatesWithoutBlocking() {
        val s = state()
        s.update(ProfileField.LONGITUDE, "12.34")
        assertEquals(1, s.conflicts().size)
        s.update(ProfileField.LATITUDE, "41.9")
        assertTrue(s.conflicts().isEmpty())
    }

    // ---------------------------------------------------------------- 保存闸

    @Test
    fun canSave_requiresDirtyAndAtLeastOneValidField() {
        val s = state()
        assertFalse("空表无改动不可存", s.canSave)
        s.update(ProfileField.MODEL, "Pixel 7 Pro")
        assertTrue(s.canSave)
        assertEquals(1, s.filledCount())
        s.clear(ProfileField.MODEL)
        assertFalse("改回全空 = 零生效项,无可伪装内容", s.canSave)
    }

    @Test
    fun canSave_blocksOnInvalidInputAndNamesTheField() {
        val s = state()
        s.update(ProfileField.IMEI, "123")
        assertFalse(s.canSave)
        val first = s.firstError()
        assertNotNull(first)
        assertEquals(ProfileField.IMEI, first!!.first)
        // 修好即放行:随机源产出的值恒合法且生效(闸门不残留旧错误态)。
        assertTrue(s.randomizeField(ProfileField.IMEI))
        assertNull(s.firstError())
        assertTrue(s.canSave)
    }

    @Test
    fun commit_clearsDirtySoTheSameContentCannotBeSavedTwice() {
        val s = state()
        s.update(ProfileField.MODEL, "Pixel 7 Pro")
        assertTrue("相对空基线有改动 ⇒ 脏", s.isDirty())
        s.commit()
        assertFalse(s.isDirty())
        assertFalse(s.canSave)
        s.update(ProfileField.MODEL, "Pixel 8")
        assertTrue(s.canSave)
    }

    @Test
    fun viewSnapshot_carriesTheSameNumbersTheCardsRender() {
        val s = state()
        s.update(ProfileField.MODEL, "Pixel 7 Pro")
        s.update(ProfileField.BRAND, "google")
        val view = s.toView()
        assertEquals(2, view.filledCount)
        assertEquals(s.groups(), view.groups)
        assertEquals(s.identity(), view.identity)
        assertEquals("google", view.texts[ProfileField.BRAND])
        assertNull(view.errors[ProfileField.BRAND])
        assertEquals(ProfileFieldCatalog.bySection, view.rowsBySection)
        assertEquals(ProfileField.entries.size, view.texts.size)
    }

    @Test
    fun view_isRecomputableFromTheSameStateWithoutMutation() {
        val s = state()
        s.update(ProfileField.SDK_INT, "34")
        assertEquals(s.toView(), s.toView())
    }

    // ---------------------------------------------------------------- 分派写入面

    @Test
    fun boolWrite_falseIsStoredAsUnsetSoTheGroupGoesAway() {
        val s = state()
        s.writeBool(ProfileField.PASS_CONTACTS, true)
        assertEquals("true", s.textOf(ProfileField.PASS_CONTACTS))
        assertTrue(s.groups().contains(ProfileKind.PROFILE_BLANK_CONTENT))
        s.writeBool(ProfileField.PASS_CONTACTS, false)
        assertEquals("", s.textOf(ProfileField.PASS_CONTACTS))
        assertFalse(s.groups().contains(ProfileKind.PROFILE_BLANK_CONTENT))
    }

    @Test
    fun slotWrite_unhookSlotNormalisesToUnsetSoBothCountersAgree() {
        val s = state()
        s.writeSlot(ProfileField.NETWORK_TYPE, ProfileValues.NET_MOBILE_5G)
        assertEquals("5", s.textOf(ProfileField.NETWORK_TYPE))
        assertEquals(1, s.filledCount())
        s.writeSlot(ProfileField.NETWORK_TYPE, ProfileValues.NET_UNHOOK)
        assertEquals("选中「不干预」等价于清空", "", s.textOf(ProfileField.NETWORK_TYPE))
        assertEquals(0, s.filledCount())
        assertFalse(s.isFilled(ProfileField.NETWORK_TYPE))
        s.writeSlot(ProfileField.SCREENSHOTS_FLAG, ProfileValues.SCREENSHOT_DENY)
        assertEquals("0", s.textOf(ProfileField.SCREENSHOTS_FLAG))
        assertTrue(s.groups().contains(ProfileKind.PROFILE_SCREENSHOT))
    }

    @Test
    fun acceptedSlots_areReverseDerivedFromTheCatalogValidator() {
        // 与 MCP 目录工具同一手法(扫窗留合法者),两侧不可能各抄一张表而分叉。
        assertEquals(
            listOf(-1, 0, 1, 2, 3, 4, 5),
            ProfileEditorState.acceptedSlots(ProfileField.NETWORK_TYPE),
        )
        assertEquals(
            listOf(-1, 0, 1),
            ProfileEditorState.acceptedSlots(ProfileField.SCREENSHOTS_FLAG),
        )
        assertEquals(emptyList<Int>(), ProfileEditorState.acceptedSlots(ProfileField.BRAND))
        ProfileEditorState.acceptedSlots(ProfileField.NETWORK_TYPE).forEach { slot ->
            assertNull(
                "反推出的槽位必须被目录接受:$slot",
                ProfileFieldCatalog.validate(ProfileField.NETWORK_TYPE, slot.toString()),
            )
        }
    }

    // ---------------------------------------------------------------- 随机

    @Test
    fun randomizeAll_fillsCoherentIdentityAndLeavesExcludedFamiliesUnset() {
        val s = state()
        val written = s.randomizeAll()
        assertTrue(written > 0)
        // 整组派生恒自洽:指纹前缀 == 三元组(这是「可视化配置」最有价值的一条保证)。
        assertEquals(ProfileIdentityCheck.Consistent, s.identity().check)
        listOf(
            ProfileField.BRAND,
            ProfileField.MANUFACTURER,
            ProfileField.MODEL,
            ProfileField.PRODUCT,
            ProfileField.DEVICE,
            ProfileField.BUILD_ID,
            ProfileField.FINGERPRINT,
            ProfileField.ANDROID_VERSION,
            ProfileField.SDK_INT,
            ProfileField.NETWORK_TYPE,
            ProfileField.WIFI_SSID,
            ProfileField.WIFI_BSSID,
            ProfileField.WIFI_MAC_ADDRESS,
            ProfileField.SIM_OPERATOR,
            ProfileField.SIM_OPERATOR_NAME,
            ProfileField.SIM_COUNTRY,
            ProfileField.ANDROID_ID,
            ProfileField.IMEI,
            ProfileField.PHONE_NUM,
            ProfileField.LONGITUDE,
            ProfileField.LATITUDE,
            ProfileField.BATTERY_LEVEL,
        ).forEach { field ->
            assertTrue("整组随机应写入 $field", s.isFilled(field))
            assertNull("随机源不得产出非法值:$field", s.errorOf(field))
        }
        // 恒不参与的族必须留在未填(它们是「现场属性」或「机型固定属性」,不是随机对象)。
        listOf(
            ProfileField.BOARD,
            ProfileField.HARDWARE,
            ProfileField.DENSITY_DPI,
            ProfileField.CPU_CORES,
            ProfileField.LANGUAGE,
            ProfileField.TIME_ZONE,
            ProfileField.LAC,
            ProfileField.CID,
            ProfileField.VERSION_CODE,
            ProfileField.VERSION_NAME,
            ProfileField.SCREENSHOTS_FLAG,
            ProfileField.RANDOM_OFFSET,
            ProfileField.WIFI_LOCATION_HIDE,
            ProfileField.CELL_LOCATION_HIDE,
            ProfileField.PASS_CONTACTS,
            ProfileField.PASS_PHOTO,
            ProfileField.PASS_VIDEO,
            ProfileField.PASS_AUDIO,
            ProfileField.PASS_APPLICATIONS,
        ).forEach { field ->
            assertFalse("整组随机越界写入了 $field", s.isFilled(field))
        }
        assertFalse(s.groups().contains(ProfileKind.PROFILE_DENSITY))
        assertFalse(s.groups().contains(ProfileKind.PROFILE_BLANK_CONTENT))
        assertTrue("坐标齐备 ⇒ 无半配置矛盾", s.conflicts().isEmpty())
    }

    @Test
    fun randomizeField_onlyWorksForCatalogRandomizableFields() {
        val s = state()
        assertFalse(s.randomizeField(ProfileField.BOARD))
        assertEquals("", s.textOf(ProfileField.BOARD))
        assertTrue(s.randomizeField(ProfileField.IMEI))
        assertNull(s.errorOf(ProfileField.IMEI))
        assertTrue(s.isFilled(ProfileField.IMEI))
    }

    @Test
    fun clearAll_resetsEveryParameterAndTheGroupPreview() {
        val s = state()
        // 脏 = 「与载入基线不同」,不是「内容非空」⇒ 必须从一份真实基线出发才测得到
        // 「清空整组」是一次可保存的改动(新建空档后清空 = 回到基线,本就不该脏)。
        s.loadFrom(requireNotNull(BuiltinDeviceProfiles.specFor(BuiltinDeviceProfiles.PIXEL_7_PRO)).profile)
        assertFalse("载入即新基线,此时无改动", s.isDirty())
        s.randomizeAll()
        s.clearAll()
        assertEquals(0, s.filledCount())
        assertEquals(emptyList<ProfileKind>(), s.groups())
        assertTrue("清空相对载入的内置档是一次改动(基线非空)", s.isDirty())
        assertFalse("全空不可存", s.canSave)
        ProfileField.entries.forEach { assertEquals("", s.textOf(it)) }
    }

    @Test
    fun clearAll_onAFreshFormIsNotDirty_becauseTheBaselineIsAlreadyEmpty() {
        val s = state()
        s.randomizeAll()
        s.clearAll()
        assertEquals(0, s.filledCount())
        assertFalse("回到空基线 ⇒ 无改动可保存", s.isDirty())
    }

    // ---------------------------------------------------------------- 绑定三态

    @Test
    fun bindingDisplay_keepsUnboundAndMissingApart() {
        assertEquals(ProfileBindingDisplay.Unbound, ProfileResolution.Unbound.toBindingDisplay())
        val missing = ProfileResolution.Missing("deleted-id").toBindingDisplay()
        assertTrue(missing is ProfileBindingDisplay.Missing)
        assertEquals("deleted-id", (missing as ProfileBindingDisplay.Missing).id)
    }

    @Test
    fun bindingDisplay_boundCarriesSeedIdSoBuiltinNamesStayLocalized() {
        val entry = DeviceProfileEntry(
            id = "builtin:pixel-7-pro",
            name = "",
            summary = "",
            profile = DeviceProfile(brand = "google", model = "Pixel 7 Pro"),
            builtinSeedId = BuiltinDeviceProfiles.PIXEL_7_PRO,
        )
        val display = ProfileResolution.Bound(entry).toBindingDisplay()
        assertEquals(
            ProfileBindingDisplay.Bound(name = "", builtinSeedId = "pixel-7-pro", filledCount = 2),
            display,
        )
    }
}
