package com.lioisme.modev.core.model

import com.lioisme.modev.core.codec.Json

/*
 * 运营商目录(V48 R-3,基线 `com.houvven.guise.module.preset.CarrierPresetRepository` 同族):
 * 让 SIM 三字段(PLMN / 运营商名 / 国家码)**选**而不是手打 —— 手写 PLMN 既容易错,
 * 又容易写出与运营商名、国家码互相矛盾的组合(那本身就是一枚检测点)。
 *
 * 数据随包发布在 `app/src/main/assets/carriers.json`(134,053 B / 实测 1597 条 /
 * 派生自 pbakondy/mcc-mnc-list,MIT;上游修订号逐字记在同目录 carriers.NOTICE.txt,禁改文件)。
 *
 * 与机型目录(`DeviceCatalog.kt`)刻意同形态:纯数据 + 零 android + 唯一解码入口 +
 * 「写画像」这一步单独成函数,好让「选了谁」与「写了哪几项」各自可测。
 */

/**
 * 一条运营商记录。四字段全部带默认值 ⇒ Kotlin 生成无参构造器 ⇒ Gson 走
 * 「构造器 + 逐字段填充」(AGENTS 实锤:`Unsafe.allocateInstance` 会跳过默认值表达式)。
 */
data class CarrierEntry(
    /** 运营商显示名(市场名),写进 [ProfileField.SIM_OPERATOR_NAME]。 */
    val name: String = "",

    /** PLMN = MCC+MNC 五位或六位纯数字,写进 [ProfileField.SIM_OPERATOR]。 */
    val plmn: String = "",

    /** ISO 3166-1 alpha-2;**实测 1597 条里有 77 条是 `"AU/CC/CX"` 这类多地区串**,故不可直写。 */
    val countryCode: String = "",

    /** 国家/地区呈现名,只用于选择器标签(画像里没有这一项,也不派生)。 */
    val countryName: String = "",
) {

    /** 选择器行文本(唯一事实源,UI 逐字显示):`国家 · 运营商 · PLMN`,与基线同形。 */
    fun label(): String = listOf(countryName, name, plmn).filter { it.isNotBlank() }.joinToString(" · ")

    /** 可安全写进画像的国家码:恰两字母才返回,多地区串一律返回 null(**不猜、不拆、不钳**)。 */
    fun singleCountryCode(): String? = countryCode.takeIf { it.length == 2 && it.all(Char::isUpperCase) }
}

/**
 * 资产文本 → 条目表。**唯一解码入口**(别处不得再 `fromJson(List<CarrierEntry>)`)。
 *
 * 返回 null 的全部情形(调用方须上屏为「目录不可用」,禁退化成「库里没运营商」):
 * 空白文本、非 JSON、形态不符(Gson 抛异常即收口)、解析后零条目。
 */
fun parseCarrierCatalog(text: String): List<CarrierEntry>? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parsed = runCatching {
        Json.gson.fromJson(trimmed, Array<CarrierEntry>::class.java)
    }.getOrNull() ?: return null
    return parsed.toList().takeIf { it.isNotEmpty() }
}

/**
 * 把选中的运营商写进画像:一次写全 SIM_OPERATOR / SIM_OPERATOR_NAME / SIM_COUNTRY 三项。
 *
 * 国家码那一项**有条件**:数据集里 77 条 countryCode 是 `"AU/CC/CX"` 形态的多地区串,
 * 直写会得到一个过不了 `ProfileFieldCatalog` 校验的值(校验判的是两字母 ISO 码)。
 * 此时留空(= 该项不干预),而不是拆第一个两字母凑一个 —— 那等于自造事实源。
 */
fun applyCarrierPreset(profile: DeviceProfile, picked: CarrierEntry): DeviceProfile = profile.copy(
    simOperator = picked.plmn,
    simOperatorName = picked.name,
    simCountry = picked.singleCountryCode().orEmpty(),
)
