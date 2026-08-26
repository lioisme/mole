package com.lioisme.modev.data.mapping

import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.SqlRecord

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