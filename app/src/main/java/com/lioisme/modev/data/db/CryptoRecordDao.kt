package com.lioisme.modev.data.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.lioisme.modev.domain.record.RecordTypeCount
import kotlinx.coroutines.flow.Flow

/**
 * 统一记录表存取(表承载 10 类记录,名 = [CryptoRecordEntity.TABLE_NAME])。
 *
 * 查询形态(,R1「底层数据架构高效」):
 * - **谓词不写在 SQL 里**——列表 / 计数 / 删除候选 id / 详情邻接四路的谓词一律由
 *   [RecordQueryFactory] 从 [com.lioisme.modev.domain.record.RecordFilter] 派生
 *   (缺省维度不出列,SQLite 才可能选到索引),DAO 侧只留 `@RawQuery` 执行位;
 * - 「删 = 筛 = 计数」口径单一由「同一生成器 + 同一投影列」保证;`@RawQuery` 不承担
 *   DML,故删除 = [idsByQuery] 取同谓词 id → [deleteByIds] 按主键分片删;
 * - 列表投影列([ROW_SELECT])为分页与切片共用,保证两者列集一致,
 *   且大字段(hex/base64/正文/堆栈/结果集)恒不进列表,仅 SQL 侧 `substr` 预览。
 *
 * 索引(见 [CryptoRecordEntity]):(packageName, timestamp)、(packageName, kind)、
 * (recordType, kind)、(recordType, packageName, timestamp)、(timestamp) 与唯一
 * (packageName, processName, seq)。**索引是否真被命中由真机 `EXPLAIN QUERY PLAN`
 * 门禁裁决**(见 `RecordQueryPlanTest`,Robolectric 会剥离 EXPLAIN 前缀不可用)。
 */
@Dao
interface CryptoRecordDao {

    @Insert
    suspend fun insert(record: CryptoRecordEntity)

    /** 插入并返回自增 id(详情页邻接定位/测试用)。 */
    @Insert
    suspend fun insertReturningId(record: CryptoRecordEntity): Long

