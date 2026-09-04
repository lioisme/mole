package com.lioisme.modev.data.repo

import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.core.model.KeywordRule
import com.lioisme.modev.data.db.KeywordEntity
import com.lioisme.modev.data.db.KeywordLibraryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 全局弹窗拦截关键词库仓库(V37 T2.1):
 *
 * - DAO 全量流 → 内存快照([entries]),UI 与配置解析均读快照(同步、无阻塞);
 * - [add] 以 text 唯一索引去重(冲突返回 false,UI 提示重复);
 * - [resolveRules] 纯内存解析:全局启用集 ∧ 每应用启用子集(keywordIds,
 *   null = 跟随全局)→ 下发规则表;
 * - 库变更后由调用方(SettingsRepository)向全部在线会话重推有效配置。
 */
class KeywordLibraryRepository(
    private val dao: KeywordLibraryDao,
    scope: CoroutineScope,
) {

    private val _entries = MutableStateFlow<List<KeywordEntry>>(emptyList())

    /** 库全量快照(createdAt 降序)。 */
    val entries: StateFlow<List<KeywordEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            dao.observeAll()
                .map { list -> list.map { it.toEntry() } }
                .collect { _entries.value = it }
        }
    }

    /** 新增关键词;空白/重复返回 false。 */
    suspend fun add(text: String, matchMode: KeywordMatchMode = KeywordMatchMode.CONTAINS): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val id = dao.insertIgnore(
            KeywordEntity(
                text = trimmed,
                matchMode = matchMode.name,
                enabled = true,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id != -1L
    }

    /** 更新文本/模式;空白或与他项冲突返回 false。 */
    suspend fun update(id: Long, text: String, matchMode: KeywordMatchMode): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val current = _entries.value.firstOrNull { it.id == id } ?: return false
        val updated = current.copy(text = trimmed, matchMode = matchMode)
        return runCatching { dao.update(updated.toEntity()) }.isSuccess
    }

    /** 全局启停。 */
    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val current = _entries.value.firstOrNull { it.id == id } ?: return
        dao.update(current.copy(enabled = enabled).toEntity())
    }

    suspend fun remove(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clear() {
        dao.clear()
    }

    /**
     * 解析每应用有效规则表:
     * - [keywordIds] = null → 跟随全局(全部全局启用条目);
     * - 非 null → 全局启用 ∧ 应用启用子集(库中已删除条目自然失效)。
     */
    fun resolveRules(keywordIds: List<Long>?): List<KeywordRule> {
        val snapshot = _entries.value
        val globallyEnabled = snapshot.filter { it.enabled }
        val selected = if (keywordIds == null) {
            globallyEnabled
        } else {
            val idSet = keywordIds.toSet()
            globallyEnabled.filter { it.id in idSet }
        }
        return selected.map { KeywordRule(it.text, it.matchMode) }
    }

    private fun KeywordEntity.toEntry(): KeywordEntry = KeywordEntry(
        id = id,
        text = text,
        matchMode = runCatching { KeywordMatchMode.valueOf(matchMode) }.getOrDefault(KeywordMatchMode.CONTAINS),
        enabled = enabled,
        createdAt = createdAt,
    )

    private fun KeywordEntry.toEntity(): KeywordEntity = KeywordEntity(
        id = id,
        text = text,
        matchMode = matchMode.name,
        enabled = enabled,
        createdAt = createdAt,
    )
}
