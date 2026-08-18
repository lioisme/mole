package com.lioisme.modev.data.repo

/**
 * 统一查询契约(V17 R2 记录底层一体化):
 * 全字段 null/空集 = 不过滤;集合为多值语义(IN);query 为关键字(仓库层负责 SQL LIKE 转义)。
 * REST/MCP 层唯一依赖面:未来 HTTP API 直接映射本契约,禁止直触 DAO/实体。
 */
data class RecordFilter(
    val recordTypes: Set<String>? = null,
    val packages: Set<String>? = null,
    val kinds: Set<String>? = null,
    val query: String? = null,
)
