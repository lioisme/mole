package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.PipelineStats
import com.lioisme.modev.domain.scope.ScopeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 服务与诊断页(V10 设置分级):框架状态 + 服务连接数 + 捕获管线统计。
 */
class DiagnosticsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val frameworkState: StateFlow<ScopeState> = container.scopeRepository.state

    val connectedCount: StateFlow<Int> = container.server.connectedPackagesFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pipelineStats: StateFlow<PipelineStats> = container.logs.statsFlow
}

class DiagnosticsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiagnosticsViewModel(container) as T
    }
}
