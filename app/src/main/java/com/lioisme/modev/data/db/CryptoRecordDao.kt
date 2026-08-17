package com.lioisme.modev.data.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 算法记录存取。所有查询按 (packageName, timestamp) 倒序,支持 kind / 关键字过滤。
 * 复合索引 (packageName, timestamp)、(packageName, kind) 由实体声明。
 */
@Dao
interface CryptoRecordDao {

    @Insert
    suspend fun insert(record: CryptoRecordEntity)

    /** 插入并返回自增 id(详情页邻接定位/测试用)。 */
    @Insert
    suspend fun insertReturningId(record: CryptoRecordEntity): Long

    /** 批量插入(宿主管线批量落库,减少事务次数);冲突 REPLACE = 断线重放幂等。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<CryptoRecordEntity>)

    /** 最近 [limit] 条(可先按包名过滤)。 */
    @Query(
        """
        SELECT * FROM crypto_records
        WHERE (:packageName IS NULL OR packageName = :packageName)
          AND (:kind IS NULL OR kind = :kind)
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun observe(packageName: String?, kind: String?, limit: Int): Flow<List<CryptoRecordEntity>>

    /**
     * 分页查询(供 Paging3 使用)。关键字为已转义的 LIKE 模式(仓库层负责 escape)。
     * `LIMIT :limit OFFSET :offset` 由 Room 生成的 PagingSource 注入。
     */
    @Query(
        """
        SELECT * FROM crypto_records
        WHERE (:packageName IS NULL OR packageName = :packageName)
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
               OR threadName LIKE :query ESCAPE '\')
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observePaged(
        packageName: String?,
        kind: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): PagingSource<Int, CryptoRecordEntity>

    /**
     * 列表投影分页(供 Paging3 使用,2026-08-17 大数据修复):
     * 仅取展示列 + SQL 侧 `substr` 预览裁剪,列表页零大字段物化。
     * 过滤/排序语义与 [observePaged] 完全一致。
     */
    @Query(
        """
        SELECT id, packageName, kind, operation, algorithm, threadName, direction,
               timestamp, keyAlias,
               substr(keyHex, 1, :previewChars) AS keyPreview,
               substr(inputHex, 1, :previewChars) AS inputPreview,
               substr(outputHex, 1, :previewChars) AS outputPreview,
               keyTruncated, inputTruncated, outputTruncated
        FROM crypto_records
        WHERE (:packageName IS NULL OR packageName = :packageName)
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
               OR threadName LIKE :query ESCAPE '\')
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observePagedRows(
        packageName: String?,
        kind: String?,
        query: String?,
        previewChars: Int,
        limit: Int,
        offset: Int,
    ): PagingSource<Int, CryptoRecordRow>

    /** 每包记录数(应用列表徽标)。 */
    @Query(
        """
        SELECT packageName, COUNT(*) AS recordCount FROM crypto_records
        GROUP BY packageName
        """
    )
    fun observeRecordCounts(): Flow<List<RecordCount>>

    @Query("SELECT DISTINCT packageName FROM crypto_records ORDER BY packageName")
    fun observePackages(): Flow<List<String>>

    @Query("DELETE FROM crypto_records WHERE packageName = :packageName")
    suspend fun clearPackage(packageName: String)

    @Query("DELETE FROM crypto_records")
    suspend fun clearAll()

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
        WHERE (:packageName IS NULL OR packageName = :packageName)
          AND (timestamp < :ts OR (timestamp = :ts AND id < :id))
        ORDER BY timestamp DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun neighborIdOlderThan(packageName: String?, ts: Long, id: Long): Long?

    /** 比 (ts, id) 更新的相邻一条的 id(记录详情"下一条";投影语义同 [neighborIdOlderThan])。 */
    @Query(
        """
        SELECT id FROM crypto_records
        WHERE (:packageName IS NULL OR packageName = :packageName)
          AND (timestamp > :ts OR (timestamp = :ts AND id > :id))
        ORDER BY timestamp ASC, id ASC
        LIMIT 1
        """
    )
    suspend fun neighborIdNewerThan(packageName: String?, ts: Long, id: Long): Long?

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
        GROUP BY packageName
        """
    )
    suspend fun counts(): List<RecordCount>
}

/** 每包记录计数(应用列表徽标)。 */
data class RecordCount(
    val packageName: String,
    @ColumnInfo(name = "recordCount") val count: Long,
)

/**
 * 列表行投影(2026-08-17 大数据修复):仅展示列 + SQL 侧预览裁剪,
 * 列表/邻接场景零大字段物化。大字段 hex 仅存于实体,由详情页按需全量读取。
 */
data class CryptoRecordRow(
    val id: Long,
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
)