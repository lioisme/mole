package com.lioisme.modev.data.mapping

import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.core.model.UiInteractionRecord
import com.lioisme.modev.core.model.WebViewRecord
import com.lioisme.modev.data.db.CryptoRecordEntity

/**
 * 类型化记录 → 存储实体(写入面唯一分派点,V45 T2.5 / ISO-1)。
 *
 * 背景:写入模型原为 `domain.record.RecordDraft` —— 逐列镜像 110 个存储列的第三份
 * 平铺副本(实体 / 类型化记录 / Draft),且 Draft→实体映射是**恒等逐列拷贝**
 * (纯机械重复,新增一列要改三处)。现写入面直接用 [MonitoredRecord]
 * (注入侧、wire 编解码、读取投影共用的同一类型化模型族),
 * 落库由本函数按类型分派到各 `*RecordMappers.toEntity()`(与摄入路径同一实现,
 * 单一事实源);密封穷尽 → 新增记录类型编译期强制登记,不会静默漏写。
 *
 * `id` 恒 0(create 自增)、`seq` 恒 null(重放幂等键仅注入侧链路需要,
 * V2 语义行),主键由调用方在 update 路径 `.copy(id = …)` 覆盖。
 */
internal fun MonitoredRecord.writeEntity(): CryptoRecordEntity = when (this) {
    is CryptoRecord -> toEntity()
    is SqlRecord -> toEntity()
    is BehaviorRecord -> toEntity()
    is PrefsRecord -> toEntity()
    is WebViewRecord -> toEntity()
    is NetworkRecord -> toEntity()
    is DeviceEnvRecord -> toEntity()
    is UiInteractionRecord -> toEntity()
    is SystemRecord -> toEntity()
    is ProcessRecord -> toEntity()
}
