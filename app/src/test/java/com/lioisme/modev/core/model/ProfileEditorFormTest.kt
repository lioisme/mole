package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V47 T3 画像编辑器表单态:编辑文本 ↔ 模型哨兵的双向语义、校验门、脏态、
 * 就近随机与整组随机的自洽性。
 *
 * 这里锁的是一条产品承诺:**用户在校验失败时不可能保存出半坏的画像**,
 * 而「清空输入框」必须等价于「取消该项干预」(基线靠 41 个 State 加一张显式映射表
 * 维持这件事,新增字段极易漏接一侧)。
 */
class ProfileEditorFormTest {

    @Test
    fun `form mirrors the initial profile as edit text`() {
        val form = ProfileEditorForm(BuiltinDeviceProfiles.BUILTINS.first().profile)
        assertEquals("google", form.textOf(ProfileField.BRAND))
        assertEquals("34", form.textOf(ProfileField.SDK_INT))
        // 未填字段渲染为空串,而不是 "-1" 这种暴露哨兵的字面量
        assertEquals("", form.textOf(ProfileField.IMEI))
        assertEquals("", form.textOf(ProfileField.LONGITUDE))
        assertEquals("", form.textOf(ProfileField.RANDOM_OFFSET))
        assertFalse(form.isDirty())
    }

    @Test
    fun `clearing a field drops it out of the snapshot`() {
        val profile = BuiltinDeviceProfiles.BUILTINS.first().profile
        val form = ProfileEditorForm(profile)
        assertTrue(form.isFilled(ProfileField.BRAND))

        form.clear(ProfileField.BRAND)
        assertEquals("", form.textOf(ProfileField.BRAND))
        assertFalse(form.snapshot().brand.isNotEmpty())
        assertTrue(form.isDirty())
    }

    @Test
    fun `whitespace-only input is treated as cleared`() {
        val form = ProfileEditorForm()
        form.update(ProfileField.MODEL, "   ")
        assertEquals("", form.textOf(ProfileField.MODEL))
        assertTrue(form.snapshot().isEmpty)
    }

    @Test
    fun `snapshot round trips every builtin profile exactly`() {
        for (spec in BuiltinDeviceProfiles.BUILTINS) {
            val form = ProfileEditorForm(spec.profile)
            assertEquals("${spec.seedId} 表单往返失真", spec.profile, form.snapshot())
        }
    }

    @Test
    fun `invalid value is reported and blocks save, but is never silently rewritten`() {
        val form = ProfileEditorForm()
        form.update(ProfileField.IMEI, "490171581696171") // Luhn 不合法
        assertEquals(ProfileFieldError.IMEI_FORMAT, form.errorOf(ProfileField.IMEI))
        assertTrue(form.hasErrors())
        assertEquals(ProfileField.IMEI to ProfileFieldError.IMEI_FORMAT, form.firstError())
        // 契约一:表单不悄悄改写用户的输入
        assertEquals("490171581696171", form.snapshot().imei)
        // 契约二:保存被挡住
        assertFalse("有非法值时不得允许保存", form.canSave)
        // 契约三:仓库层是第二道防线(它按 isValid 原子拒绝)
        assertFalse(ProfileCodec.isValid(form.snapshot()))
    }

    @Test
    fun `out of range numeric is reported and blocks save`() {
        val form = ProfileEditorForm()
        form.update(ProfileField.DENSITY_DPI, "9999")
        assertEquals(ProfileFieldError.NUMBER_OUT_OF_RANGE, form.errorOf(ProfileField.DENSITY_DPI))
        assertEquals(9999, form.snapshot().densityDpi)
        assertFalse(form.canSave)
        assertFalse(ProfileCodec.isValid(form.snapshot()))
    }

    @Test
    fun `unparsable integer never throws`() {
        val form = ProfileEditorForm()
        ProfileField.entries.filter { it.type == ProfileValueType.INT }.forEach { field ->
            form.update(field, "abc")
            assertEquals(field.stringOf(form.snapshot()), "")
        }
    }

