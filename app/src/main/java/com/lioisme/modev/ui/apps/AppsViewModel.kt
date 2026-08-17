package com.lioisme.modev.ui.apps

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.data.server.ConfigServer
import com.lioisme.modev.domain.scope.ScopeChangeResult
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.i18n.LocaleManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 跨层消息:资源 id + 格式化参数,由 UI 解析(便于 i18n)。 */
data class UiMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList(),
)

/**
 * 应用列表:全量已安装应用 × 作用域(LSPosed,单一事实源)× 连接状态 × 记录数。
 * 勾选/取消勾选 = 框架作用域变更,与 LSPosed 管理器同根同源。
 * 排序(2026-08-17 V7):终态排序唯一在本层——名称 Locale Collator(社区规范),
 * 设置页「已作用域应用置顶」开启时已作用域优先(AppListSettings 单一事实源)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModel(
    private val catalog: AppCatalogRepository,
    private val logs: LogRepository,
    server: ConfigServer,
    private val scopeRepository: ScopeRepository,
) : ViewModel() {

    data class AppRow(
        val packageName: String,
        val label: String,
        val system: Boolean,
        val enabled: Boolean,
        val scoped: Boolean,
        val connected: Boolean,
        val recordCount: Long,
    )

    data class UiState(
        val rows: List<AppRow> = emptyList(),
        /** 框架作用域是否可用(不可用时禁用勾选)。 */
        val scopeReady: Boolean = false,
        /** 变更进行中的包(防重复点击)。 */
        val pending: Set<String> = emptySet(),
        val refreshing: Boolean = false,
        val onlyScoped: Boolean = false,
        val showSystem: Boolean = true,
    )

    private data class CatalogData(
        val apps: List<AppCatalogRepository.AppInfo>,
        val counts: Map<String, Long>,
        val connected: Set<String>,
        val scopeState: ScopeState,
    )

    private data class UiPrefs(
        val query: String,
        val onlyScoped: Boolean,
        val showSystem: Boolean,
        val pending: Set<String>,
        val refreshing: Boolean,
    )

    private val query = MutableStateFlow("")
    private val onlyScoped = MutableStateFlow(false)
    private val showSystem = MutableStateFlow(true)
    private val pending = MutableStateFlow<Set<String>>(emptySet())
    private val refreshing = MutableStateFlow(false)

    /** 一次性提示(scope 变更结果),由 UI 收集为 Snackbar。 */
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)

    val uiState: StateFlow<UiState> = combine(
        combine(
            catalog.apps,
            logs.observeRecordCounts(),
            server.connectedPackagesFlow,
            scopeRepository.state,
            ::CatalogData,
        ),
        combine(query, onlyScoped, showSystem, pending, refreshing, ::UiPrefs),
        // 置顶开关(AppListSettings 单一事实源):变更即重排,跨页返回不滞留旧序。
        AppListSettings.pinScoped,
        // 语言热切换触发器(V9):tag 变化即重排——AppSort 按调用时
        // Locale.getDefault() 现建比较器,重算即按新语言 Collator 排序。
        LocaleManager.localeFlow,
    ) { catalogData, prefs, pinScoped, _ ->
        val ready = catalogData.scopeState as? ScopeState.Ready
        val scopedSet = ready?.scope ?: emptySet()
        val nameComparator = AppSort.nameComparator()
        val rows = catalogData.apps
            .filter { prefs.showSystem || !it.system }
            .filter { !prefs.onlyScoped || it.packageName in scopedSet }
            .filter {
                prefs.query.isBlank() ||
                    it.label.contains(prefs.query, ignoreCase = true) ||
                    it.packageName.contains(prefs.query, ignoreCase = true)
            }
            .map { app ->
                AppRow(
                    packageName = app.packageName,
                    label = app.label,
                    system = app.system,
                    enabled = app.enabled,
                    scoped = app.packageName in scopedSet,
                    connected = app.packageName in catalogData.connected,
                    recordCount = catalogData.counts[app.packageName] ?: 0L,
                )
            }
            // 终态排序唯一在本层:名称 Collator(社区规范)恒生效;
            // 置顶开启时已作用域优先,组内仍按名称(稳定全序)。
            .let { list ->
                if (pinScoped) {
                    list.sortedWith(compareBy<AppRow> { !it.scoped }.thenComparing(nameComparator))
                } else {
                    list.sortedWith(nameComparator)
                }
            }
        UiState(
            rows = rows,
            scopeReady = ready != null,
            pending = prefs.pending,
            refreshing = prefs.refreshing,
            onlyScoped = prefs.onlyScoped,
            showSystem = prefs.showSystem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setOnlyScoped(value: Boolean) {
        onlyScoped.value = value
    }

    fun setShowSystem(value: Boolean) {
        showSystem.value = value
    }

    /** 下拉刷新:重扫应用列表 + 重读框架作用域。 */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            catalog.refresh()
            scopeRepository.refresh()
            refreshing.value = false
        }
    }

    /** 勾选 / 取消勾选(等价于 LSPosed 作用域变更)。 */
    fun toggle(packageName: String) {
        if (packageName in pending.value) return
        viewModelScope.launch {
            pending.value = pending.value + packageName
            val result = scopeRepository.toggle(packageName)
            messages.emit(messageFor(packageName, result))
            pending.value = pending.value - packageName
        }
    }

    private fun messageFor(packageName: String, result: ScopeChangeResult): UiMessage = when (result) {
        is ScopeChangeResult.ScopeRequested ->
            UiMessage(R.string.apps_scope_requested, listOf(packageName))
        is ScopeChangeResult.ScopeRemoved ->
            UiMessage(R.string.apps_scope_removed, listOf(packageName))
        is ScopeChangeResult.Failed ->
            UiMessage(R.string.apps_scope_failed, listOf(packageName, result.message))
    }
}

