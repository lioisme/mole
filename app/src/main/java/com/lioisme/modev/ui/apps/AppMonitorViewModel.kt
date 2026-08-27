package com.lioisme.modev.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.domain.scope.ScopeChangeResult
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.ui.common.UiMessage
import com.lioisme.modev.ui.common.scopeChangeMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用监听入口页数据(V18;V20 整合「查看记录」头部;V23 T5.1 拆分独立文件):
 * 应用信息 / 运行中 / 作用域 / 启动入口 / 每类型记录计数 + 应用级操作:
 * 作用域开关、移出作用域、清空该应用记录;
 * V23 G3:单应用监听覆盖([AppListenOverride])读取与「监听设置」入口摘要,
 * 删除 V20 遗留的 globalSwitch 死代码(组合体从未消费)。
 */
class AppMonitorViewModel(
    val packageName: String,
    private val catalog: AppCatalogRepository,
    server: ConnectionMonitor,
    private val scopeRepository: ScopeRepository,
    private val logs: LogRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 应用名+图标信息。*/
    val appInfo: StateFlow<AppCatalogRepository.AppInfo?> = catalog.apps
        .map { list -> list.firstOrNull { it.packageName == packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val connected: StateFlow<Boolean> = server.connectedPackagesFlow
        .map { set -> packageName in set }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scopeState: StateFlow<ScopeState> = scopeRepository.state

    /** 该应用是否已作用域(Ready 才有意义,其余态恒 false)。 */
    val scoped: StateFlow<Boolean> = scopeState
        .map { it is ScopeState.Ready && it.isScoped(packageName) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 作用域变更进行中(防重复点击,与列表页 pending 同语义)。 */
    private val scopePending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = scopePending

    private val launchableState = MutableStateFlow<Boolean?>(null)

    /** 启动入口解析结果(null = 解析中,V16 R4 惰性单次解析 + 缓存)。*/
    val launchable: StateFlow<Boolean?> = launchableState

    init {
        viewModelScope.launch {
            launchableState.value = catalog.resolveLaunchable(packageName)
        }
    }

    /** 该应用算法记录数(入口副标题,AppCountFormat 紧凑格式)。*/
    val cryptoCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.CRYPTO)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 该应用数据库记录数(入口副标题,AppCountFormat 紧凑格式)。*/
    val dbCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.DATABASE)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 该应用行为记录数(V27 一等公民;入口副标题)。*/
    val behaviorCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.BEHAVIOR)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 该应用偏好记录数(V28 一等公民;入口副标题)。*/
    val prefsCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.PREFS)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 该应用 WebView 记录数(V29 一等公民;入口副标题)。*/
    val webviewCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.WEBVIEW)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 该应用网络环境监听记录数(V30 一等公民;入口副标题)。*/
    val networkCount: StateFlow<Long> = logs.observeRecordCounts(RecordTypes.NETWORK)
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 单应用监听覆盖(无覆盖 = [AppListenOverride.DEFAULT];「监听设置」摘要来源)。*/
    val listenOverride: StateFlow<AppListenOverride> = settings.overrides
        .map { it[packageName] ?: AppListenOverride.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListenOverride.DEFAULT)

    /** 一次性提示(scope 变更 / 清空记录结果),由 UI 收集发 Snackbar。*/
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)

    /**
     * 作用域开关(V23 自行内 Checkbox 升级为页内 Switch):
     * 未作用域 → requestScope(系统通知确认);已作用域 → removeScope。
     */
    fun toggleScope() {
        if (scopePending.value) return
        viewModelScope.launch {
            scopePending.value = true
            try {
                val result =
                    if (scoped.value) {
                        scopeRepository.remove(packageName)
                    } else {
                        scopeRepository.toggle(packageName)
                    }
                messages.emit(scopeChangeMessage(packageName, result))
            } finally {
                scopePending.value = false
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logs.clear(packageName)
            messages.emit(UiMessage(R.string.apps_records_cleared, listOf(packageName)))
        }
    }

    /** 移出作用域,等价于 LSPosed 作用域取消勾选。*/
    fun removeScope() {
        if (scopePending.value) return
        viewModelScope.launch {
            val result = scopeRepository.remove(packageName)
            messages.emit(scopeChangeMessage(packageName, result))
        }
    }
}

/** 简单工厂,避免在 Compose 中手动拼参。*/
class AppMonitorViewModelFactory(
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppMonitorViewModel(
            packageName = packageName,
            catalog = container.catalog,
            server = container.server,
            scopeRepository = container.scopeRepository,
            logs = container.logs,
            settings = container.settings,
        ) as T
    }
}
