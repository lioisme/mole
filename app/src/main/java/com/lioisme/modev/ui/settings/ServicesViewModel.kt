package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.domain.server.ConnectionMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 服务页 VM(V39 拆分):wire ConfigServer + MCP 服务的**配置面**唯一出口。
 * 诊断页只留运行时观测;本页只管开关/端口/令牌入口。
 *
 * 包边界(P5-1):绑定地址经 [ConnectionMonitor.boundAddress] 抽象与 MCP 注入的
 * StateFlow 出口消费,UI 零 import data.server / mcp 实现类型。
 */
class ServicesViewModel(
    wireMonitor: ConnectionMonitor,
    mcpBoundAddress: StateFlow<String?>,
    mcpBindError: StateFlow<String?>,
    private val settings: SettingsRepository,
) : ViewModel() {

    // ---- wire 通信服务 ----

    val wireExternalAccess: StateFlow<Boolean> = settings.serverExternalAccess

    val wirePort: StateFlow<Int> = settings.serverPort

    /** wire 监听地址展示口径:未绑定回退默认回环语义。 */
    val wireListeningAddress: StateFlow<String> =
        wireMonitor.boundAddress
            .map { it ?: ConnectionMonitor.BIND_LOOPBACK }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionMonitor.BIND_LOOPBACK)

    fun setWireExternalAccess(enabled: Boolean) {
        viewModelScope.launch { settings.setServerExternalAccess(enabled) }
    }

    fun setWirePort(port: Int) {
        viewModelScope.launch { settings.setServerPort(port) }
    }

    // ---- MCP 服务 ----

    val mcpEnabled: StateFlow<Boolean> = settings.mcpEnabled

    val mcpPort: StateFlow<Int> = settings.mcpPort

    val mcpExternalAccess: StateFlow<Boolean> = settings.mcpExternalAccess

    val mcpTokenAuth: StateFlow<Boolean> = settings.mcpTokenAuth

    val mcpTokenCount: StateFlow<Int> =
        settings.mcpTokens
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** MCP 监听地址展示口径:未运行 = null(UI 回落端口字面量)。 */
    val mcpListeningAddress: StateFlow<String?> = mcpBoundAddress

    /** MCP 最近一次绑定失败原因(null = 正常)。 */
    val mcpBindError: StateFlow<String?> = mcpBindError

    fun setMcpEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setMcpEnabled(enabled) }
    }

    fun setMcpExternalAccess(enabled: Boolean) {
        viewModelScope.launch { settings.setMcpExternalAccess(enabled) }
    }

    fun setMcpPort(port: Int) {
        viewModelScope.launch { settings.setMcpPort(port) }
    }

    fun setMcpTokenAuth(enabled: Boolean) {
        viewModelScope.launch { settings.setMcpTokenAuth(enabled) }
    }
}

class ServicesViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ServicesViewModel(
            wireMonitor = container.server,
            mcpBoundAddress = container.mcp.boundAddress,
            mcpBindError = container.mcp.bindError,
            settings = container.settings,
        ) as T
    }
}
