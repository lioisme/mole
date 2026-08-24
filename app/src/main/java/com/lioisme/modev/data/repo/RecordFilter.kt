package com.lioisme.modev.data.repo

import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.RecordTypes

/**
 * 统一查询契约(V17 R2 记录底层一体化,V22 增强):
 * 全字段 null/空集 = 不过滤;集合为多值语义(IN);query 为关键字(仓库层负责 SQL LIKE 转义)。
 *
 * 新增维度:
 * - [operations]:按具体操作阶段名过滤([CryptoOperation]/[SqlOperation] 名,跨类型自动收敛);
 * - [timeRange]:起止时间戳闭区间(毫秒),null = 不限;
 * - [sort]:时间排序方向(默认 [SortOrder.DESC],列表/详情邻接通用)。
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
    /**
     * 返回收敛后的副本:将 [kinds]/[operations] 按 [recordTypes] 作用域收敛。
     * - 未限定类型(recordTypes null/多类型):不收敛(跨类型查询保留原集合);
     * - 单类型:剔除不属于该类型的项,空交集保留为空 `Set`(调用方据此短路为「无匹配」)。
     * 其余字段原样透传。[RecordFilter] 本身不可变,本方法产生新实例。
     */
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

/** 时间排序方向(列表/邻接通用)。 */
enum class SortOrder {
    /** 时间倒序(最新在前,默认)。 */
    DESC,

    /** 时间正序(最旧在前)。 */
    ASC,
}

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