    /**
     * 批量插入(宿主管线批量落库,减少事务次数);冲突 REPLACE = 断线重放幂等。
     * V23 T6.1 显式 [Transaction]:整批单事务原子落库(部分失败全回滚),
     * ACK 仅在整批成功后回执,与 LogRepository 重试语义严格对齐。
     */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CryptoRecordEntity>)

    /** 单条插入或更新(主键冲突时覆盖;供 MCP 导入/手动补录复用)。 */
    @Upsert
    suspend fun upsert(record: CryptoRecordEntity)

    // ------------------------------------------------------------------
    // V45 T1.1(ST-1 / ST-2):谓词派生查询四路(列表 / 计数 / 删除前置 id / 详情邻接)。
    // 谓词唯一生成器 = [RecordQueryFactory]——缺省维度不出列,使 SQLite 可选索引;
    // 旧 `@Query + $WHERE_FILTER` 哨兵形态在本批删除(见各方法 KDoc)。
    // ------------------------------------------------------------------

    /** 流式分页记录行(谓词与排序由查询对象承载;[RecordQueryFactory.rows])。 */
    @RawQuery(observedEntities = [CryptoRecordEntity::class])
    fun pagedRows(query: SupportSQLiteQuery): PagingSource<Int, CryptoRecordRow>

    /** 同步切片记录行(MCP / 导出等非 Paging 消费者)。 */
    @RawQuery(observedEntities = [CryptoRecordEntity::class])
    suspend fun sliceRows(query: SupportSQLiteQuery): List<CryptoRecordRow>

    /** 匹配计数(与 [pagedRows] / [sliceRows] 同谓词)。 */
    @RawQuery(observedEntities = [CryptoRecordEntity::class])
    fun countByQuery(query: SupportSQLiteQuery): Flow<Long>

    /** 匹配 id 集(删除前置:`@RawQuery` 不承担 DML,删走 [deleteByIds])。 */
    @RawQuery(observedEntities = [CryptoRecordEntity::class])
    suspend fun idsByQuery(query: SupportSQLiteQuery): List<Long>

    /** 邻接 id(keyset,与列表同谓词)。 */
    @RawQuery(observedEntities = [CryptoRecordEntity::class])
    suspend fun neighborIdByQuery(query: SupportSQLiteQuery): Long?

    /** 按记录类型分组计数(谓词同 [countByQuery];V45 ST-6 一次 GROUP BY 出全部类型)。 */
    @RawQuery(observedEntities = [CryptoRecordEntity::class])
    fun typeCountsByQuery(query: SupportSQLiteQuery): Flow<List<RecordTypeCount>>

    /**
     * 按主键批量删除(「删 = 筛」的后半段:候选 id 来自 [idsByQuery] 同一谓词);
     * 单事务,调用方按 ≤500 分片(SQLite 变量数上限)。
     */
    @Transaction
    @Query("DELETE FROM ${CryptoRecordEntity.TABLE_NAME} WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    /** 按记录类型分组计数(V17 R2:全类型总览,设置页/未来 REST 元信息)。 */
    @Query(
        """
        SELECT recordType, COUNT(*) AS count FROM ${CryptoRecordEntity.TABLE_NAME}
        GROUP BY recordType
        """
    )
    fun observeRecordTypeCounts(): Flow<List<RecordTypeCount>>

    /** 每包记录数(应用列表徽标;可限定类型)。 */
    @Query(
        """
        SELECT packageName, COUNT(*) AS recordCount FROM ${CryptoRecordEntity.TABLE_NAME}
        WHERE (:recordType IS NULL OR recordType = :recordType)
        GROUP BY packageName
        """
    )
    fun observeRecordCounts(recordType: String?): Flow<List<RecordCount>>

    /** 每类别记录数(筛选菜单徽标;限定单一记录类型)。 */
    @Query(
        """
        SELECT kind AS kind, COUNT(*) AS count FROM ${CryptoRecordEntity.TABLE_NAME}
        WHERE recordType = :type
        GROUP BY kind
        """
    )
    fun observeCategoryCounts(type: String): Flow<List<KindCount>>

    // 包维度观测出口恒为 [packagesOver](容量治理)与 [observeRecordCounts](列表徽标)
    // 两处;不要再补「按类型枚举有哪些包」的第三条查询(生产零调用点,只会长出死代码)。

    @Query("DELETE FROM ${CryptoRecordEntity.TABLE_NAME} WHERE packageName = :packageName")
    suspend fun clearPackage(packageName: String)

    /** 按记录类型清理(V19 T7 数据管理按类型清理)。 */
    @Query("DELETE FROM ${CryptoRecordEntity.TABLE_NAME} WHERE recordType = :recordType")
    suspend fun clearByType(recordType: String)

    @Query("DELETE FROM ${CryptoRecordEntity.TABLE_NAME}")
    suspend fun clearAll()

    /** 按 id 删除单条(CRUD 删除,MCP/详情页)。 */
    @Query("DELETE FROM ${CryptoRecordEntity.TABLE_NAME} WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** 按 id 取单条记录(记录详情页,唯一允许全量物化的查询)。 */
    @Query("SELECT * FROM ${CryptoRecordEntity.TABLE_NAME} WHERE id = :id")
    fun observeById(id: Long): Flow<CryptoRecordEntity?>

    /**
     * 全局容量截断点(V45 T1.6 / ST-7):第 `keep + 1` 新的 `(timestamp, id)`,
     * 行数 ≤ keep 时 null。排序键与列表/详情完全一致(`timestamp DESC, id DESC`),
     * 故「保留的恒是列表首屏所见」;`ORDER BY … LIMIT 1 OFFSET :keep` 走
     * `index_…_timestamp`(旧 `id NOT IN (SELECT …)` 需物化命中集再全表探测)。
     */
    @Query(
        """
        SELECT timestamp, id FROM ${CryptoRecordEntity.TABLE_NAME}
        ORDER BY timestamp DESC, id DESC LIMIT 1 OFFSET :keep
        """
    )
    suspend fun globalTrimCutoff(keep: Int): TrimCutoff?

    /** 删除截断行及其更早的记录(索引范围删除;截断点本身即第 keep+1 行,故恒被删除)。 */
    @Query(
        """
        DELETE FROM ${CryptoRecordEntity.TABLE_NAME}
        WHERE timestamp < :timestamp OR (timestamp = :timestamp AND id <= :id)
        """
    )
    suspend fun deleteBefore(timestamp: Long, id: Long): Int

    /**
     * 保留最近 [keep] 条,其余删除(全局容量治理入口, = 截断点 + 范围删两段);
     * 两步之间即使被并发写入插入更新行,下一治理周期自会纠正,故不必包事务。
     */
    suspend fun trim(keep: Int): Int = globalTrimCutoff(keep)?.let { deleteBefore(it.timestamp, it.id) } ?: 0

    /** 单包容量截断点(走 `index_…_packageName_timestamp` 的包内时间序)。 */
    @Query(
        """
        SELECT timestamp, id FROM ${CryptoRecordEntity.TABLE_NAME}
        WHERE packageName = :packageName
        ORDER BY timestamp DESC, id DESC LIMIT 1 OFFSET :keep
        """
    )
    suspend fun packageTrimCutoff(packageName: String, keep: Int): TrimCutoff?

    /** 删除某包截断行及其更早的记录(截断点 = 该包第 keep+1 新行,恒被删除)。 */
    @Query(
        """
        DELETE FROM ${CryptoRecordEntity.TABLE_NAME}
        WHERE packageName = :packageName
          AND (timestamp < :timestamp OR (timestamp = :timestamp AND id <= :id))
        """
    )
    suspend fun deletePackageBefore(packageName: String, timestamp: Long, id: Long): Int

    /** 保留某包最近 [keep] 条,其余删除(每包容量治理);返回删除条数。 */
    suspend fun trimPackage(packageName: String, keep: Int): Int =
        packageTrimCutoff(packageName, keep)?.let { deletePackageBefore(packageName, it.timestamp, it.id) } ?: 0

    /** 超过 [limit] 条的包(容量治理探针:GROUP BY + HAVING,结果集受 offenders 数约束)。 */
    @Query(
        """
        SELECT packageName, COUNT(*) AS recordCount FROM ${CryptoRecordEntity.TABLE_NAME}
        GROUP BY packageName HAVING COUNT(*) > :limit
        """
    )
    suspend fun packagesOver(limit: Int): List<RecordCount>

    @Query("SELECT COUNT(*) FROM ${CryptoRecordEntity.TABLE_NAME} WHERE packageName = :packageName")
    suspend fun count(packageName: String): Long

    /**
     * 每包记录数一次快照(V45 契约归属标注:容量治理走有界的 [packagesOver],
     * 本查询保留给观测/测试与未来 REST 元信息,不在写入热路径上)。
     */
    @Query(
        """
        SELECT packageName, COUNT(*) AS recordCount FROM ${CryptoRecordEntity.TABLE_NAME}
        WHERE (:recordType IS NULL OR recordType = :recordType)
        GROUP BY packageName
        """
    )
    suspend fun counts(recordType: String?): List<RecordCount>

    companion object {
        /** 列表/切片投影列(展示列 + SQL 侧预览裁剪),分页与切片共用保证列集一致。 */
        const val ROW_SELECT = """
            id, recordType, packageName, kind, operation, algorithm, threadName, direction,
            timestamp, keyAlias,
            substr(keyHex, 1, :previewChars) AS keyPreview,
            substr(inputHex, 1, :previewChars) AS inputPreview,
            substr(outputHex, 1, :previewChars) AS outputPreview,
            keyTruncated, inputTruncated, outputTruncated,
            substr(sql, 1, :previewChars) AS sqlPreview,
            rowCount, affectedCount, rowId,
            path, destPath, command, fileSize, prefKey,
            proxyHost, vpnActive,
            dialogTitle, dialogMessage, dialogKeywords, dialogIntercepted,
            widgetText, widgetClassName, activityName,
            intentAction, intentData, intentExtrasJson, intentFlags,
            logcatTag, logcatLevel, logcatMessage, signatureHash,
            exitCode, processPid, crashType, crashBlocked, screenRecordBlocked,
            interventionAction
            """

    }
}

/** 每包记录计数(应用列表徽标)。 */
data class RecordCount(
    val packageName: String,
    @ColumnInfo(name = "recordCount") val count: Long,
)

/**
 * 容量治理截断点:第 keep+1 行的 `(timestamp, id)`,
 * 与列表排序键同构 ⇒ 「保留集 = 列表首屏所见」在并列同戳下仍精确。
 */
data class TrimCutoff(
    val timestamp: Long,
    val id: Long,
)

/** 按类别的记录计数(筛选菜单徽标)。 */
data class KindCount(
    val kind: String,
    @ColumnInfo(name = "count") val count: Long,
)

/**
 * 列表行投影(2026-08-17 大数据修复):仅展示列 + SQL 侧预览裁剪,
 * 列表/邻接场景零大字段物化。大字段 hex 仅存于实体,由详情页按需全量读取。
 */
data class CryptoRecordRow(
    val id: Long,
    val recordType: String,
    val packageName: String,
    val kind: String,
    val operation: String,
    val algorithm: String,
    val threadName: String,
    val direction: Int? = null,
    val timestamp: Long,
    val keyAlias: String? = null,
    @ColumnInfo(name = "keyPreview") val keyPreview: String? = null,
    @ColumnInfo(name = "inputPreview") val inputPreview: String? = null,
    @ColumnInfo(name = "outputPreview") val outputPreview: String? = null,
    @ColumnInfo(name = "keyTruncated") val keyTruncated: Boolean = false,
    @ColumnInfo(name = "inputTruncated") val inputTruncated: Boolean = false,
    @ColumnInfo(name = "outputTruncated") val outputTruncated: Boolean = false,
    @ColumnInfo(name = "sqlPreview") val sqlPreview: String? = null,
    @ColumnInfo(name = "rowCount") val rowCount: Int? = null,
    @ColumnInfo(name = "affectedCount") val affectedCount: Int? = null,
    @ColumnInfo(name = "rowId") val rowId: Long? = null,
    // 行为监听投影列(V27;crypto/db 行为 NULL)
    val path: String? = null,
    val destPath: String? = null,
    val command: String? = null,
    val fileSize: Long? = null,
    // 偏好监听投影列(V28;prefs 行承载配置键,其余 NULL)
    val prefKey: String? = null,
    // 网络环境监听投影列
    val proxyHost: String? = null,
    val vpnActive: Int? = null,
    // UI 交互监听投影列
    val dialogTitle: String? = null,
    val dialogMessage: String? = null,
    val dialogKeywords: String? = null,
    val dialogIntercepted: Int? = null,
    val widgetText: String? = null,
    val widgetClassName: String? = null,
    val activityName: String? = null,
    val intentAction: String? = null,
    val intentData: String? = null,
    val intentExtrasJson: String? = null,
    val intentFlags: Int? = null,
    // 系统信息监听投影列
    val logcatTag: String? = null,
    val logcatLevel: String? = null,
    val logcatMessage: String? = null,
    val signatureHash: String? = null,
    // 进程控制投影列
    val exitCode: Int? = null,
    val processPid: Int? = null,
    val crashType: String? = null,
    val crashBlocked: Int? = null,
    val screenRecordBlocked: Int? = null,
    // V34 审计增强
    val interventionAction: String? = null,
)
