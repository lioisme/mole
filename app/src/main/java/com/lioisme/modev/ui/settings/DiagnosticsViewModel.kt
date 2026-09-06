package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.PipelineStats
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.domain.server.ConnectionMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 服务与诊断页(V10 设置分级;V26 外部访问开关;V38 MCP 服务分区):
 * 框架状态 + 监听地址/端口 + 连接数 + 捕获管线统计 + MCP 状态/开关/端口/Token。
 * M18:传输常量([WireProtocol.PORT])与绑定地址([ConnectionMonitor.boundAddress])
 * 经 VM 出口暴露,UI 零传输层 import(P5-1 包边界门禁);
 * V26:外部访问开关经 [SettingsRepository.setServerExternalAccess]
 * 即写即重启改绑,UI 仅消费状态与动作;
 * V38:MCP 配置经 [SettingsRepository] 同款模式(mcpRestarter 由 AppContainer 装配)。
 */
class DiagnosticsViewModel(
    scopeRepository: ScopeRepository,
    server: ConnectionMonitor,
    logs: LogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val frameworkState: StateFlow<ScopeState> = scopeRepository.state

    val connectedCount: StateFlow<Int> = server.connectedPackagesFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 通信服务端口(V29:经 SettingsRepository 管理,可自定义)。 */
    val port: StateFlow<Int> = settings.serverPort

    /**
     * 监听地址展示口径(V26):绑定地址非空取实测值,未绑定回退默认回环语义;
     * UI 不感知 data.server 实现(包边界)。
     */
    val listeningAddress: StateFlow<String> =
        server.boundAddress
            .map { it ?: ConnectionMonitor.BIND_LOOPBACK }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionMonitor.BIND_LOOPBACK)

    /** 外部访问开关状态(宿主本地偏好,默认停用)。 */
    val externalAccess: StateFlow<Boolean> = settings.serverExternalAccess

    // ---- V38 MCP 服务 ----

    /** MCP 启用开关(默认开启,回环常驻)。 */
    val mcpEnabled: StateFlow<Boolean> = settings.mcpEnabled

    /** MCP 端口(默认 15110)。 */
    val mcpPort: StateFlow<Int> = settings.mcpPort

    /** MCP 外部访问(默认停用;开启侧强制 Bearer Token,由服务端执行)。 */
    val mcpExternalAccess: StateFlow<Boolean> = settings.mcpExternalAccess

    /** MCP Token(装配期自动生成;UI 展示/复制)。 */
    val mcpToken: String = settings.mcpToken()

    fun setMcpEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setMcpEnabled(enabled) }
    }

    fun setMcpExternalAccess(enabled: Boolean) {
        viewModelScope.launch { settings.setMcpExternalAccess(enabled) }
    }

    fun setMcpPort(port: Int) {
        viewModelScope.launch { settings.setMcpPort(port) }
    }

    /** 切换外部访问:持久化 + 服务端立即改绑(127.0.0.1 ↔ 0.0.0.0)。 */
    fun setExternalAccess(enabled: Boolean) {
        viewModelScope.launch { settings.setServerExternalAccess(enabled) }
    }

    /** 设置监听端口:持久化 + 服务端重启改绑。 */
    fun setPort(port: Int) {
        viewModelScope.launch { settings.setServerPort(port) }
    }

    val pipelineStats: StateFlow<PipelineStats> = logs.statsFlow
}

class DiagnosticsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiagnosticsViewModel(
            container.scopeRepository,
            container.server,
            container.logs,
            container.settings,
        ) as T
    }
}
