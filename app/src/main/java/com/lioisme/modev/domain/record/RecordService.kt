package com.lioisme.modev.domain.record

import androidx.paging.PagingData
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.data.mapping.RecordDetailModel
import com.lioisme.modev.data.mapping.RowModel
import kotlinx.coroutines.flow.Flow

/**
 * 记录读写服务(单一门面,V22):所有查询 / 增 / 删 / 改 / 统计经本接口暴露,DAO 不对外暴露。
 * 前端(ViewModel)与 MCP(V38)均仅依赖本接口 + [RecordFilter] + [CategoryTaxonomy],
 * 禁止直触 DAO / 实体(V38 D1:写入面经纯 Kotlin [RecordDraft],domain 零 data.db 依赖)。
 *
 * 设计约定:
 * - 读:流式分页([observe])供 UI;同步切片([queryPage])供 MCP / 导出(返回总量 + 当页);
 * - 写:单行 [create]/[update]/[delete];批量 [deleteByFilter](共享 count 的 WHERE,保证"删=筛");
 * - 空 kind/operation 交集(经 [RecordFilter.normalize] 收敛)在仓库层短路为零结果,
 *   避免 `kindCount=0` 误判为"不过滤";
 * - 全字段空的 [deleteByFilter] 视为无约束,拒绝整表删除(显式整清请用 [clearAll])。
 */
interface RecordService {

    /** 流式分页记录(时间倒序,支持包/类型/类别/操作/关键字/时间范围过滤),供 UI 收集。 */
    fun observe(filter: RecordFilter): Flow<PagingData<RowModel>>

    /** 同步切片查询(供 MCP / 导出):返回当页行 + 总量 + 页码,不入 Paging 冷流。 */
    suspend fun queryPage(filter: RecordFilter, page: Int, pageSize: Int): RecordPage

    /** 匹配 [RecordFilter] 的记录总数(分页元信息)。 */
    fun count(filter: RecordFilter): Flow<Long>

    /** 单条记录(详情页),不可解码/已清理返回 null。 */
    fun get(id: Long): Flow<RecordDetailModel?>

    /** 相邻记录 id([newer]=true 取更新、false 取更旧),用于详情"上一条/下一条"。 */
    suspend fun neighbor(id: Long, newer: Boolean): Long?

    /** 新增单条(MCP 导入 / 手动补录),返回自增 id。 */
    suspend fun create(record: RecordDraft): Long

    /** 更新单条(按主键整行覆盖;MCP 编辑),成功返回 true。 */
    suspend fun update(id: Long, record: RecordDraft): Boolean

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

    /** 已出现记录的应用包名列表(筛选菜单 / 应用列表)。 */
    fun observePackages(): Flow<List<String>>

    /** 按记录类型分组计数(全类型总览)。 */
    fun observeTypeCounts(): Flow<List<RecordTypeCount>>

    /** 指定类型的每类别计数(筛选菜单徽标)。 */
    suspend fun categoryCounts(type: RecordType): Map<String, Long>
}
