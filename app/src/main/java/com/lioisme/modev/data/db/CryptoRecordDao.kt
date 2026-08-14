package com.lioisme.modev.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 算法记录存取。所有查询按 (packageName, timestamp) 倒序,支持 kind 过滤与条数上限。
 */
@Dao
interface CryptoRecordDao {

    @Insert
    suspend fun insert(record: CryptoRecordEntity)

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

    @Query("SELECT DISTINCT packageName FROM crypto_records ORDER BY packageName")
    fun observePackages(): Flow<List<String>>

    @Query("DELETE FROM crypto_records WHERE packageName = :packageName")
    suspend fun clearPackage(packageName: String)

    @Query("DELETE FROM crypto_records")
    suspend fun clearAll()

    /** 保留最近 [keep] 条,其余删除(容量治理)。 */
    @Query(
        """
        DELETE FROM crypto_records WHERE id NOT IN (
            SELECT id FROM crypto_records ORDER BY timestamp DESC LIMIT :keep
        )
        """
    )
    suspend fun trim(keep: Int)

    @Query("SELECT COUNT(*) FROM crypto_records WHERE packageName = :packageName")
    suspend fun count(packageName: String): Long
}