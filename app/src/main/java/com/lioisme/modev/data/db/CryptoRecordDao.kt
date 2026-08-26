package com.lioisme.modev.data.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 算法/数据库记录存取。所有查询按 (packageName, timestamp) 倒序,支持
 * recordType / kind / operation / package / 关键字 / 时间范围过滤。
 *
 * 复合索引 (packageName, timestamp)、(packageName, kind)、(recordType, kind)、
 * (recordType, packageName, timestamp) 由实体声明(见 [CryptoRecordEntity])。
 *
 * recordType 过滤语义:null = 不过滤(跨类型合计),非 null = 仅该监听类型(ADR-4)。
 *
 * WHERE 片段([WHERE_FILTER])为 count / delete / slice 三查询唯一来源,杜绝筛选项漂移;
 * 列表投影列([ROW_SELECT])为分页与切片共用,保证两者列集一致。
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

    /**
     * 分页查询(供 Paging3 使用)。关键字为已转义的 LIKE 模式(仓库层负责 escape)。
     * `LIMIT :limit OFFSET :offset` 由 Room 生成的 PagingSource 注入。
     */
    @Query(
        """
        SELECT * FROM crypto_records
        WHERE (:recordType IS NULL OR recordType = :recordType)
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (:kind IS NULL OR kind = :kind)
          AND (:query IS NULL OR :query = ''
                OR algorithm LIKE :query ESCAPE '\'
                OR operation LIKE :query ESCAPE '\'
                OR keyText LIKE :query ESCAPE '\'
                OR keyHex LIKE :query ESCAPE '\'
                OR inputText LIKE :query ESCAPE '\'
                OR inputHex LIKE :query ESCAPE '\'
                OR outputText LIKE :query ESCAPE '\'
                OR outputHex LIKE :query ESCAPE '\'
                OR threadName LIKE :query ESCAPE '\'
                OR sql LIKE :query ESCAPE '\'
                OR resultText LIKE :query ESCAPE '\'
                OR prefKey LIKE :query ESCAPE '\')
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observePaged(
        recordType: String?,
        packageName: String?,
        kind: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): PagingSource<Int, CryptoRecordEntity>

    /**
     * 列表投影分页(供 Paging3 使用,2026-08-17 大数据修复):
     * 仅取展示列 + SQL 侧 `substr` 预览裁剪,列表页零大字段物化。
     * 过滤/排序语义与 [observePaged] 一致,新增 operation / 时间范围维度。
     */
    @Query(
        """
        SELECT $ROW_SELECT
        FROM crypto_records
        WHERE (:recordType IS NULL OR recordType = :recordType)
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (:kind IS NULL OR kind = :kind)
          AND (:query IS NULL OR :query = ''
                OR algorithm LIKE :query ESCAPE '\'
                OR operation LIKE :query ESCAPE '\'
                OR keyText LIKE :query ESCAPE '\'
                OR keyHex LIKE :query ESCAPE '\'
                OR inputText LIKE :query ESCAPE '\'
                OR inputHex LIKE :query ESCAPE '\'
                OR outputText LIKE :query ESCAPE '\'
                OR outputHex LIKE :query ESCAPE '\'
                OR threadName LIKE :query ESCAPE '\'
                OR sql LIKE :query ESCAPE '\'
                OR resultText LIKE :query ESCAPE '\'
                OR prefKey LIKE :query ESCAPE '\')
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observePagedRows(
        recordType: String?,
        packageName: String?,
        kind: String?,
        query: String?,
        previewChars: Int,
        limit: Int,
        offset: Int,
    ): PagingSource<Int, CryptoRecordRow>

    /**
     * 集合过滤投影分页(V17 R2 RecordFilter 契约):与 [observePagedRows] 同列投影,
     * 但类型/包/类别/操作为 IN 多值语义。Room 需绑定非空集合:仓库层以哨兵计数
     * (`:xCount = 0 OR ... IN (...)`)表达"不过滤",集合为 null/空时绑定占位单例。
     */
    @Query(
        """
        SELECT $ROW_SELECT
        FROM crypto_records
        WHERE $WHERE_FILTER
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observePagedRowsByFilter(
        recordTypes: List<String>,
        packages: List<String>,
        kinds: List<String>,
        typeCount: Int,
        packageCount: Int,
        kindCount: Int,
        query: String?,
        previewChars: Int,
        limit: Int,
        offset: Int,
        operations: List<String> = emptyList(),
        opCount: Int = 0,
        timeStart: Long? = null,
        timeEnd: Long? = null,
    ): PagingSource<Int, CryptoRecordRow>

    /** 匹配 [RecordFilter] 的记录总数(契约计数入口),含 operation / 时间范围维度。 */
    @Query(
        """
        SELECT COUNT(*) AS total FROM crypto_records
        WHERE $WHERE_FILTER
        """
    )
    fun observeCountByFilter(
        recordTypes: List<String>,
        packages: List<String>,
        kinds: List<String>,
        typeCount: Int,
        packageCount: Int,
        kindCount: Int,
        query: String?,
        operations: List<String> = emptyList(),
        opCount: Int = 0,
        timeStart: Long? = null,
        timeEnd: Long? = null,
    ): Flow<Long>

    /**
     * 按 [RecordFilter] 批量删除,返回删除条数。WHERE 与 [observeCountByFilter] 完全一致
     * (共享 [WHERE_FILTER]),保证"删 = 筛";空交集由仓库层短路(见 [RecordService])。
     */
    @Query(
        """
        DELETE FROM crypto_records
        WHERE $WHERE_FILTER
        """
    )
    suspend fun deleteByFilter(
        recordTypes: List<String>,
        packages: List<String>,
        kinds: List<String>,
        operations: List<String>,
        typeCount: Int,
        packageCount: Int,
        kindCount: Int,
        opCount: Int,
        timeStart: Long?,
        timeEnd: Long?,
        query: String?,
    ): Int

    /**
     * 同步切片查询(供 MCP [RecordService.queryPage] 等非 Paging 消费者)。
     * 列投影/WHERE 与分页一致,仅排序方向按 [asc] 切换。
     */
    @Query(
        """
        SELECT $ROW_SELECT
        FROM crypto_records
        WHERE $WHERE_FILTER
        ORDER BY timestamp ASC, id ASC
        LIMIT :limit OFFSET :offset
        """
    )
    fun sliceByFilterAsc(
        recordTypes: List<String>,
        packages: List<String>,
        kinds: List<String>,
        typeCount: Int,
        packageCount: Int,
        kindCount: Int,
        query: String?,
        previewChars: Int,
        limit: Int,
        offset: Int,
        operations: List<String> = emptyList(),
        opCount: Int = 0,
        timeStart: Long? = null,
        timeEnd: Long? = null,
    ): List<CryptoRecordRow>

    /** 同步切片查询(时间倒序,列表默认方向)。 */
    @Query(
        """
        SELECT $ROW_SELECT
        FROM crypto_records
        WHERE $WHERE_FILTER
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun sliceByFilterDesc(
        recordTypes: List<String>,
        packages: List<String>,
        kinds: List<String>,
        typeCount: Int,
        packageCount: Int,
        kindCount: Int,
        query: String?,
        previewChars: Int,
        limit: Int,
        offset: Int,
        operations: List<String> = emptyList(),
        opCount: Int = 0,
        timeStart: Long? = null,
        timeEnd: Long? = null,
    ): List<CryptoRecordRow>

    /** 按记录类型分组计数(V17 R2:全类型总览,设置页/未来 REST 元信息)。 */
    @Query(
        """
        SELECT recordType, COUNT(*) AS recordCount FROM crypto_records
        GROUP BY recordType
        """
    )
    fun observeRecordTypeCounts(): Flow<List<RecordTypeCount>>

    /** 每包记录数(应用列表徽标;可限定类型)。 */
    @Query(
        """
        SELECT packageName, COUNT(*) AS recordCount FROM crypto_records
        WHERE (:recordType IS NULL OR recordType = :recordType)
        GROUP BY packageName
        """
    )
    fun observeRecordCounts(recordType: String?): Flow<List<RecordCount>>

    /** 每类别记录数(筛选菜单徽标;限定单一记录类型)。 */
    @Query(
        """
        SELECT kind AS kind, COUNT(*) AS count FROM crypto_records
        WHERE recordType = :type
        GROUP BY kind
        """
    )
    fun observeCategoryCounts(type: String): Flow<List<KindCount>>

    @Query(
        """
        SELECT DISTINCT packageName FROM crypto_records
        WHERE (:recordType IS NULL OR recordType = :recordType)
        ORDER BY packageName
        """
    )
    fun observePackages(recordType: String?): Flow<List<String>>

    @Query("DELETE FROM crypto_records WHERE packageName = :packageName")
    suspend fun clearPackage(packageName: String)

    /** 按记录类型清理(V19 T7 数据管理按类型清理)。 */
    @Query("DELETE FROM crypto_records WHERE recordType = :recordType")
    suspend fun clearByType(recordType: String)

    @Query("DELETE FROM crypto_records")
    suspend fun clearAll()

    /** 按 id 删除单条(CRUD 删除,MCP/详情页)。 */
    @Query("DELETE FROM crypto_records WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /** 按 id 取单条记录(记录详情页,唯一允许全量物化的查询)。 */
    @Query("SELECT * FROM crypto_records WHERE id = :id")
    fun observeById(id: Long): Flow<CryptoRecordEntity?>

    /**
     * 比 (ts, id) 更旧的相邻一条的 id(记录详情"上一条";2026-08-17 大数据修复:
     * 仅投影 id,杜绝邻居全量大字段物化)。
     */
    @Query(
        """
        SELECT id FROM crypto_records
        WHERE (:recordType IS NULL OR recordType = :recordType)
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (timestamp < :ts OR (timestamp = :ts AND id < :id))
        ORDER BY timestamp DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun neighborIdOlderThan(recordType: String?, packageName: String?, ts: Long, id: Long): Long?

    /** 比 (ts, id) 更新的相邻一条的 id(记录详情"下一条";投影语义同 [neighborIdOlderThan])。 */
    @Query(
        """
        SELECT id FROM crypto_records
        WHERE (:recordType IS NULL OR recordType = :recordType)
          AND (:packageName IS NULL OR packageName = :packageName)
          AND (timestamp > :ts OR (timestamp = :ts AND id > :id))
        ORDER BY timestamp ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun neighborIdNewerThan(recordType: String?, packageName: String?, ts: Long, id: Long): Long?

    /** 保留最近 [keep] 条,其余删除(全局容量治理);返回删除条数。 */
    @Query(
        """
        DELETE FROM crypto_records WHERE id NOT IN (
            SELECT id FROM crypto_records ORDER BY timestamp DESC LIMIT :keep
        )
        """
    )
    suspend fun trim(keep: Int): Int

    /** 保留某包最近 [keep] 条,其余删除(每包容量治理);返回删除条数。 */
    @Query(
        """
        DELETE FROM crypto_records WHERE packageName = :packageName AND id NOT IN (
            SELECT id FROM crypto_records
            WHERE packageName = :packageName
            ORDER BY timestamp DESC LIMIT :keep
        )
        """
    )
    suspend fun trimPackage(packageName: String, keep: Int): Int

    @Query("SELECT COUNT(*) FROM crypto_records WHERE packageName = :packageName")
    suspend fun count(packageName: String): Long

    /** 每包记录数一次快照(宿主管线每包容量治理用)。 */
    @Query(
        """
        SELECT packageName, COUNT(*) AS recordCount FROM crypto_records
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
            path, destPath, command, fileSize, prefKey
            """

        /**
         * count / delete / slice 三查询共享 WHERE 片段(单一事实源,杜绝筛选项漂移):
         * 集合维度以哨兵计数(`:xCount = 0 OR ... IN (...)`)表达"不过滤",
         * 空集合由仓库层绑定占位单例;时间范围以 NULL 哨兵;关键字 LIKE 转义由仓库层负责。
         */
        const val WHERE_FILTER = """
            (:typeCount = 0 OR recordType IN (:recordTypes))
            AND (:packageCount = 0 OR packageName IN (:packages))
            AND (:kindCount = 0 OR kind IN (:kinds))
            AND (:opCount = 0 OR operation IN (:operations))
            AND (:timeStart IS NULL OR timestamp >= :timeStart)
            AND (:timeEnd IS NULL OR timestamp <= :timeEnd)
            AND (:query IS NULL OR :query = ''
                 OR algorithm LIKE :query ESCAPE '\'
                 OR operation LIKE :query ESCAPE '\'
                 OR keyText LIKE :query ESCAPE '\'
                 OR keyHex LIKE :query ESCAPE '\'
                 OR inputText LIKE :query ESCAPE '\'
                 OR inputHex LIKE :query ESCAPE '\'
                 OR outputText LIKE :query ESCAPE '\'
                 OR outputHex LIKE :query ESCAPE '\'
                 OR threadName LIKE :query ESCAPE '\'
                  OR sql LIKE :query ESCAPE '\'
                  OR resultText LIKE :query ESCAPE '\'
                  OR path LIKE :query ESCAPE '\'
                  OR destPath LIKE :query ESCAPE '\'
                  OR command LIKE :query ESCAPE '\'
                  OR prefKey LIKE :query ESCAPE '\')
             """
    }
}

/** 每包记录计数(应用列表徽标)。 */
data class RecordCount(
    val packageName: String,
    @ColumnInfo(name = "recordCount") val count: Long,
)

/** 按记录类型的记录计数(V17 R2)。 */
data class RecordTypeCount(
    val recordType: String,
    @ColumnInfo(name = "recordCount") val count: Long,
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
)
