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
    /**
     * 把 [kinds]/[operations] 收敛到所选类型的合法名集(V45 T1.8 / QA-6 补全):
     * - 单类型:该类型合法集(原语义不变);
     * - **多类型:各类型合法集的并集** —— 旧形态在多类型下完全不收敛,
     *   跨类同名 kind(如 `OPEN` 同属 SQL 与 Prefs 两域)可命中未选类型的行,
     *   且筛选菜单在「记录类型 + 类别」双组单选下会静默返回全集或空集;
     * - 未选类型 / 空集:不过滤(维持「缺省维度不出列」口径);
     * - 未知类型名(协议漂移):不收敛(由调用侧类型校验负责拒绝)。
     */
    fun normalize(): RecordFilter {
        val types = recordTypes?.filter { CategoryTaxonomy.isKnownType(it) }
        if (types.isNullOrEmpty()) return this
        return copy(
            kinds = CategoryTaxonomy.convergeKinds(types, kinds),
            operations = CategoryTaxonomy.convergeOperations(types, operations),
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
