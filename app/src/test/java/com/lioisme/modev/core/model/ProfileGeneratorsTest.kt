package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V47 T3 值生成器:形态合法性 + 与校验器的互逆性。
 *
 * 这里刻意用**固定种子**跑大样本,而不是「生成一次断言一次」:
 * 形态类断言(Luhn、MAC 置位、坐标域)只有在多样本下才有意义,
 * 单次抽样既抓不到越界也抓不到「某些随机分支根本不产出合法值」。
 */
class ProfileGeneratorsTest {

    private fun seeded(seed: Long) = Random(seed)

    @Test
    fun `generated imei always passes the luhn validator`() {
        val random = seeded(20260922L)
        repeat(2000) {
            val imei = ProfileGenerators.randomImei(random)
            assertEquals(15, imei.length)
            assertTrue("$imei 不过 Luhn", ProfileFieldCatalog.isLuhnValidImei(imei))
            assertNullMessage(imei)
        }
    }

    private fun assertNullMessage(imei: String) {
        assertEquals(
            "生成器与校验器互逆断裂:$imei",
            null,
            ProfileFieldCatalog.validate(ProfileField.IMEI, imei),
        )
    }

    @Test
    fun `luhn check digit reproduces a known valid imei`() {
        // 独立算出的样例:主体 49017158169617 ⇒ 校验位 2
        assertEquals('2', ProfileGenerators.luhnCheckDigit("49017158169617"))
        assertTrue(ProfileFieldCatalog.isLuhnValidImei("490171581696172"))
    }

    @Test
    fun `generated mac is locally administered unicast and validates`() {
        val random = seeded(7L)
        repeat(1000) {
            val mac = ProfileGenerators.randomMacAddress(random)
            assertTrue("形态异常:$mac", ProfileFieldCatalog.isMacAddress(mac))
            val first = mac.substring(0, 2).toInt(16)
            assertEquals("bit1 未置位(不是本地管理地址):$mac", 0x02, first and 0x02)
            assertEquals("bit0 未清零(组播地址):$mac", 0x00, first and 0x01)
            assertEquals(null, ProfileFieldCatalog.validate(ProfileField.WIFI_MAC_ADDRESS, mac))
        }
    }

    @Test
    fun `generated mac never lands on the emulator and docker OUI heuristics`() {
        // DFC-04 实锤形态:旧假 MAC 前缀 02:42:ac:11: 本身就是模拟器/容器判据之一。
        val bannedPrefixes = listOf("02:42:ac", "00:05:69", "00:1c:14", "00:0c:29", "00:50:56")
        val random = seeded(99L)
        repeat(3000) {
            val mac = ProfileGenerators.randomMacAddress(random).lowercase()
            bannedPrefixes.forEach { banned ->
                assertFalse("$mac 命中已知虚拟化 OUI $banned", mac.startsWith(banned))
            }
        }
    }

    @Test
    fun `generated android id is 16 lowercase hex`() {
        val random = seeded(11L)
        repeat(500) {
            val id = ProfileGenerators.randomAndroidId(random)
            assertEquals(16, id.length)
            assertTrue(id.all { it in "0123456789abcdef" })
            assertEquals(null, ProfileFieldCatalog.validate(ProfileField.ANDROID_ID, id))
        }
    }

    @Test
    fun `generated phone number is 11 digits starting with one`() {
        val random = seeded(13L)
        repeat(500) {
            val phone = ProfileGenerators.randomPhoneNum(random)
            assertEquals(11, phone.length)
            assertTrue(phone.all { it.isDigit() })
            assertEquals('1', phone.first())
        }
    }

    @Test
    fun `build id prefix and year track the declared android release`() {
        for (release in listOf("10", "11", "12", "13", "14", "15", "16", "17")) {
            val prefix = ProfileGenerators.buildIdPrefix(release)
            val baseYear = ProfileGenerators.buildIdBaseYear(release)
            // 生成器在 baseYear 上加 0..2 年,取两位年份 ⇒ 回绕后仍是这三档。
            val allowedYears = (0..2).map { (baseYear + it) % 100 }.toSet()
            val random = seeded(release.hashCode().toLong())
            repeat(200) {
                val id = ProfileGenerators.randomBuildId(release, random)
                assertTrue("$id 前缀不是 $prefix", id.startsWith("$prefix."))
                val yymmdd = id.substring(prefix.length + 1).take(6)
                assertTrue("$id 年份越出 $release 世代", yymmdd.take(2).toInt() in allowedYears)
                assertTrue("$id 月域异常", yymmdd.substring(2, 4).toInt() in 1..12)
                assertTrue("$id 日域异常", yymmdd.substring(4, 6).toInt() in 1..28)
                assertTrue("$id 缺序号段", id.substringAfterLast(".").length == 3)
            }
        }
    }

