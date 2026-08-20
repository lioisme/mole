package com.lioisme.modev.data.mapping

import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation

/**
 * 列表行轻量模型(ADR-10,数据库监听 T9):
 * 数据库投影行([CryptoRecordRow])按 [recordType] 分派为本密封族,
 * 算法行复用轻量 [CryptoRecord](投影预览),数据库行承载 SQL 摘要等投影列,
 * 列表/时间线/分页/邻接场景零大字段物化。
 */
sealed interface RowModel {
    val id: Long
    val timestamp: Long

    /** 记录来源包名(V19 全局监听列表行内包名语境)。 */
    val packageName: String
}

/** 算法记录行(投影预览,字段语义同列表页既有 [CryptoRecord] 轻量复用)。 */
data class CryptoRow(
    val record: CryptoRecord,
) : RowModel {
    override val id: Long get() = record.id
    override val timestamp: Long get() = record.timestamp
    override val packageName: String get() = record.packageName
}

/** 数据库记录行(SQL 摘要预览 + 结果元数据;零大字段物化)。 */
data class SqlRow(
    val recordId: Long,
    val recordTimestamp: Long,
    override val packageName: String,
    val kind: SqlKind,
    val operation: SqlOperation,
    val databaseName: String,
    val sqlPreview: String?,
    val rowCount: Int?,
    val affectedCount: Int?,
    val rowId: Long?,
    val threadName: String,
) : RowModel {
    override val id: Long get() = recordId
    override val timestamp: Long get() = recordTimestamp
}