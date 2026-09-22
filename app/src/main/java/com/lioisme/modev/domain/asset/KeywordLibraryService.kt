package com.lioisme.modev.domain.asset

import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.core.model.KeywordRule
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局弹窗拦截关键词库契约(V45 ML-1:资产面方法层补齐;AGENTS #23 词表事实源)。
 *
 * UI 关键词库页与 MCP `keywords_*` 五工具共用本契约:库变更后「向全部在线会话重推
 * 各自有效配置」由实现方内部收口(不在两侧各写一遍),[resolveRules] 是配置解析
 * 唯一入口(全局启用集 ∧ 每应用子集,null = 跟随全局)。
 */
interface KeywordLibraryService {

    /** 库全量快照(createdAt 降序;UI 列表与 MCP `keywords_list` 同源)。 */
    val entries: StateFlow<List<KeywordEntry>>

    /** 新增关键词;空白/重复(text 唯一索引)返回 false。 */
    suspend fun add(text: String, matchMode: KeywordMatchMode = KeywordMatchMode.CONTAINS): Boolean

    /** 按 id 改文本/匹配模式;id 不存在返回 false。 */
    suspend fun update(id: Long, text: String, matchMode: KeywordMatchMode): Boolean

    /** 条目启停(全局启停,与「每应用跟随/子集」正交)。 */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** 删除条目(幂等)。 */
    suspend fun remove(id: Long)

    /** 清空全库(危险操作;UI 恒居中确认、MCP 恒 `confirm=true`)。 */
    suspend fun clear()

    /** 解析下发规则:[keywordIds] null = 全局启用集,非 null = 其子集(保持库内序)。 */
    fun resolveRules(keywordIds: List<Long>?): List<KeywordRule>
}
