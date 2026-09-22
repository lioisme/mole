package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.McpTokenEntry
import com.lioisme.modev.domain.server.McpServiceConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * MCP 访问令牌管理页 VM(V39):多令牌列表 + 认证开关。
 * 令牌值仅在本页显隐/复制消费,恒不进 MCP 工具输出与诊断快照(PII 纪律);
 * 创建/撤销经 [McpServiceConfig] 原地更新服务端校验集(不重启、不踢会话)。
 * V45 T2.2:依赖面为契约接口(与 MCP 令牌面「仅 UI」排除项同一上游)。
 */
class McpTokensViewModel(
    private val mcpConfig: McpServiceConfig,
) : ViewModel() {

    val tokenAuth: StateFlow<Boolean> = mcpConfig.mcpTokenAuth

    val tokens: StateFlow<List<McpTokenEntry>> = mcpConfig.mcpTokens

    fun setTokenAuth(enabled: Boolean) {
        viewModelScope.launch { mcpConfig.setMcpTokenAuth(enabled) }
    }

    /** 新建令牌(label 可空 = 未命名);完成后 Snackbar 反馈由 UI 侧执行。 */
    fun addToken(label: String) {
        viewModelScope.launch { mcpConfig.addMcpToken(label) }
    }

    /** 重命名(可提交空名 = 清除名称);原地生效,不重启 MCP、不踢已连会话。 */
    fun setTokenLabel(id: String, label: String) {
        viewModelScope.launch { mcpConfig.setMcpTokenLabel(id, label) }
    }

    fun revokeToken(id: String) {
        viewModelScope.launch { mcpConfig.revokeMcpToken(id) }
    }
}

class McpTokensViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return McpTokensViewModel(container.mcpConfig) as T
    }
}