    @Test
    fun `filled count tracks valid entries only`() {
        val form = ProfileEditorForm()
        assertEquals(0, form.filledCount())
        form.update(ProfileField.BRAND, "google")
        form.update(ProfileField.IMEI, "bad")
        assertEquals(1, form.filledCount())
    }

    @Test
    fun `commit resets the dirty flag`() {
        val form = ProfileEditorForm()
        form.update(ProfileField.MODEL, "Pixel 7 Pro")
        assertTrue(form.isDirty())
        form.commit()
        assertFalse(form.isDirty())
        form.update(ProfileField.MODEL, "Pixel 7 Pro")
        assertFalse(form.isDirty())
    }

    @Test
    fun `loadFrom rebases both content and dirty baseline`() {
        val form = ProfileEditorForm()
        form.update(ProfileField.BRAND, "stale")
        form.loadFrom(BuiltinDeviceProfiles.BUILTINS[2].profile)
        assertEquals("samsung", form.textOf(ProfileField.BRAND))
        assertFalse(form.isDirty())
    }

    @Test
    fun `clearAll yields the empty profile and a clean preview`() {
        val form = ProfileEditorForm(BuiltinDeviceProfiles.BUILTINS.first().profile)
        form.clearAll()
        assertEquals(DeviceProfile.EMPTY, form.snapshot())
        assertEquals(emptyList<ProfileKind>(), form.previewGroups())
        assertTrue(form.isDirty())
    }

    @Test
    fun `row order comes from the catalog so the ui cannot grow a second list`() {
        val form = ProfileEditorForm()
        assertEquals(ProfileFieldCatalog.byKind, form.rowsByKind())
        assertEquals(form.fields, ProfileField.entries)
        assertEquals(41, form.fields.size)
    }

    @Test
    fun `preview groups and conflicts track edits live`() {
        val form = ProfileEditorForm()
        form.update(ProfileField.BRAND, "google")
        assertEquals(listOf(ProfileKind.PROFILE_BUILD_IDENTITY), form.previewGroups())
        assertTrue(ProfileHookPlan.adaptiveMaskShouldYield(form.snapshot()))

        form.update(ProfileField.LONGITUDE, "13.4")
        assertEquals(listOf(ProfileConflict.COORDINATE_HALF_SET), form.previewConflicts())
    }

    @Test
    fun `randomize field only accepts the declared random set`() {
        val random = Random(1L)
        val form = ProfileEditorForm()
        listOf(
            ProfileField.BOARD,
            ProfileField.HARDWARE,
            ProfileField.DENSITY_DPI,
            ProfileField.LANGUAGE,
            ProfileField.LAC,
            ProfileField.VERSION_NAME,
            ProfileField.PASS_CONTACTS,
            ProfileField.BRAND,
        ).forEach { field ->
            assertFalse("$field 不该被就近随机", form.randomizeField(field, random))
        }
        listOf(
            ProfileField.IMEI,
            ProfileField.ANDROID_ID,
            ProfileField.WIFI_MAC_ADDRESS,
            ProfileField.BATTERY_LEVEL,
        ).forEach { field ->
            assertTrue("$field 应可就近随机", form.randomizeField(field, random))
            assertTrue("$field 随机后应生效", field.isSetIn(form.snapshot()))
            assertNull("$field 随机值应合法", form.errorOf(field))
        }
    }

    @Test
    fun `fingerprint randomization also rewrites build id so the two stay consistent`() {
        val form = ProfileEditorForm(BuiltinDeviceProfiles.BUILTINS.first().profile)
        val buildIdBefore = form.textOf(ProfileField.BUILD_ID)
        assertTrue(form.randomizeField(ProfileField.FINGERPRINT, Random(3L)))
        val buildIdAfter = form.textOf(ProfileField.BUILD_ID)
        assertNotNull(buildIdAfter)
        val fingerprint = form.textOf(ProfileField.FINGERPRINT)
        assertTrue(
            "fingerprint 未携带新生成的 Build ID:$fingerprint / $buildIdAfter",
            fingerprint.contains("/$buildIdAfter/"),
        )
        assertTrue("随机后两段应仍互洽", form.errorOf(ProfileField.FINGERPRINT) == null)
    }

