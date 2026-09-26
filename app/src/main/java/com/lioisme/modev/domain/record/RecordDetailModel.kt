package com.lioisme.modev.domain.record

import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.core.model.UiInteractionRecord
import com.lioisme.modev.core.model.WebViewRecord

/**
 * 记录详情模型(ADR-11,数据库监听 T11):
 * 详情页按 [recordType] 分派为算法 / 数据库 / 行为(V27)三种形态,渲染节由 UI 分支。
 * 详情页是唯一允许全量物化的查询,故承载完整模型。
 */
sealed interface RecordDetailModel {
    val id: Long
    val recordType: String
    val packageName: String
    val processName: String?
    val threadName: String
    val stack: List<String>
    val timestamp: Long
}

/** 算法记录详情。 */
data class CryptoDetail(val record: CryptoRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** 数据库记录详情。 */
data class SqlDetail(val record: SqlRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** 行为记录详情(V27 一等公民)。 */
data class BehaviorDetail(val record: BehaviorRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** 偏好记录详情(V28 一等公民)。 */
data class PrefsDetail(val record: PrefsRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** WebView 记录详情(V29 一等公民)。 */
data class WebViewDetail(val record: WebViewRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** 网络环境监听记录详情(V30 一等公民)。 */
data class NetworkDetail(val record: NetworkRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** 设备环境监听记录详情(V31 一等公民)。 */
data class DeviceEnvDetail(val record: DeviceEnvRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** UI 交互监听记录详情(V33 一等公民)。 */
data class UiInteractionDetail(val record: UiInteractionRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** 系统信息监听记录详情(V33 一等公民)。 */
data class SystemDetail(val record: SystemRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}

/** 进程控制监听记录详情(V33 一等公民)。 */
data class ProcessDetail(val record: ProcessRecord) : RecordDetailModel {
    override val id: Long get() = record.id
    override val recordType: String get() = record.recordType
    override val packageName: String get() = record.packageName
    override val processName: String? get() = record.processName
    override val threadName: String get() = record.threadName
    override val stack: List<String> get() = record.stack
    override val timestamp: Long get() = record.timestamp
}
