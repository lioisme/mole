package com.lioisme.modev.domain.record

import androidx.paging.PagingData
import com.lioisme.modev.core.model.MonitoredRecord
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.diagnostics.PipelineStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 记录读写服务(单一门面,V22):所有查询 / 增 / 删 / 改 / 统计经本接口暴露,DAO 不对外暴露。
 * 前端(ViewModel)与 MCP(V38)均仅依赖本接口 + [RecordFilter] + [CategoryTaxonomy],
 * 禁止直触 DAO / 实体(V38 D1:写入面纯 Kotlin,V45 T2.5 起直接用类型化模型族
 * [com.lioisme.modev.core.model.MonitoredRecord],domain 零 data.db 依赖)。
 *
 * 设计约定:
 * - 读:流式分页([observe])供 UI;同步切片([queryPage])供 MCP / 导出(返回总量 + 当页);
 * - 写:单行 [create]/[update]/[delete];批量 [deleteByFilter](共享 count 的 WHERE,保证"删=筛");
 * - 空 kind/operation 交集(经 [RecordFilter.normalize] 收敛)在仓库层短路为零结果,
 *   避免 `kindCount=0` 误判为"不过滤";
 * - 全字段空的 [deleteByFilter] 视为无约束,拒绝整表删除(显式整清请用 [clearAll])。
 */
interface RecordService {

    /**
     * 流式分页记录(支持包/类型/类别/操作/关键字/时间范围过滤;排序方向随
     * [RecordFilter.sort],默认 DESC 时间线,V44 Q1 与切片查询同一契约),供 UI 收集。
     */
    fun observe(filter: RecordFilter): Flow<PagingData<RowModel>>

    /** 同步切片查询(供 MCP / 导出):返回当页行 + 总量 + 页码,不入 Paging 冷流。 */
    suspend fun queryPage(filter: RecordFilter, page: Int, pageSize: Int): RecordPage

    /** 匹配 [RecordFilter] 的记录总数(分页元信息)。 */
    fun count(filter: RecordFilter): Flow<Long>

    /** 单条记录(详情页),不可解码/已清理返回 null。 */
    fun get(id: Long): Flow<RecordDetailModel?>

    /**
     * 记录原始 JSON 快照(详情页「复制/导出」唯一出口,V45 ISO-2):
     * 序列化实现住在数据层,消费方不再 import `data.mapping` 编码器;
     * 纯计算无 IO,调用方自行决定调度线程。
     */
    fun exportJson(record: RecordDetailModel): String

    /** 相邻记录 id([newer]=true 取更新、false 取更旧),用于详情"上一条/下一条"。 */
    suspend fun neighbor(id: Long, newer: Boolean): Long?

    /**
     * 新增单条(MCP 导入 / 手动补录),返回自增 id。
     * 入参 = [MonitoredRecord] 类型化模型族(V45 T2.5:与注入侧 / wire 编解码 / 读取投影
     * 同一底层模型,写入面不再另立「逐列镜像实体」的第三份平铺副本)。
     */
    suspend fun create(record: MonitoredRecord): Long

    /** 更新单条(按主键整行覆盖;MCP 编辑),成功返回 true。 */
    suspend fun update(id: Long, record: MonitoredRecord): Boolean

    /** 按 id 删除单条,成功返回 true。 */
    suspend fun delete(id: Long): Boolean

    /** 按 [RecordFilter] 批量删除(返回删除条数;无约束时返回 0,不整表删除)。 */
    suspend fun deleteByFilter(filter: RecordFilter): Long

    /** 按包清理(数据管理)。 */
    suspend fun clearPackage(packageName: String)

    /** 按记录类型清理(数据管理)。 */
    suspend fun clearByType(recordType: String)

    /** 清空全部(数据管理,危险操作)。 */
    suspend fun clearAll()

    /** 存储占用字节(主库 + wal + shm 三文件和;V38 上提为契约成员,数据管理/MCP 共用)。 */
    suspend fun storageUsage(): Long

    /**
     * 按记录类型分组的每应用记录计数(V44 上提为契约成员:应用列表徽标 /
     * 监控页类型计数 / MCP apps_list、app_monitor 唯一计数出口;
     * [recordType] null = 全类型合计)。
     */
    fun observeRecordCounts(recordType: String? = null): Flow<Map<String, Long>>

    /** 按记录类型分组计数(全类型总览)。 */
    fun observeTypeCounts(): Flow<List<RecordTypeCount>>

    /**
     * 单包按记录类型分组计数(V45 ST-6:监控页 10 个类型徽标与 MCP `app_monitor`
     * 的唯一计数出口 —— 一次 `GROUP BY` 取代逐类型 10 次 COUNT;
     * 缺席键 = 该类型零记录)。
     */
    fun observeTypeCountsForPackage(packageName: String): Flow<Map<String, Long>>

    /** 指定类型的每类别计数(筛选菜单徽标)。 */
    suspend fun categoryCounts(type: RecordType): Map<String, Long>

    /**
     * 捕获管线统计快照流(接收/丢弃/写失败/裁剪 + 通道四类拒绝)。
     * V45 ML-2:原为 `LogRepository.statsFlow` 契约外成员 —— UI 与 MCP 为取它而注入
     * **具体仓库类**,唯一合格门面的契约被实现类型旁路;现上提为契约成员,两侧只依赖本接口。
     */
    val stats: StateFlow<PipelineStats>

    companion object {
        /**
         * 双端统一页大小(V44 Q4 单一事实源:UI Paging 页与 MCP records_query
         * 默认 pageSize 同源,消灭 60/50 账面漂移;REST 复用同值)。
         */
        const val DEFAULT_PAGE_SIZE = 60
    }
}
