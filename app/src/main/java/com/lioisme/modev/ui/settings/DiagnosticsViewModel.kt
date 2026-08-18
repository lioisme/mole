package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.PipelineStats
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.domain.server.ConnectionMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 服务与诊断页(V10 设置分级):框架状态 + 服务连接数 + 捕获管线统计。
 * V11 M4 依赖收窄 + M18:传输常量([WireProtocol.PORT])经 VM 暴露,UI 零传输层 import。
 */
class DiagnosticsViewModel(
    scopeRepository: ScopeRepository,
    server: ConnectionMonitor,
    logs: LogRepository,
) : ViewModel() {

    val frameworkState: StateFlow<ScopeState> = scopeRepository.state

    val connectedCount: StateFlow<Int> = server.connectedPackagesFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 通信服务端口(M18:传输常量单一出口,UI 不再直接引用 WireProtocol)。 */
    val port: Int = WireProtocol.PORT

    val pipelineStats: StateFlow<PipelineStats> = logs.statsFlow
}

class DiagnosticsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiagnosticsViewModel(container.scopeRepository, container.server, container.logs) as T
    }
}
