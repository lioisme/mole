package com.lioisme.modev.core.model

import java.util.Locale
import java.util.TimeZone

/*
 * 可枚举字段的候选值来源(V47 WP-5c):语言 / 国家 / 时区这三项的合法取值**不在本项目里维护**,
 * 直接向平台常量表要(`Locale.getISOLanguages` / `Locale.getISOCountries` /
 * `TimeZone.getAvailableIDs`)—— 自己抄一张 ISO/IANA 清单就是造第二事实源,
 * 而且是一张会过期的。选出来的值恒过 `ProfileFieldCatalog` 校验,
 * 因为校验器判时区用的正是同一张 `TimeZone.getAvailableIDs()`。
 *
 * 只覆盖「取值即枚举、形态无歧义」的三项。运营商 PLMN / SIM 运营商名**不在这里** ——
 * 那需要 MCC/MNC 数据资产(基线 `carriers.json`),留到 WP-4 与设备资产同轮引入;
 * 语言/国家/时区也不走机型库那条随包 JSON 通路,理由同上:平台已经给了权威表,
 * 再随包一份就是两处维护。
 */

/** 一个字段的候选值列表(null = 该字段没有可枚举来源,走文本输入)。 */
object ProfileOptionSource {

    /** 该字段的候选值;null = 无枚举来源。 */
    fun optionsFor(field: ProfileField): List<String>? = when (field) {
        ProfileField.LANGUAGE -> languages()
        ProfileField.SIM_COUNTRY -> countries()
        ProfileField.TIME_ZONE -> timeZones()
        // 这两档平台没有可枚举 API(见 ProfileValueSets 头注),值表在仓里,入口仍收口在本函数。
        ProfileField.DENSITY_DPI -> ProfileValueSets.densities()
        ProfileField.CPU_CORES -> ProfileValueSets.cpuCores()
        else -> null
    }

    /** ISO 639 二字码(平台表原样;升序去重,选择器按字典序呈现)。 */
    fun languages(): List<String> = Locale.getISOLanguages().toList().sorted()

    /** ISO 3166-1 alpha-2 国家码(即画像 `SIM_COUNTRY` 要的两字母形态)。 */
    fun countries(): List<String> = Locale.getISOCountries().toList().sorted()

    /**
     * IANA 时区标识。
     *
     * 排序 + 去重:平台表本身含别名重复,而这张列表要按字典序给人扫(600+ 项,
     * 未排序时同一地区的别名会散落各处)。校验器判时区用的就是同一张表 ⇒ 这里选出的
     * 值恒过校验(注意 JVM 侧与设备侧 ICU 的表内容不同,故测试只断言「每项都过校验」,
     * 不断言具体条数)。
     */
    fun timeZones(): List<String> = TimeZone.getAvailableIDs().toList().distinct().sorted()
}
