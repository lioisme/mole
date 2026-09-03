package com.lioisme.modev.domain.record

import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.RecordTypes

/**
 * 统一查询契约(V17 R2 记录底层一体化,V22 增强):
 * 全字段 null/空集 = 不过滤;集合为多值语义(IN);query 为关键字(仓库层负责 SQL LIKE 转义)。
 *
 * REST/MCP 层唯一依赖面:未来 HTTP API 直接映射本契约,禁止直触 DAO/实体。
 * [normalize] 负责把 [kinds]/[operations] 收敛到 [recordTypes] 所属类型合法集合,
 * 是实现「精准筛选、防跨类型 kind 名碰撞」的单一入口。
 */
data class RecordFilter(
    val recordTypes: Set<String>? = null,
    val packages: Set<String>? = null,
    val kinds: Set<String>? = null,
    val operations: Set<String>? = null,
    val query: String? = null,
    val timeRange: LongRange? = null,
    val sort: SortOrder = SortOrder.DESC,
) {
    fun normalize(): RecordFilter {
        val singleType = recordTypes?.singleOrNull()
        return copy(
            kinds = if (singleType != null) CategoryTaxonomy.validateKinds(singleType, kinds) else kinds,
            operations = if (singleType != null) {
                CategoryTaxonomy.validateOperations(singleType, operations)
            } else {
                operations
            },
        )
    }
}

enum class SortOrder {
    DESC,
    ASC,
}

val RecordFilter.isCryptoOnly: Boolean
    get() = recordTypes == setOf(RecordTypes.CRYPTO)

val RecordFilter.isDatabaseOnly: Boolean
    get() = recordTypes == setOf(RecordTypes.DATABASE)

val RecordFilter.hasConstraint: Boolean
    get() = recordTypes != null || packages != null || kinds != null ||
        operations != null || !query.isNullOrBlank() || timeRange != null