    @Test
    fun `derived fingerprint is consistent with the identity triple`() {
        val random = seeded(21L)
        val fingerprint = ProfileGenerators.deriveFingerprint(
            brand = "google",
            product = "raven",
            device = "raven",
            release = "14",
            buildId = "UP1A.231105.001",
            random = random,
        )
        assertTrue(fingerprint.startsWith("google/raven/raven:14/UP1A.231105.001/"))
        assertTrue(fingerprint.endsWith(":user/release-keys"))
        assertEquals(null, ProfileFieldCatalog.validate(ProfileField.FINGERPRINT, fingerprint))
    }

    @Test
    fun `fingerprint parts sanitize the segment separators instead of corrupting structure`() {
        // 段值本身含 `/` 或 `:` 会把 fingerprint 撑出多余段,必须清洗成 `_`。
        val fingerprint = ProfileGenerators.deriveFingerprint(
            brand = "a/b:c",
            product = " ",
            device = "d",
            release = "14",
            buildId = "UP1A.1",
            random = seeded(1L),
        )
        val segments = fingerprint.substringBefore(':').split('/')
        assertEquals("清洗失效,段数被撑开:$fingerprint", 3, segments.size)
        assertEquals("a_b_c", segments[0])
        assertEquals("d", segments[2])
        // product 清洗后为空 ⇒ 回落占位值,不得留空段
        assertTrue(segments[1].isNotEmpty())
    }

    @Test
    fun `coordinates stay inside their legal domain and keep six decimals`() {
        val random = seeded(31L)
        repeat(2000) {
            val lat = ProfileGenerators.randomLatitude(random)
            val lon = ProfileGenerators.randomLongitude(random)
            assertTrue("$lat 越出纬度域", lat in -90.0..90.0)
            assertTrue("$lon 越出经度域", lon in -180.0..180.0)
            val decimals = lat.toString().substringAfter('.', "")
            assertTrue("$lat 小数位超六位", decimals.length <= 6 || decimals == "0")
            assertEquals(null, ProfileFieldCatalog.validate(ProfileField.LATITUDE, lat.toString()))
            assertEquals(null, ProfileFieldCatalog.validate(ProfileField.LONGITUDE, lon.toString()))
        }
    }

    @Test
    fun `battery level stays in zero to one hundred`() {
        val random = seeded(41L)
        val seen = mutableSetOf<Int>()
        repeat(2000) {
            val level = ProfileGenerators.randomBatteryLevel(random)
            assertTrue("$level 越界", level in 0..100)
            assertEquals(null, ProfileFieldCatalog.validate(ProfileField.BATTERY_LEVEL, level.toString()))
            seen += level
        }
        assertTrue("电量分布异常稀疏:${seen.size}", seen.size > 80)
    }

    @Test
    fun `identical seed reproduces identical values`() {
        assertEquals(ProfileGenerators.randomImei(seeded(5L)), ProfileGenerators.randomImei(seeded(5L)))
        assertEquals(
            ProfileGenerators.randomMacAddress(seeded(5L)),
            ProfileGenerators.randomMacAddress(seeded(5L)),
        )
        assertDifferentOnDifferentSeed()
    }

    private fun assertDifferentOnDifferentSeed() {
        assertFalse(
            ProfileGenerators.randomAndroidId(seeded(1L)) == ProfileGenerators.randomAndroidId(seeded(2L)),
        )
    }

    @Test
    fun `uuid and string helpers honour requested length and alphabet`() {
        val uuid = ProfileGenerators.uuidNoDash(seeded(3L))
        assertEquals(32, uuid.length)
        assertTrue(uuid.all { it in "0123456789abcdef" })
        assertEquals(12, ProfileGenerators.randomString(12, seeded(3L)).length)
        assertEquals("", ProfileGenerators.randomString(0, seeded(3L)))
    }
}
