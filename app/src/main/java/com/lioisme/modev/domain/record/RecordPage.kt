package com.lioisme.modev.domain.record


/**
 * 同步切片结果([RecordService.queryPage] 返回值):与流式分页互补,供 transport 无关消费者。
 *
 * @param items   当页行模型(已映射 [RowModel])。
 * @param total   匹配 [RecordFilter] 的总记录数。
 * @param page    当前页码(从 0 起)。
 * @param pageSize 每页大小。
 */
data class RecordPage(
    val items: List<RowModel>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)
