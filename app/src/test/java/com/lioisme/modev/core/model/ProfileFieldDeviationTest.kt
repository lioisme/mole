package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每应用画像覆写的合并 / 反推对账(V49 R-2 的语义地基)。
 *
 * 为什么两条方向都要钉:[apply] 决定**下发什么**,[between] 决定**屏上显示什么与存什么**。
 * 二者不是简单互逆 —— 「把画像里填过的一项清成空」必须是覆写(可表达),
 * 而「编辑文本恰好等于画像值」必须**不是**覆写(否则每开一次编辑页就长出一堆幽灵偏差,
 * 恢复默认行的计数、`isDefault`、零偏差删键三条全部失真)。
 */
class ProfileFieldDeviationTest {

    private val base = DeviceProfile(
        brand = "google",
        manufacturer = "Google",
        model = "Pixel 7 Pro",
        densityDpi = 420,
        androidVersion = "14",
        sdkInt = 34,
    )

    private fun key(field: ProfileField): String = ProfileFieldCatalog.jsonKeyOf(field)

    private fun textsOf(profile: DeviceProfile): Map<ProfileField, String> =
        ProfileField.entries.associateWith { it.stringOf(profile) }

    @Test
    fun applyingNoDeviationIsAnIdentity() {
        val empty = ProfileFieldDeviation.apply(base, null)
        assertEquals(base, empty.profile)
        assertTrue(empty.rejected.isEmpty())
        assertEquals(base, ProfileFieldDeviation.apply(base, emptyMap()).profile)
    }

    @Test
    fun overridingOneFieldTouchesOnlyThatField() {
        val merged = ProfileFieldDeviation.apply(
            base,
            mapOf(key(ProfileField.MODEL) to "Galaxy S24"),
        )
        assertEquals("Galaxy S24", merged.profile.model)
        assertEquals(base.brand, merged.profile.brand)
        assertEquals(base.densityDpi, merged.profile.densityDpi)
        assertEquals(base.sdkInt, merged.profile.sdkInt)
        assertTrue(merged.rejected.isEmpty())
    }

    @Test
    fun emptyValueClearsAProfileSuppliedField() {
        val merged = ProfileFieldDeviation.apply(base, mapOf(key(ProfileField.MODEL) to ""))
        assertEquals("空串 = 显式不伪装这一项", "", merged.profile.model)
        assertEquals("其余项不受影响", "google", merged.profile.brand)
    }

    @Test
    fun illegalAndUnknownKeysAreRejectedByNameNotSwallowed() {
        val merged = ProfileFieldDeviation.apply(
            base,
            mapOf(
                key(ProfileField.DENSITY_DPI) to "999999",
                key(ProfileField.SDK_INT) to "abc",
                "noSuchKey" to "x",
                key(ProfileField.BRAND) to "Samsung",
            ),
        )
        assertEquals(
            "三项非法/未知必须逐条点名(静默丢弃 = 用户存进一份与自己输入不同的画像)",
            listOf(
                key(ProfileField.DENSITY_DPI),
                key(ProfileField.SDK_INT),
                "noSuchKey",
            ).sorted(),
            merged.rejected.sorted(),
        )
        assertEquals("合法项必须照常落", "Samsung", merged.profile.brand)
        assertEquals("非法项必须保持画像原值", base.densityDpi, merged.profile.densityDpi)
    }

    @Test
    fun roundTripThroughBetweenReproducesTheSameProfile() {
        val edited = base.copy(model = "Pixel 8", brand = "", batteryLevel = 55)
        val deviations = ProfileFieldDeviation.between(base, textsOf(edited))
        assertTrue(
            "只应留下真正被改的三项:${deviations?.keys}",
            deviations != null && deviations.size == 3,
        )
        assertEquals(edited, ProfileFieldDeviation.apply(base, deviations).profile)
    }

    @Test
    fun textEqualToProfileValueIsNeverADeviation() {
        assertNull(
            "逐字等于画像 ⇒ 零覆写(否则幽灵偏差会让 isDefault 与恢复默认行永不归零)",
            ProfileFieldDeviation.between(base, textsOf(base)),
        )
        val padded = textsOf(base).toMutableMap().apply {
            this[ProfileField.MODEL] = "  ${base.model}  "
        }
        assertNull(
            "两侧同值只差首尾空白也不算定制(存进去的值与画像原值等价,留下来就是噪音)",
            ProfileFieldDeviation.between(base, padded),
        )
    }

    @Test
    fun clearingAProfileValueIsADeviationOfItsOwn() {
        val deviations = ProfileFieldDeviation.between(
            base,
            textsOf(base).toMutableMap().apply { this[ProfileField.MODEL] = "" },
        )
        assertEquals("清空必须是可表达的定制", mapOf(key(ProfileField.MODEL) to ""), deviations)
    }

    @Test
    fun everyFieldKeyRoundTripsThroughTheCatalog() {
        // 覆写以 jsonKey 存盘:任何一个字段的 jsonKey 认不回来,它就是屏上改得动、
        // 下发时被当未知键丢掉的死项。41 项逐个走一遍,新加字段自动纳入。
        for (field in ProfileField.entries) {
            val k = ProfileFieldCatalog.jsonKeyOf(field)
            assertEquals("jsonKey 必须认回同一字段", field, ProfileFieldCatalog.fieldByJsonKey(k))
            val value = field.stringOf(base)
            assertEquals(
                "写回同值必须无损($k)",
                value,
                ProfileFieldDeviation.apply(base, mapOf(k to value)).profile.let { field.stringOf(it) },
            )
        }
    }
}
