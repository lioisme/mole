package com.lioisme.modev.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 关键词库 DAO(V37 T2.1):全量流观测(内存快照缓存驱动)+ 幂等增删改。
 * [insertIgnore] 以 text 唯一索引去重(冲突返回 -1,调用方据此提示重复)。
 */
@Dao
interface KeywordLibraryDao {

    @Query("SELECT * FROM keyword_library ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keyword_library ORDER BY createdAt DESC")
    suspend fun getAll(): List<KeywordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: KeywordEntity): Long

    @Insert
    suspend fun insert(entity: KeywordEntity): Long

    @Update
    suspend fun update(entity: KeywordEntity)

    @Query("DELETE FROM keyword_library WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM keyword_library")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM keyword_library")
    fun observeCount(): Flow<Int>
}
