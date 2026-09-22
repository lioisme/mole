package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V47 T1 画像字段目录:分区与类别覆盖、键名唯一性、哨兵语义、校验表与整组随机集。
 *
 * 本类的核心作用是**双向对账** —— 目录声明的字段集必须与
 * [DeviceProfile] 的属性集、基线 `ModuleConfig` 的 40 项能力集严格等价。
 */
class ProfileFieldCatalogTest {

    @Test
    fun `catalog covers exactly forty-one fields with forty baseline keys`() {
        assertEquals(41, ProfileField.entries.size)
        assertEquals(
            "基线 40 项须逐字段对齐,Modev 超基线项恒为一项(CPU 核数)",
            40,
            ProfileField.entries.count { it.baselineKey != null },
        )
        assertEquals(listOf(ProfileField.CPU_CORES), ProfileField.entries.filter { it.baselineKey == null })
    }

    @Test
    fun `baseline keys are distinct and match baseline spelling`() {
        val keys = ProfileField.entries.mapNotNull { it.baselineKey }
        assertEquals(keys.size, keys.toSet().size)
        // 基线易错拼写必须原样保留(wire 兼容):fingerPrint 与 wifiSSID 的驼峰、大小写。
        assertTrue("fingerPrint" in keys)
        assertTrue("wifiSSID" in keys)
        assertTrue("wifiBSSID" in keys)
        assertTrue("makeWifiLocationFail" in keys)
        assertFalse("fingerprint" in keys)
    }

    @Test
    fun `every kind owns at least one field and every field owns exactly one kind`() {
        for (kind in ProfileKind.entries) {
            assertTrue("$kind 无字段归属", ProfileFieldCatalog.byKind.getValue(kind).isNotEmpty())
        }
        assertEquals(18, ProfileKind.entries.size)
        val covered = ProfileFieldCatalog.byKind.values.flatten()
        assertEquals(covered.size, covered.toSet().size)
        assertEquals(ProfileField.entries.size, covered.size)
    }

    @Test
    fun `every section owns fields and sections partition the catalog`() {
        val partitioned = ProfileFieldCatalog.bySection.values.flatten()
        assertEquals(ProfileField.entries.size, partitioned.size)
        assertEquals(partitioned.size, partitioned.toSet().size)
        assertTrue(ProfileFieldCatalog.bySection.values.all { it.isNotEmpty() })
    }

    @Test
    fun `json keys are unique and reverse lookup round trips`() {
        val jsonKeys = ProfileField.entries.map { ProfileFieldCatalog.jsonKeyOf(it) }
        assertEquals(jsonKeys.size, jsonKeys.toSet().size)
        for (field in ProfileField.entries) {
            val key = ProfileFieldCatalog.jsonKeyOf(field)
            assertEquals(field, ProfileFieldCatalog.fieldByJsonKey(key))
        }
        assertNull("未知键必须反查为 null 供调用方硬失败", ProfileFieldCatalog.fieldByJsonKey("noSuchField"))
    }

    @Test
    fun `extension field json key derives to lower camel case`() {
        assertEquals("cpuCores", ProfileFieldCatalog.jsonKeyOf(ProfileField.CPU_CORES))
        assertEquals("brand", ProfileFieldCatalog.jsonKeyOf(ProfileField.BRAND))
    }

    @Test
    fun `empty profile puts every field in unhook state`() {
        for (field in ProfileField.entries) {
            assertFalse("$field 空画像应不生效", field.isSetIn(DeviceProfile.EMPTY))
            assertEquals("$field 空画像应渲染为空串", "", field.stringOf(DeviceProfile.EMPTY))
        }
        assertEquals(0, DeviceProfile.EMPTY.filledCount)
        assertTrue(DeviceProfile.EMPTY.isEmpty)
    }

    @Test
    fun `legal samples set the field and survive the text round trip`() {
        for (field in ProfileField.entries) {
            val sample = legalSample(field)
            val profile = field.withText(DeviceProfile.EMPTY, sample)
            assertTrue("$field 取值 $sample 应生效", field.isSetIn(profile))
            assertEquals("$field 文本回读不稳定", sample, field.stringOf(profile))
            assertNull("$field 取值 $sample 应合法", ProfileFieldCatalog.validate(field, sample))
        }
    }

    @Test
    fun `clearing a field returns it to unhook state`() {
        for (field in ProfileField.entries) {
            val set = field.withText(DeviceProfile.EMPTY, legalSample(field))
            assertTrue(field.isSetIn(set))
            val cleared = field.withText(set, "")
            assertFalse("$field 清空后应取消干预", field.isSetIn(cleared))
            assertNull("$field 空串恒合法", ProfileFieldCatalog.validate(field, ""))
        }
    }

    @Test
    fun `unparsable numerics fall back to sentinel instead of throwing`() {
        for (field in listOf(ProfileField.SDK_INT, ProfileField.LAC, ProfileField.BATTERY_LEVEL)) {
            val profile = field.withText(DeviceProfile.EMPTY, "not-a-number")
            assertFalse(field.isSetIn(profile))
            assertEquals(ProfileFieldError.NUMBER_INVALID, ProfileFieldCatalog.validate(field, "abc"))
        }
        assertFalse(ProfileField.LONGITUDE.isSetIn(ProfileField.LONGITUDE.withText(DeviceProfile.EMPTY, "x")))
    }

