package com.lioisme.modev.data.mapping

import com.lioisme.modev.domain.record.BehaviorDetail
import com.lioisme.modev.domain.record.CryptoDetail
import com.lioisme.modev.domain.record.DeviceEnvDetail
import com.lioisme.modev.domain.record.NetworkDetail
import com.lioisme.modev.domain.record.PrefsDetail
import com.lioisme.modev.domain.record.ProcessDetail
import com.lioisme.modev.domain.record.RecordDetailModel
import com.lioisme.modev.domain.record.SqlDetail
import com.lioisme.modev.domain.record.SystemDetail
import com.lioisme.modev.domain.record.UiInteractionDetail
import com.lioisme.modev.domain.record.WebViewDetail

/**
 * 记录原始 JSON 导出分派:序列化是数据层职责,消费方(UI 导出 / 未来 REST)
 * 经 [com.lioisme.modev.domain.record.RecordService.exportJson] 取用,不再直接 import
 * 各类型 `toJson` 扩展(此前 UI 因此持有 `data.mapping` 依赖边)。
 *
 * 逐类型编码器仍住在各 `*RecordMappers.kt`(与实体映射同文件,字段序单一事实源)。
 */
internal fun RecordDetailModel.rawJson(): String = when (this) {
    is CryptoDetail -> record.toJson()
    is SqlDetail -> record.toJson()
    is BehaviorDetail -> record.toJson()
    is PrefsDetail -> record.toJson()
    is WebViewDetail -> record.toJson()
    is NetworkDetail -> record.toJson()
    is DeviceEnvDetail -> record.toJson()
    is UiInteractionDetail -> record.toJson()
    is SystemDetail -> record.toJson()
    is ProcessDetail -> record.toJson()
}
