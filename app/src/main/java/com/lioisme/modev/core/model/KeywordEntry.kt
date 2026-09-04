package com.lioisme.modev.core.model

/**
 * 全局关键词库条目(V37 T2.1 领域视图;存储细节由 data 层仓库出口承担,
 * UI 禁 import data.db.*)。
 *
 * @param id 稳定标识(每应用启用集 [AppListenOverride.keywordIds] 引用)。
 * @param text 关键词文本。
 * @param matchMode 匹配模式。
 * @param enabled 全局停用位(false = 对所有应用不生效)。
 * @param createdAt 创建时间戳(排序用)。
 */
data class KeywordEntry(
    val id: Long,
    val text: String,
    val matchMode: KeywordMatchMode,
    val enabled: Boolean,
    val createdAt: Long,
)
