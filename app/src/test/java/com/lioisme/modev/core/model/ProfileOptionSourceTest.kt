package com.lioisme.modev.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 枚举字段的候选值来源回归(V47 WP-5c;V48 R-3 由 3 项扩到 5 项)。
 *
 * 这里钉的是**「选出来的值必须过校验」**这一条:候选表来自平台(`Locale` / `TimeZone`)
 * 或本仓事实表(`ProfileValueSets`),判据来自 [ProfileFieldCatalog],两者不同源就可能不同步 ——
 * 一旦漂移,选择器就会把用户推向「选了却报非法」或「存进一份画像库里根本不认的值」。
 * 平台表内容随 JDK/ICU 版本变,故不断言其条数与具体取值,只断言不变式。
 */
class ProfileOptionSourceTest {

    /**
     * 当前有候选表的字段。新增一项必须同时出现在这里 —— 否则它绕过下面三条不变式。
     */
    private val enumeratedFields = listOf(
        ProfileField.LANGUAGE,
        ProfileField.SIM_COUNTRY,
        ProfileField.TIME_ZONE,
        ProfileField.DENSITY_DPI,
        ProfileField.CPU_CORES,
    )

    @Test
    fun enumeratedFields_areListedAndTheRestStayHandFilled() {
        enumeratedFields.forEach { field ->
            assertTrue("$field 应有候选表", ProfileOptionSource.optionsFor(field)!!.isNotEmpty())
        }
        // 机型/型号没有平台表(走 devices.json 那条),其余字段一律手填。
        assertNull(ProfileOptionSource.optionsFor(ProfileField.MODEL))
        assertNull(ProfileOptionSource.optionsFor(ProfileField.BRAND))
        assertNull(ProfileOptionSource.optionsFor(ProfileField.IMEI))
        assertNull(ProfileOptionSource.optionsFor(ProfileField.FINGERPRINT))
    }

    @Test
    fun everyOfferedValue_passesTheFieldValidator() {
        enumeratedFields.forEach { field ->
            val options = ProfileOptionSource.optionsFor(field)!!
            options.forEach { value ->
                assertNull(
                    "候选值必须被同一字段的校验器接受:$field = $value",
                    ProfileFieldCatalog.validate(field, value),
                )
            }
        }
    }

    /**
     * 顺序与去重:整数档按**数值升序**(字典序会把核数排成 10,12,2,4,6,8 —— 数值表按字典
     * 断言等于把 UX 交给排序器),文本表按字典序(600+ 项的时区列表无序 = 找不到)。
     */
    @Test
    fun lists_areOrderedAndDuplicateFree_soThePickerScrollsPredictably() {
        enumeratedFields.forEach { field ->
            val options = ProfileOptionSource.optionsFor(field)!!
            assertEquals("$field 不得有重复项", options.size, options.distinct().size)
            if (field.type == ProfileValueType.INT) {
                val numbers = options.map { it.toInt() }
                assertEquals("$field 必须按数值升序", numbers.sorted(), numbers)
            } else {
                assertEquals("$field 必须按字典序", options, options.sorted())
            }
        }
    }

    @Test
    fun countryAndLanguage_areTwoLetterForms_theValidatorsExpect() {
        assertTrue(ProfileOptionSource.optionsFor(ProfileField.SIM_COUNTRY)!!.all { it.length == 2 })
        assertTrue(ProfileOptionSource.optionsFor(ProfileField.LANGUAGE)!!.all { it.length == 2 })
        assertNotNull(
            "默认时区必须在自己给的列表里(否则用户连当前值都选不回来)",
            ProfileOptionSource.timeZones().firstOrNull { it == java.util.TimeZone.getDefault().id },
        )
    }
}
