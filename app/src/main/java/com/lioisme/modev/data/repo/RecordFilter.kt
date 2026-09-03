package com.lioisme.modev.data.repo

import com.lioisme.modev.core.model.RecordTypes

/**
 * 类型别名:兼容旧导入路径,实际数据类已迁移至 [com.lioisme.modev.domain.record.RecordFilter]。
 * 新代码应直接使用 [com.lioisme.modev.domain.record.RecordFilter]。
 */
typealias RecordFilter = com.lioisme.modev.domain.record.RecordFilter

/**
 * 类型别名:兼容旧导入路径,实际枚举已迁移至 [com.lioisme.modev.domain.record.SortOrder]。
 * 新代码应直接使用 [com.lioisme.modev.domain.record.SortOrder]。
 */
typealias SortOrder = com.lioisme.modev.domain.record.SortOrder

/** [RecordFilter.recordTypes] 便捷:是否限定单一算法类型。 */
val RecordFilter.isCryptoOnly: Boolean
    get() = recordTypes == setOf(RecordTypes.CRYPTO)

/** [RecordFilter.recordTypes] 便捷:是否限定单一数据库类型。 */
val RecordFilter.isDatabaseOnly: Boolean
    get() = recordTypes == setOf(RecordTypes.DATABASE)

/** 是否至少含一个有效约束(供 [RecordService.deleteByFilter] 防止无约束整表误删)。 */
val RecordFilter.hasConstraint: Boolean
    get() = recordTypes != null || packages != null || kinds != null ||
        operations != null || !query.isNullOrBlank() || timeRange != null