/**
 * 算法列表页(2026-08-16 V3 更名,四界面重构):单一职责 = 某应用的算法记录列表。
 * 数据流:分页记录(时间倒序,随搜索词/类别重建)+ 应用信息/连接状态/作用域状态
 * + 每应用捕获配置(8 类开关 + 记录堆栈)+ 移出作用域 + 清空日志。
 * 2026-08-16 V2 的 LOGS/CAPTURE 双 Tab 机制已删除(信息架构收敛为 4 界面,
 * 捕获配置改为列表页内容区折叠区块)。
 */
class AlgorithmListViewModel(
    val packageName: String,
    private val catalog: AppCatalogRepository,
    server: ConfigServer,
    private val settings: SettingsRepository,
    private val logs: LogRepository,
    private val scopeRepository: ScopeRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow<CryptoKind?>(null)

    /** 应用名/图标信息。 */
    val appInfo: StateFlow<AppCatalogRepository.AppInfo?> = catalog.apps
        .map { list -> list.firstOrNull { it.packageName == packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val switchState: StateFlow<HookSwitch> = settings.flowFor(packageName)

    val connected: StateFlow<Boolean> = server.connectedPackagesFlow
        .map { set -> packageName in set }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scopeState: StateFlow<ScopeState> = scopeRepository.state

    val recordCount: StateFlow<Long> = logs.observeRecordCounts()
        .map { counts -> counts[packageName] ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 分页记录流(时间倒序,随搜索词 / 类别切换重建)。 */
    val records: Flow<PagingData<CryptoRecord>> = combine(query, kind) { q, k -> q to k }
        .flatMapLatest { (q, k) -> logs.pagedRecords(packageName, k, q) }

    /** 一次性提示(移出作用域结果),由 UI 收集为 Snackbar。 */
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setKind(value: CryptoKind?) {
        kind.value = value
    }

    fun setSwitch(transform: (HookSwitch) -> HookSwitch) {
        settings.set(packageName, transform(switchState.value))
    }

    fun clearLogs() {
        viewModelScope.launch { logs.clear(packageName) }
    }

    /** 移出作用域(等价于 LSPosed 作用域取消勾选)。 */
    fun removeScope() {
        viewModelScope.launch {
            val result = scopeRepository.remove(packageName)
            messages.emit(
                when (result) {
                    is ScopeChangeResult.ScopeRemoved ->
                        UiMessage(R.string.apps_scope_removed, listOf(packageName))
                    is ScopeChangeResult.Failed ->
                        UiMessage(R.string.apps_scope_failed, listOf(packageName, result.message))
                    else -> UiMessage(R.string.apps_scope_failed, listOf(packageName))
                }
            )
        }
    }
}

/** 简单工厂,避免在 Compose 中手动拼参。 */
class AppsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppsViewModel(
            catalog = container.catalog,
            logs = container.logs,
            server = container.server,
            scopeRepository = container.scopeRepository,
        ) as T
    }
}

/** 简单工厂,避免在 Compose 中手动拼参。 */
class AlgorithmListViewModelFactory(
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AlgorithmListViewModel(
            packageName = packageName,
            catalog = container.catalog,
            server = container.server,
            settings = container.settings,
            logs = container.logs,
            scopeRepository = container.scopeRepository,
        ) as T
    }
}