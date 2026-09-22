package com.lioisme.modev.domain.config

import com.lioisme.modev.core.model.DeviceCatalog

/**
 * 真机目录契约(V47):品牌 → 机型选择器的唯一数据源(UI 与将来的工具面同经本契约,
 * AGENTS #18 方法层契约族;实现 = `data.repo.DeviceCatalogRepository`,容器出口 = `AppContainer.deviceCatalog`)。
 *
 * [catalog] 的 null **不等于空目录**:它表示随包资产读不出或解不开(打包缺失 / JSON 破损 /
 * 目录版本高于本 App 能理解的形态)。两者必须可区分 —— 把「读不到」渲染成「没有可选机型」
 * 就是静默失效,与 `ProfileResolution.Missing` 曾被吞成 auto 同一族缺陷(AGENTS 红线「零静默」,
 * 参见 `core.model.ProfileResolution`)。因此调用方须把 null 上屏为可诊断的错误态,
 * 禁回退到空选择器,也禁自行 `?: DeviceCatalog.EMPTY`(EMPTY 只为比较而存在)。
 */
interface DeviceCatalogService {

    /** 随包目录;null = 资产不可用(含义见类注释)。成功后实现侧缓存,重复调用不再读盘。 */
    suspend fun catalog(): DeviceCatalog?
}