    @Test
    fun `randomize all yields a fully valid and self consistent profile`() {
        repeat(200) { seed ->
            val form = ProfileEditorForm()
            val written = form.randomizeAll(Random(seed.toLong()))
            assertTrue("应至少写入 20 项,实际 $written", written >= 20)
            val profile = form.snapshot()
            assertFalse("整组随机不得产出空画像", profile.isEmpty)
            assertNull("整组随机后仍有非法值:${ProfileCodec.firstInvalid(profile)}", ProfileCodec.firstInvalid(profile))
            // 三元组与 fingerprint 自洽(这是最容易被逐字段独立抽样破坏的地方)
            assertTrue(
                "fingerprint 与身份三段矛盾:${profile.fingerPrint}",
                profile.fingerPrint.startsWith("${profile.brand}/${profile.product}/${profile.device}:"),
            )
            assertTrue(
                "fingerprint 与版本世代矛盾:${profile.fingerPrint}",
                profile.fingerPrint.contains(":${profile.androidVersion}/${profile.buildId}/"),
            )
            // V48:配对判据改钉「与唯一事实源一致」而非一个魔法区间(旧断言 sdkInt in 33..35
            // 只证没跑飞,不证两段是同一代 —— 而第二份世代表正是本次清掉的东西)。
            assertEquals(
                "release 与 SDK 不符 Android 公开表:${profile.androidVersion}/${profile.sdkInt}",
                ProfileValueSets.sdkForRelease(profile.androidVersion),
                profile.sdkInt.toString(),
            )
            assertTrue(
                "随机池只能取近期世代",
                ProfileValueSets.recentAndroidReleases().contains(profile.androidVersion),
            )
        }
    }

    @Test
    fun `randomize all never touches the excluded families`() {
        val form = ProfileEditorForm()
        form.randomizeAll(Random(9L))
        val snapshot = form.snapshot()
        listOf(
            ProfileField.BOARD,
            ProfileField.HARDWARE,
            ProfileField.DENSITY_DPI,
            ProfileField.LANGUAGE,
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
            assertFalse("$field 不应被一键随机改写", field.isSetIn(snapshot))
        }
    }

    @Test
    fun `random source is injected so the device catalog can replace it later`() {
        val custom = object : ProfileRandomSource {
            override fun brandAt(index: Int) = BrandProfileSample("Acme", "Acme Inc", "A1", "acme1")
            override fun brandCount() = 1
            override fun carrierAt(index: Int) = CarrierProfileSample("310260", "T-Mobile", "US")
            override fun carrierCount() = 1
        }
        val form = ProfileEditorForm(samples = custom)
        form.randomizeAll(Random(42L))
        val profile = form.snapshot()
        assertEquals("Acme", profile.brand)
        assertEquals("acme1", profile.device)
        assertEquals("310260", profile.simOperator)
        assertEquals("T-Mobile", profile.simOperatorName)
        assertEquals("US", profile.simCountry)
    }

    @Test
    fun `built in random source is derived from the builtin profiles not a copy`() {
        assertEquals(
            BuiltinDeviceProfiles.BUILTINS.map { it.profile.brand },
            (0 until BuiltInProfileRandomSource.brandCount())
                .map { BuiltInProfileRandomSource.brandAt(it).brand },
        )
        assertEquals(BuiltinDeviceProfiles.BUILTINS.size, BuiltInProfileRandomSource.brandCount())
    }

    @Test
    fun `same seed reproduces the same form state`() {
        val a = ProfileEditorForm().apply { randomizeAll(Random(7L)) }
        val b = ProfileEditorForm().apply { randomizeAll(Random(7L)) }
        assertEquals(a.snapshot(), b.snapshot())
    }
}
