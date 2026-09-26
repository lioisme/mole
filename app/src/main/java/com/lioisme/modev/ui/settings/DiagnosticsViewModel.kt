package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.domain.diagnostics.PipelineStats
import com.lioisme.modev.domain.record.RecordService
import com.lioisme.modev.domain.server.McpServiceConfig
import com.lioisme.modev.domain.server.ServerConfigService
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.domain.server.ConnectionMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 诊断页 VM:纯运行时观测——框架状态 + 连接数 + 捕获管线统计 +
 * 复制快照所需的 wire 端口/地址(只读)。
 * 服务配置动作(开关/端口/令牌)全部迁至 [ServicesViewModel],本页零写路径。
 * M18:传输常量与绑定地址经 VM 出口暴露,UI 零传输层 import(P5-1 包边界门禁)。
 * V45 T2.2:依赖面全为 `domain.*` 契约(RecordService/ServerConfigService/McpServiceConfig/
 * ConnectionMonitor),不再持有仓库与服务端实现类型。
 */
class DiagnosticsViewModel(
    scopeRepository: ScopeRepository,
    server: ConnectionMonitor,
    logs: RecordService,
    serverConfig: ServerConfigService,
    mcpConfig: McpServiceConfig,
    mcpBoundAddress: StateFlow<String?>,
) : ViewModel() {

    val frameworkState: StateFlow<ScopeState> = scopeRepository.state

    val connectedCount: StateFlow<Int> = server.connectedPackagesFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 通信服务端口(只读;复制快照消费,编辑入口在服务页)。 */
    val port: StateFlow<Int> = serverConfig.serverPort

    /**
     * 监听地址展示口径:绑定地址非空取实测值,未绑定回退默认回环语义;
     * UI 不感知 data.server 实现(包边界)。
     */
    val listeningAddress: StateFlow<String> =
        server.boundAddress
            .map { it ?: ConnectionMonitor.BIND_LOOPBACK }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionMonitor.BIND_LOOPBACK)

    /** 外部访问状态(只读;复制快照消费,V41 统一开关,配置动作在服务页「通用」分区)。 */
    val externalAccess: StateFlow<Boolean> = serverConfig.serverExternalAccess

    // ---- V39: MCP 只读态(复制快照消费;配置动作在服务页/令牌页) ----

    val mcpEnabled: StateFlow<Boolean> = mcpConfig.mcpEnabled
    val mcpPort: StateFlow<Int> = mcpConfig.mcpPort
    val mcpTokenAuth: StateFlow<Boolean> = mcpConfig.mcpTokenAuth
    val mcpBoundAddress: StateFlow<String?> = mcpBoundAddress

    val pipelineStats: StateFlow<PipelineStats> = logs.stats
}

class DiagnosticsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiagnosticsViewModel(
            scopeRepository = container.scopeRepository,
            server = container.wireStatus,
            logs = container.records,
            serverConfig = container.serverConfig,
            mcpConfig = container.mcpConfig,
            mcpBoundAddress = container.mcpStatus.boundAddress,
        ) as T
    }
}
