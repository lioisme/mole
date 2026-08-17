package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.PipelineStats
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页:框架状态、服务状态、管道统计与数据治理。
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val connectedCount: StateFlow<Int> = container.server.connectedPackagesFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 全部应用记录总数(「数据」分区只读行)。 */
    val totalRecords: StateFlow<Long> = container.logs.observeRecordCounts()
        .map { counts -> counts.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 管道统计(接收/丢弃/写失败/裁剪/通道拒绝),供「管道统计」分区展示。 */
    val pipelineStats: StateFlow<PipelineStats> = container.logs.statsFlow

    /** 框架作用域状态(现代框架连接 / 模块未启用 / legacy 降级)。 */
    val frameworkState: StateFlow<ScopeState> = container.scopeRepository.state

    fun clearAll() {
        viewModelScope.launch { container.logs.clearAll() }
    }
}

class SettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(container) as T
    }
}