package com.lioisme.modev.domain.record


/**
 * 按记录类型的记录计数。
 * 从 data.db.RecordTypeCount 迁移至 domain 层,供 UI 和未来 MCP 使用。
 */
data class RecordTypeCount(
    val recordType: String,
    val count: Long,
)
