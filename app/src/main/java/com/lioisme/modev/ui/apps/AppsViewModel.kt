package com.lioisme.modev.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用列表:合并"有记录/已配置/当前连接"三类来源,支持名称过滤。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModel(
    server: ConfigServer,
    settings: SettingsRepository,
) : ViewModel() {

    data class AppItem(
        val packageName: String,
        val connected: Boolean,
        val configured: Boolean,
    )

    private val query = MutableStateFlow("")

    val uiState: StateFlow<List<AppItem>> = combine(
        query,
        server.connectedPackagesFlow,
        settings.configuredPackagesFlow,
    ) { q, connected, configured ->
        (connected + configured)
            .filter { q.isBlank() || it.contains(q, ignoreCase = true) }
            .sorted()
            .map { AppItem(packageName = it, connected = it in connected, configured = it in configured) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }
}

/**
 * 目标详情:某包的开关集 + 连接状态 + 清空日志。
 */
class AppDetailViewModel(
    val packageName: String,
    server: ConfigServer,
    private val settings: SettingsRepository,
    private val logs: com.lioisme.modev.data.repo.LogRepository,
) : ViewModel() {

    val switchState: StateFlow<com.lioisme.modev.core.model.HookSwitch> = settings.flowFor(packageName)

    val connected: StateFlow<Boolean> = server.connectedPackagesFlow
        .map { set -> packageName in set }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSwitch(transform: (com.lioisme.modev.core.model.HookSwitch) -> com.lioisme.modev.core.model.HookSwitch) {
        settings.set(packageName, transform(switchState.value))
    }

    fun clearLogs() {
        viewModelScope.launch { logs.clear(packageName) }
    }
}

/** 简单工厂,避免在 Compose 中手动拼参。 */
class AppsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppsViewModel(
            server = container.server,
            settings = container.settings,
        ) as T
    }
}

/** 简单工厂,避免在 Compose 中手动拼参。 */
class AppDetailViewModelFactory(
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppDetailViewModel(
            packageName = packageName,
            server = container.server,
            settings = container.settings,
            logs = container.logs,
        ) as T
    }
}