package com.lioisme.modev.domain.config

import com.lioisme.modev.core.model.CarrierEntry

/**
 * 运营商目录契约:SIM 三字段(PLMN / 运营商名 / 国家码)选择器的唯一数据源。
 * 与 [DeviceCatalogService] 同形态 —— 包括那条红线:[carriers] 的 null **不等于空表**,
 * 它表示随包资产读不出或解不开,调用方须上屏为「目录不可用」并可诊断,
 * 禁渲染成「库里没有运营商」(那是静默失效的另一种写法)。
 */
interface CarrierCatalogService {

    /** 随包运营商表;null = 资产不可用。成功后实现侧缓存,重复调用不再读盘。 */
    suspend fun carriers(): List<CarrierEntry>?
}
