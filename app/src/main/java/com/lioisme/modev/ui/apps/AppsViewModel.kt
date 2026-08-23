package com.lioisme.modev.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.data.repo.AppCatalogRepository
import com.lioisme.modev.data.repo.AppCatalogSource
import com.lioisme.modev.data.repo.AppListSettings
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.domain.scope.ScopeChangeResult
import com.lioisme.modev.domain.scope.ScopeRepository
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.domain.server.ConnectionMonitor
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.common.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用列表:全量已安装应用 × 作用域(LSPosed,单一事实源)× 连接状态 × 记录数。
 * 勾选/取消勾选 = 框架作用域变更,与 LSPosed 管理器同根同源。
 * 排序(2026-08-17 V7):终态排序唯一在本层——名称 Locale Collator(社区规范),
 * 设置页「已作用域应用置顶」开启时已作用域优先(AppListSettings 单一事实源)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModel(
    private val catalog: AppCatalogSource,
    private val logs: LogRepository,
    server: ConnectionMonitor,
    private val scopeRepository: ScopeRepository,
) : ViewModel() {

data class AppRow(
        val packageName: String,
        val label: String,
        val system: Boolean,
        val scoped: Boolean,
        val connected: Boolean,
        val recordCount: Long,
    )

    data class UiState(
        val rows: List<AppRow> = emptyList(),
        /** 目录扫描进行中(首帧 progress 依据,防误显空态)。 */
        val loading: Boolean = false,
        /** 框架作用域是否可用(不可用时禁用勾选)。 */
        val scopeReady: Boolean = false,
        /** 变更进行中的包(防重复点击)。 */
        val pending: Set<String> = emptySet(),
        val refreshing: Boolean = false,
        val showSystem: Boolean = true,
        val showModules: Boolean = false,
    )

private data class CatalogBase(
        val apps: List<AppCatalogRepository.AppInfo>,
        val scopeState: ScopeState,
    )

    /** 排序/筛选流水线偏好(S2:与计数/连接态分离,变化才触发重排)。 */
    private data class SortPrefs(
        val query: String,
        val showSystem: Boolean,
        val showModules: Boolean,
        val pinScoped: Boolean,
        val localeTag: String,
    )

    /** 排序核心行(S2:排序阶段仅依赖稳定源,不含计数/连接态)。 */
    data class SortedRow(
        val packageName: String,
        val label: String,
        val system: Boolean,
        val scoped: Boolean,
    )

    /** uiState 投影所需筛选偏好(与 SortPrefs 分离:不触发重排的仅投影字段)。 */
    private data class UiFilterPrefs(
        val query: String,
        val showSystem: Boolean,
        val showModules: Boolean,
    )

    private data class UiPrefs(
        val query: String,
        val showSystem: Boolean,
        val showModules: Boolean,
        val pending: Set<String>,
        val refreshing: Boolean,
        val loading: Boolean,
    )

private val query = MutableStateFlow("")
    private val pending = MutableStateFlow<Set<String>>(emptySet())
    private val refreshing = MutableStateFlow(false)

    /** 一次性提示(scope 变更结果),由 UI 收集为 Snackbar。 */
    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)

    /**
     * 排序流水线(S2):目录 × 作用域 × UI 偏好 × 置顶开关 × 语言。
     * 计数与连接态不在本流内——记录数每 200ms 落库变化不再触发全量过滤 + 排序。
     * 置顶 = 稳定分区(O(n) 无比较器,组内保持名称序)。
     * 筛选/置顶偏好 V14 R7/R8 起统一取自 AppListSettings(单一事实源 + 持久化)。
     */
    val sortedRows: StateFlow<List<SortedRow>> = combine(
        combine(catalog.apps, scopeRepository.state, ::CatalogBase),
        combine(
            combine(
                query,
                AppListSettings.showSystem,
                AppListSettings.showModules,
                ::UiFilterPrefs,
            ),
            AppListSettings.pinScoped,
            LocaleManager.localeFlow,
        ) { f, pin, locale ->
            SortPrefs(f.query, f.showSystem, f.showModules, pin, locale)
        },
    ) { base, prefs ->
        val scopedSet = (base.scopeState as? ScopeState.Ready)?.scope ?: emptySet()
        val nameComparator = AppSort.nameComparator<SortedRow>(label = { it.label }, key = { it.packageName })
        val rows = base.apps
            .filter { prefs.showSystem || !it.system }
            .filter { prefs.showModules || !it.isXposedModule }
            .filter {
                prefs.query.isBlank() ||
                    it.label.contains(prefs.query, ignoreCase = true) ||
                    it.packageName.contains(prefs.query, ignoreCase = true)
            }
            .map { app ->
                SortedRow(
                    packageName = app.packageName,
                    label = app.label,
                    system = app.system,
                    scoped = app.packageName in scopedSet,
                )
            }
            .sortedWith(nameComparator)
        if (prefs.pinScoped) AppSort.pinScoped(rows) { it.scoped } else rows
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 终态 UI 状态:排序行 × 计数/连接 × 偏好(仅投影,不重排)。 */
    val uiState: StateFlow<UiState> = combine(
        sortedRows,
        combine(logs.observeRecordCounts(), server.connectedPackagesFlow) { counts, connected -> counts to connected },
        combine(
            combine(query, AppListSettings.showSystem, AppListSettings.showModules, ::UiFilterPrefs),
            combine(pending, refreshing, catalog.loading) { p, r, l -> Triple(p, r, l) },
        ) { prefs3, state3 ->
            UiPrefs(
                query = prefs3.query,
                showSystem = prefs3.showSystem,
                showModules = prefs3.showModules,
                pending = state3.first,
                refreshing = state3.second,
                loading = state3.third,
            )
        },
        scopeRepository.state,
    ) { rows, countsAndConnected, prefs, scopeState ->
        val (counts, connected) = countsAndConnected
        UiState(
            rows = rows.map { row ->
                AppRow(
                    packageName = row.packageName,
                    label = row.label,
                    system = row.system,
                    scoped = row.scoped,
                    connected = row.packageName in connected,
                    recordCount = counts[row.packageName] ?: 0L,
                )
            },
            loading = prefs.loading,
            scopeReady = scopeState is ScopeState.Ready,
            pending = prefs.pending,
            refreshing = prefs.refreshing,
            showSystem = prefs.showSystem,
            showModules = prefs.showModules,
        )
        // V10 稳态状态流:内容未变(行/筛选/连接/作用域一致)不发射 → 列表零无谓重组。
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setQuery(value: String) {
        query.value = value
    }

    /** 下拉刷新:重扫应用列表 + 重读框架作用域(无论成败均复位刷新态)。 */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                catalog.refresh()
                scopeRepository.refresh()
            } finally {
                refreshing.value = false
            }
        }
    }

    /** 勾选 / 取消勾选(等价于 LSPosed 作用域变更,异常不悬挂 pending 态)。 */
    fun toggle(packageName: String) {
        if (packageName in pending.value) return
        viewModelScope.launch {
            pending.value = pending.value + packageName
            try {
                val result = scopeRepository.toggle(packageName)
                messages.emit(messageFor(packageName, result))
            } finally {
                pending.value = pending.value - packageName
            }
        }
    }

    private fun messageFor(packageName: String, result: ScopeChangeResult): UiMessage = when (result) {
        is ScopeChangeResult.ScopeRequested ->
            UiMessage(R.string.apps_scope_requested, listOf(packageName))
        is ScopeChangeResult.ScopeRemoved ->
            UiMessage(R.string.apps_scope_removed, listOf(packageName))
        is ScopeChangeResult.Failed -> scopeFailedMessage(packageName, result)
    }
}

/** M17:失败错误码 → 本地化文案(domain 零硬编码,Snackbar 语义由 UI 层承担)。 */
private fun scopeFailedMessage(packageName: String, failed: ScopeChangeResult.Failed): UiMessage =
    when (failed) {
        ScopeChangeResult.Failed.FrameworkNotReady ->
            UiMessage(R.string.apps_scope_failed_not_ready)
        ScopeChangeResult.Failed.ServiceNotConnected ->
            UiMessage(R.string.apps_scope_failed_service)
        ScopeChangeResult.Failed.UserRejected ->
            UiMessage(R.string.apps_scope_failed_rejected)
        ScopeChangeResult.Failed.Timeout ->
            UiMessage(R.string.apps_scope_failed_timeout)
        ScopeChangeResult.Failed.RemoveFailed ->
            UiMessage(R.string.apps_scope_failed_remove, listOf(packageName))
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