package com.lioisme.modev.core

import com.lioisme.modev.core.model.CarrierEntry
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.applyCarrierPreset
import com.lioisme.modev.core.model.parseCarrierCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 运营商目录测试(V48 R-3,纯 JVM):读**真随包资产**逐值对账,不用夹具。
 *
 * 为什么要钉数据集形态:V47 台账实测 `carriers.json` 的 1597 条里有 **77 条 countryCode 是
 * `"AU/CC/CX"` 这类多地区串** —— 直接套用会写出一个过不了 `SIM_COUNTRY` 校验的值。
 * 这条坑只有把「形态」与「写入守卫」两头都锁住才不会在未来重引入(换一版上游数据时,
 * 下面的计数断言会先响,而不是让用户在编辑器里收到「选了却报非法」)。
 */
class CarrierCatalogTest {

    private val assetText by lazy { File("src/main/assets/carriers.json").readText() }

    @Test
    fun shippedAssetParsesAndKeepsEveryPlmnValid() {
        val carriers = parseCarrierCatalog(assetText)
        assertNotNull("carriers.json 必须能解出条目", carriers)
        assertEquals("实测条目数(换上游版本时先改这里再改代码)", 1597, carriers!!.size)
        carriers.forEach {
            assertTrue("非法 PLMN: $it", it.plmn.matches(Regex("\\d{5,6}")))
            assertTrue("无名运营商不可选: $it", it.name.isNotBlank())
        }
        assertEquals("PLMN 必须是唯一键(选择器按它认回条目)", 1597, carriers.map { it.plmn }.distinct().size)
        // 已知数据坑的量化形态:77 条国家码不是两字母。
        assertEquals(77, carriers.count { it.singleCountryCode() == null })
    }

    /** 实锤坑:多地区国家码串**绝不**进画像(不猜、不拆、不钳 —— 留空即该项不干预)。 */
    @Test
    fun multiRegionCountryCodeNeverReachesProfile() {
        val dirty = CarrierEntry(name = "Telstra", plmn = "50501", countryCode = "AU/CC/CX", countryName = "Australia")
        val profile = applyCarrierPreset(DeviceProfile(), dirty)
        assertEquals("50501", profile.simOperator)
        assertEquals("Telstra", profile.simOperatorName)
        assertEquals("", profile.simCountry)
    }

    @Test
    fun twoLetterCountryCodeWritesAllThreeFieldsAtOnce() {
        val profile = applyCarrierPreset(DeviceProfile(), CarrierEntry("China Mobile", "46000", "CN", "China"))
        assertEquals("46000", profile.simOperator)
        assertEquals("China Mobile", profile.simOperatorName)
        assertEquals("CN", profile.simCountry)
    }

    /** 铁律:候选表逐值必须过字段校验,否则选择器会把用户推向「选了却报非法」。 */
    @Test
    fun everyAssetValuePassesFieldValidation() {
        val carriers = checkNotNull(parseCarrierCatalog(assetText))
        val fields = listOf(ProfileField.SIM_OPERATOR, ProfileField.SIM_OPERATOR_NAME, ProfileField.SIM_COUNTRY)
        carriers.forEach { entry ->
            val written = applyCarrierPreset(DeviceProfile(), entry)
            val values = mapOf(
                ProfileField.SIM_OPERATOR to written.simOperator,
                ProfileField.SIM_OPERATOR_NAME to written.simOperatorName,
                ProfileField.SIM_COUNTRY to written.simCountry,
            )
            fields.forEach { field ->
                val error = ProfileFieldCatalog.validate(field, values.getValue(field))
                assertNull("$field 拒绝了它自己的候选值 ${values.getValue(field)} ($entry)", error)
            }
        }
    }

    @Test
    fun unparsableAndEmptyAssetsAreRejectedNotGuessed() {
        assertNull(parseCarrierCatalog(""))
        assertNull(parseCarrierCatalog("   "))
        assertNull(parseCarrierCatalog("not json"))
        assertNull(parseCarrierCatalog("[]"))
        // 形态不符(对象而非数组)必须判不可用,而不是解出一堆空条目。
        assertNull(parseCarrierCatalog("""{"a":1}"""))
    }

    /** 标签唯一事实源:国家 · 运营商 · PLMN(基线同形),空白段不留孤零零的分隔符。 */
    @Test
    fun labelComposesOnlyPresentParts() {
        assertEquals(
            "China · China Mobile · 46000",
            CarrierEntry("China Mobile", "46000", "CN", "China").label(),
        )
        assertEquals("46000", CarrierEntry(plmn = "46000").label())
    }

    /** 许可与出处必须留在包里(MIT 要求随附许可证;与 devices.NOTICE.txt 同一纪律)。 */
    @Test
    fun noticeRecordsUpstreamAndRevision() {
        val notice = File("src/main/assets/carriers.NOTICE.txt").readText()
        assertTrue(notice, notice.contains("pbakondy/mcc-mnc-list"))
        assertTrue(notice, notice.contains("MIT"))
        assertTrue(notice, notice.contains("Source revision:"))
    }
}
