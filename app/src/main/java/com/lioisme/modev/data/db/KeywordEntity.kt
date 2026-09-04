package com.lioisme.modev.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 全局弹窗拦截关键词库条目(V37 T2.1):
 * 库为全局资产,每应用经 [com.lioisme.modev.core.model.AppListenOverride.keywordIds]
 * 选择启用子集;宿主端解析为规则表下发,注入侧零感知本表。
 *
 * - [text] 唯一(大小写敏感去重,UI 层负责 trim);
 * - [matchMode] 存 [com.lioisme.modev.core.model.KeywordMatchMode] 枚举名,
 *   非法值经映射层回落 CONTAINS(漂移安全);
 * - [enabled] 全局停用位:false = 对所有应用不生效(与每应用启用集求交)。
 */
@Entity(
    tableName = "keyword_library",
    indices = [Index(value = ["text"], unique = true)],
)
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "matchMode")
    val matchMode: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
)
