package com.lioisme.modev.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 画像库 DAO(V47 T2)。
 *
 * 消费面受限:`spoofProfileDao()` 调用白名单 = ModevApp(装配)与该画像仓库自身,
 * 越界即红(`RecordDaoAccessGateTest` 已把本 accessor 纳入扫描面)——
 * UI 与 MCP 一律经 `SpoofProfileService` 契约,与记录面同一红线。
 */
@Dao
interface SpoofProfileDao {

    /** 全量观测(墓碑行排除;创建序稳定,便于 UI 行不跳动)。 */
    @Query("SELECT * FROM ${SpoofProfileEntity.TABLE_NAME} WHERE deleted = 0 ORDER BY createTime ASC")
    fun observeAll(): Flow<List<SpoofProfileEntity>>

    @Query("SELECT * FROM ${SpoofProfileEntity.TABLE_NAME} WHERE deleted = 0 ORDER BY createTime ASC")
    suspend fun getAll(): List<SpoofProfileEntity>

    @Query("SELECT * FROM ${SpoofProfileEntity.TABLE_NAME} WHERE id = :id")
    suspend fun getById(id: String): SpoofProfileEntity?

    @Query("SELECT * FROM ${SpoofProfileEntity.TABLE_NAME} WHERE builtinSeedId = :seedId LIMIT 1")
    suspend fun getBySeedId(seedId: String): SpoofProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: SpoofProfileEntity): Long

    @Insert
    suspend fun insert(entity: SpoofProfileEntity)

    @Update
    suspend fun update(entity: SpoofProfileEntity)

    @Query("DELETE FROM ${SpoofProfileEntity.TABLE_NAME} WHERE id = :id")
    suspend fun deleteById(id: String)
}
