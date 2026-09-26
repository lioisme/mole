package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.domain.server.McpServiceConfig
import com.lioisme.modev.domain.server.PortRejectedException
import com.lioisme.modev.domain.server.PortValidator
import com.lioisme.modev.domain.server.PortWriteGuard
import com.lioisme.modev.domain.server.ServerConfigService
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
 * 包边界(P5-1 / V45 T2.2):依赖面 = [ServerConfigService] + [McpServiceConfig] 契约,
 * 零 import data.server / mcp / data.repo 实现类型;端口预检与写口硬闸共用
 * [PortWriteGuard](V45 DUAL-3 消灭双端强度分叉)。
 */
class ServicesViewModel(
    mcpBoundAddress: StateFlow<String?>,
    mcpBindError: StateFlow<String?>,
    wireBindError: StateFlow<String?>,
    private val serverConfig: ServerConfigService,
    private val mcpConfig: McpServiceConfig,
) : ViewModel() {

    // ---- 统一外部访问(V41 D-1:双服务消费,中性归属,不专属 wire/MCP)----

    val externalAccess: StateFlow<Boolean> = serverConfig.serverExternalAccess

    /**
     * 统一外部访问副标题:启用 = 0.0.0.0 / 停用 = 127.0.0.1——
     * 开关态到绑定地址字面量的**纯函数映射**(与双服务实际 bind 地址同形),
     * 不设独立派生流(单源 = [externalAccess],零调度差)。
     */
    fun externalAddressLabel(enabled: Boolean): String =
        if (enabled) ConnectionMonitor.BIND_ALL_INTERFACES else ConnectionMonitor.BIND_LOOPBACK

    fun setExternalAccess(enabled: Boolean) {
        viewModelScope.launch { serverConfig.setExternalAccess(enabled) }
    }

    // ---- wire 通信服务 ----

    val wirePort: StateFlow<Int> = serverConfig.serverPort

    /** wire 最近一次绑定失败原因(V42 T3,null = 正常;服务页错误行消费)。 */
    val wireBindError: StateFlow<String?> = wireBindError

    fun setWirePort(port: Int) {
        viewModelScope.launch {
            try {
                serverConfig.setServerPort(port)
            } catch (e: PortRejectedException) {
                rejectPort(e, "wire")
            }
        }
    }

    // ---- MCP 服务 ----

    val mcpEnabled: StateFlow<Boolean> = mcpConfig.mcpEnabled

    val mcpPort: StateFlow<Int> = mcpConfig.mcpPort

    val mcpTokenAuth: StateFlow<Boolean> = mcpConfig.mcpTokenAuth

    val mcpTokenCount: StateFlow<Int> =
        mcpConfig.mcpTokens
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** MCP 最近一次绑定失败原因(null = 正常)。 */
    val mcpBindError: StateFlow<String?> = mcpBindError

    fun setMcpEnabled(enabled: Boolean) {
        viewModelScope.launch { mcpConfig.setMcpEnabled(enabled) }
    }

    fun setMcpPort(port: Int) {
        viewModelScope.launch {
            try {
                mcpConfig.setMcpPort(port)
            } catch (e: PortRejectedException) {
                rejectPort(e, "mcp")
            }
        }
    }

    fun setMcpTokenAuth(enabled: Boolean) {
        viewModelScope.launch { mcpConfig.setMcpTokenAuth(enabled) }
    }

    /**
     * 写口硬拒兜底:预检通过到落盘之间的竞态(端口被他人抢占)才会走到这里 ——
     * 方法层已保证「偏好零写入、服务零改绑」,端口 StateFlow 恒为原值,
     * 页面端口行据此回显原值即为用户可见反馈,故仅落 W 级日志,不伪造成功、不重试。
     */
    private fun rejectPort(e: PortRejectedException, which: String) {
        android.util.Log.w("ModevServices", "$which port write rejected after pre-check: ${e.check}")
    }

    // ---- 端口可用性校验(V42 T3,PortValidator 组装;P5-1:UI 零 import data.server)----

    /**
     * wire 端口预检(V45 T2.4:改调 [PortWriteGuard] —— UI 预检与写口硬闸同一函数,
     * 不再有两份参数装配);探测地址随统一外部访问开关(与真实绑定语义一致)。
     */
    fun validateWirePort(): suspend (Int) -> PortValidator.PortCheck = { candidate ->
        PortWriteGuard.check(
            candidate = candidate,
            selfPort = serverConfig.serverPort.value,
            selfLabel = WIRE_LABEL,
            otherPort = mcpConfig.mcpPort.value,
            otherLabel = MCP_LABEL,
            externalAccess = serverConfig.serverExternalAccess.value,
        )
    }

    /** MCP 端口预检:self = MCP 当前端口(豁免),other = wire 端口(冲突面)。 */
    fun validateMcpPort(): suspend (Int) -> PortValidator.PortCheck = { candidate ->
        PortWriteGuard.check(
            candidate = candidate,
            selfPort = mcpConfig.mcpPort.value,
            selfLabel = MCP_LABEL,
            otherPort = serverConfig.serverPort.value,
            otherLabel = WIRE_LABEL,
            externalAccess = serverConfig.serverExternalAccess.value,
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
            mcpBoundAddress = container.mcpStatus.boundAddress,
            mcpBindError = container.mcpStatus.bindError,
            wireBindError = container.wireStatus.bindError,
            serverConfig = container.serverConfig,
            mcpConfig = container.mcpConfig,
        ) as T
    }
}
