package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.server.PortValidator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 服务页 VM(V39 拆分;V41 T2 统一外部访问 + T3 端口纯端口化):wire ConfigServer
 * + MCP 服务的**配置面**唯一出口。诊断页只留运行时观测;本页只管开关/端口/令牌入口。
 * V41:端口行仅显示端口(IP 由统一外部访问开关承载)→ VM 不再暴露监听地址流
 * (运行时绑地址观测归诊断页,DiagnosticsViewModel.listeningAddress)。
 *
 * 外部访问(D-1 统一):单一开关双服务消费,「通用」分区承载,不专属任何一侧;
 * 与令牌认证互相独立(V40 D-4 语义保持)。
 *
 * 包边界(P5-1):UI 零 import data.server / mcp 实现类型。
 */
class ServicesViewModel(
    mcpBoundAddress: StateFlow<String?>,
    mcpBindError: StateFlow<String?>,
    wireBindError: StateFlow<String?>,
    private val settings: SettingsRepository,
) : ViewModel() {

    // ---- 统一外部访问(V41 D-1:双服务消费,中性归属,不专属 wire/MCP)----

    val externalAccess: StateFlow<Boolean> = settings.serverExternalAccess

    /**
     * 统一外部访问副标题(V41 D-4):启用 = 0.0.0.0 / 停用 = 127.0.0.1——
     * 开关态到绑定地址字面量的**纯函数映射**(与双服务实际 bind 地址同形),
     * 不设独立派生流(单源 = [externalAccess],零调度差)。
     */
    fun externalAddressLabel(enabled: Boolean): String =
        if (enabled) ConnectionMonitor.BIND_ALL_INTERFACES else ConnectionMonitor.BIND_LOOPBACK

    fun setExternalAccess(enabled: Boolean) {
        viewModelScope.launch { settings.setExternalAccess(enabled) }
    }

    // ---- wire 通信服务 ----

    val wirePort: StateFlow<Int> = settings.serverPort

    /** wire 最近一次绑定失败原因(V42 T3,null = 正常;服务页错误行消费)。 */
    val wireBindError: StateFlow<String?> = wireBindError

    fun setWirePort(port: Int) {
        viewModelScope.launch { settings.setServerPort(port) }
    }

    // ---- MCP 服务 ----

    val mcpEnabled: StateFlow<Boolean> = settings.mcpEnabled

    val mcpPort: StateFlow<Int> = settings.mcpPort

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

    fun setMcpPort(port: Int) {
        viewModelScope.launch { settings.setMcpPort(port) }
    }

    fun setMcpTokenAuth(enabled: Boolean) {
        viewModelScope.launch { settings.setMcpTokenAuth(enabled) }
    }

    // ---- 端口可用性校验(V42 T3,PortValidator 组装;P5-1:UI 零 import data.server)----

    /**
     * wire 端口校验:self = wire 当前端口(豁免),other = MCP 端口(冲突面),
     * 探测地址随统一外部访问开关(与真实绑定语义一致)。
     */
    fun validateWirePort(): suspend (Int) -> PortValidator.PortCheck = { candidate ->
        val other = settings.mcpPort.value
        PortValidator.check(
            candidate = candidate,
            selfPort = settings.serverPort.value,
            selfLabel = WIRE_LABEL,
            otherPort = other,
            otherLabel = MCP_LABEL,
            externalAccess = settings.serverExternalAccess.value,
        )
    }

    /** MCP 端口校验:self = MCP 当前端口(豁免),other = wire 端口(冲突面)。 */
    fun validateMcpPort(): suspend (Int) -> PortValidator.PortCheck = { candidate ->
        val other = settings.serverPort.value
        PortValidator.check(
            candidate = candidate,
            selfPort = settings.mcpPort.value,
            selfLabel = MCP_LABEL,
            otherPort = other,
            otherLabel = WIRE_LABEL,
            externalAccess = settings.serverExternalAccess.value,
        )
    }

    private companion object {
        /** 冲突错误分型文案的服务名(资源侧不感知,直接本地化于 strings)。 */
        const val WIRE_LABEL = "wire"
        const val MCP_LABEL = "MCP"
    }
}

class ServicesViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ServicesViewModel(
            mcpBoundAddress = container.mcp.boundAddress,
            mcpBindError = container.mcp.bindError,
            wireBindError = container.server.bindError,
            settings = container.settings,
        ) as T
    }
}