    @Test
    fun `validators enforce baseline hard formats`() {
        assertEquals(ProfileFieldError.SIM_OPERATOR_FORMAT, ProfileFieldCatalog.validate(ProfileField.SIM_OPERATOR, "460"))
        assertEquals(ProfileFieldError.SIM_OPERATOR_FORMAT, ProfileFieldCatalog.validate(ProfileField.SIM_OPERATOR, "4600a"))
        assertNull(ProfileFieldCatalog.validate(ProfileField.SIM_OPERATOR, "46000"))
        assertNull(ProfileFieldCatalog.validate(ProfileField.SIM_OPERATOR, "460001"))

        assertEquals(ProfileFieldError.SIM_COUNTRY_FORMAT, ProfileFieldCatalog.validate(ProfileField.SIM_COUNTRY, "chn"))

        assertEquals(ProfileFieldError.IMEI_FORMAT, ProfileFieldCatalog.validate(ProfileField.IMEI, "490171581696171"))
        assertEquals(ProfileFieldError.IMEI_FORMAT, ProfileFieldCatalog.validate(ProfileField.IMEI, "12345"))

        assertEquals(ProfileFieldError.ANDROID_ID_FORMAT, ProfileFieldCatalog.validate(ProfileField.ANDROID_ID, "zzzz"))
        assertEquals(ProfileFieldError.MAC_FORMAT, ProfileFieldCatalog.validate(ProfileField.WIFI_MAC_ADDRESS, "02:00:00"))
        assertEquals(ProfileFieldError.LANGUAGE_FORMAT, ProfileFieldCatalog.validate(ProfileField.LANGUAGE, "english"))
        assertEquals(ProfileFieldError.TIME_ZONE_UNKNOWN, ProfileFieldCatalog.validate(ProfileField.TIME_ZONE, "Mars/Olympus"))
        assertEquals(
            ProfileFieldError.FINGERPRINT_STRUCTURE,
            ProfileFieldCatalog.validate(ProfileField.FINGERPRINT, "no-separator-here"),
        )
    }

    @Test
    fun `luhn check accepts a valid imei and rejects a wrong check digit`() {
        assertTrue(ProfileFieldCatalog.isLuhnValidImei("490171581696172"))
        assertFalse(ProfileFieldCatalog.isLuhnValidImei("490171581696173"))
        assertFalse(ProfileFieldCatalog.isLuhnValidImei("49017158169617"))
    }

    @Test
    fun `validators enforce declared ranges`() {
        assertEquals(ProfileFieldError.NUMBER_OUT_OF_RANGE, ProfileFieldCatalog.validate(ProfileField.DENSITY_DPI, "71"))
        assertEquals(ProfileFieldError.NUMBER_OUT_OF_RANGE, ProfileFieldCatalog.validate(ProfileField.DENSITY_DPI, "1001"))
        assertEquals(ProfileFieldError.NUMBER_OUT_OF_RANGE, ProfileFieldCatalog.validate(ProfileField.BATTERY_LEVEL, "101"))
        assertEquals(ProfileFieldError.NUMBER_OUT_OF_RANGE, ProfileFieldCatalog.validate(ProfileField.LATITUDE, "91"))
        assertEquals(ProfileFieldError.NUMBER_OUT_OF_RANGE, ProfileFieldCatalog.validate(ProfileField.LONGITUDE, "181"))
        assertEquals(ProfileFieldError.NUMBER_OUT_OF_RANGE, ProfileFieldCatalog.validate(ProfileField.CPU_CORES, "0"))
        assertNotNull(ProfileFieldCatalog.rangeOf(ProfileField.SDK_INT))
        assertNull(ProfileFieldCatalog.rangeOf(ProfileField.BRAND))
    }

    @Test
    fun `enum slot validators reject unknown slots`() {
        assertEquals(ProfileFieldError.NETWORK_VALUE_INVALID, ProfileFieldCatalog.validate(ProfileField.NETWORK_TYPE, "9"))
        assertEquals(ProfileFieldError.SCREENSHOT_VALUE_INVALID, ProfileFieldCatalog.validate(ProfileField.SCREENSHOTS_FLAG, "7"))
        assertNull(ProfileFieldCatalog.validate(ProfileField.SCREENSHOTS_FLAG, "1"))
    }

    @Test
    fun `random set excludes device internals and privacy switches`() {
        val excluded = setOf(
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
            ProfileField.CPU_CORES,
        )
        for (field in excluded) {
            assertFalse("$field 不应被一键随机覆盖", ProfileFieldCatalog.isRandomizable(field))
        }
        assertTrue(ProfileFieldCatalog.isRandomizable(ProfileField.IMEI))
        assertTrue(ProfileFieldCatalog.isRandomizable(ProfileField.FINGERPRINT))
        assertEquals(23, ProfileFieldCatalog.RANDOMIZABLE.size)
    }

    @Test
    fun `setting one field never disturbs another`() {
        val base = ProfileField.BRAND.withText(DeviceProfile.EMPTY, "google")
        val changed = ProfileField.MODEL.withText(base, "Pixel 7 Pro")
        assertEquals("google", ProfileField.BRAND.stringOf(changed))
        assertEquals("Pixel 7 Pro", ProfileField.MODEL.stringOf(changed))
        assertEquals(2, changed.filledCount)
    }
}
