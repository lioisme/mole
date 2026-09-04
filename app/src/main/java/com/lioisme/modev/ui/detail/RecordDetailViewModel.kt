package com.lioisme.modev.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.mapping.RecordDetailModel
import com.lioisme.modev.data.repo.LogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * 记录详情(2026-08-15 UI 重构 Phase 5,2026-08-17 V6 邻接投影;V11 L6 状态收敛):
 * 按 id 订阅单条记录;邻接导航仅查询邻居 id(投影),不再物化邻居大字段。
 * 三态契约由单一 [DetailUiState] 承载:loaded 仅在首次真实查询结果到达后置 true;
 * Failed(loaded && record == null)提供 retry()。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordDetailViewModel(
    recordId: Long,
    private val logs: LogRepository,
) : ViewModel() {

    private val recordIdFlow = MutableStateFlow(recordId)
    private val retryTick = MutableStateFlow(0)

    /**
     * 原始查询流(stateIn 会把初始值 null 当作首发发射,导致 loaded 假阳性,
     * 因此这里直接收集 upstream,只把真实查询结果算作"已加载")。
     */
    private val query = combine(recordIdFlow, retryTick) { id, _ -> id }
        .flatMapLatest { id -> logs.observeById(id) }

    private val _uiState = MutableStateFlow(DetailUiState())

    /** 详情页单一状态源(L6):记录 / 加载完成 / 上下条可用,一次收集整屏。 */
    val uiState: StateFlow<DetailUiState> = _uiState

    init {
        viewModelScope.launch {
            query.collect { current ->
                _uiState.value = DetailUiState(
                    record = current,
                    loaded = true,
                    canOlder = current != null &&
                        logs.neighborIdOf(current.recordType, current.packageName, current.timestamp, current.id, newer = false) != null,
                    canNewer = current != null &&
                        logs.neighborIdOf(current.recordType, current.packageName, current.timestamp, current.id, newer = true) != null,
                )
            }
        }
    }

    fun retry() {
        retryTick.value++
    }

    fun goOlder() {
        viewModelScope.launch { move(newer = false) }
    }

    fun goNewer() {
        viewModelScope.launch { move(newer = true) }
    }

    private suspend fun move(newer: Boolean) {
        val current = _uiState.value.record ?: return
        logs.neighborIdOf(current.recordType, current.packageName, current.timestamp, current.id, newer)
            ?.let { recordIdFlow.value = it }
    }
}

/**
 * 记录详情单一状态(L6 收敛):record=当前记录(null=不存在),loaded=首次真实查询
 * 结果已到达,canOlder/canNewer=同包邻接可用性。failed = 加载完成但记录缺失。
 * V17:record 泛化为 [RecordDetailModel](算法/数据库双形态)。
 */
data class DetailUiState(
    val record: RecordDetailModel? = null,
    val loaded: Boolean = false,
    val canOlder: Boolean = false,
    val canNewer: Boolean = false,
) {
    val failed: Boolean get() = loaded && record == null
}

/** 简单工厂,避免在 Compose 中手动拼参。 */
class RecordDetailViewModelFactory(
    private val recordId: Long,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordDetailViewModel(
            recordId = recordId,
            logs = container.logs,
        ) as T
    }
}
