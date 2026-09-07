package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.McpTokenEntry
import com.lioisme.modev.data.repo.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * MCP 访问令牌管理页 VM(V39):多令牌列表 + 认证开关。
 * 令牌值仅在本页显隐/复制消费,恒不进 MCP 工具输出与诊断快照(PII 纪律);
 * 创建/撤销经 [SettingsRepository] 原地更新服务端校验集(不重启、不踢会话)。
 */
class McpTokensViewModel(
    private val settings: SettingsRepository,
) : ViewModel() {

    val tokenAuth: StateFlow<Boolean> = settings.mcpTokenAuth

    val tokens: StateFlow<List<McpTokenEntry>> = settings.mcpTokens

    fun setTokenAuth(enabled: Boolean) {
        viewModelScope.launch { settings.setMcpTokenAuth(enabled) }
    }

    /** 新建令牌(label 可空 = 未命名);完成后 Snackbar 反馈由 UI 侧执行。 */
    fun addToken(label: String) {
        viewModelScope.launch { settings.addMcpToken(label) }
    }

    fun revokeToken(id: String) {
        viewModelScope.launch { settings.revokeMcpToken(id) }
    }
}

class McpTokensViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return McpTokensViewModel(container.settings) as T
    }
}
