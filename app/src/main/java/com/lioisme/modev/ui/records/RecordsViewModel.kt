package com.lioisme.modev.ui.records

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.domain.record.RowModel
import com.lioisme.modev.domain.app.AppCatalogService
import com.lioisme.modev.domain.record.RecordFilter
import com.lioisme.modev.domain.record.RecordService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 记录列表 ViewModel(V24 G2 收敛;V40 T4 双维度:单应用 / 按类型全局两形态):
 * - 单一职责 = 以 [RecordFilter] 契约驱动分页记录流(MCP-ready 同一契约面);
 * - presetTypes 锁定记录类型(不可变);presetPackages 非 null = 单应用语境,
 *   **null = 按类型全局列表(全部应用)**——数据层哨兵语义(packages=null 不过滤);
 * - 可变面仅查询词与类别(D6:类别仅在类型唯一时生效,两种形态天然满足);
 * - 数据流:records `cachedIn` 多播缓存(V20 R3 防二次收集)+ VM 级 listState
 *   滚动保持(V20 R3.1 分页大窗口契约由仓库层承担);
 * - 全局形态可选携带目录源,产出 包名→应用名 映射(行内应用名上下文,
 *   目录已在内存加载,零逐行 binder)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    private val logs: RecordService,
    /** 预置锁定记录类型(wire 值;非空)。 */
    private val presetTypes: Set<String>,
    /** 预置锁定包名(非空 = 单应用;null = 按类型全局/全部应用)。 */
    private val presetPackages: Set<String>?,
    catalog: AppCatalogService? = null,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val kindName = MutableStateFlow<String?>(null)

    /** 列表滚动状态(VM 级:跨详情往返保持位置)。 */
    val listState = LazyListState()

    private fun buildFilter(q: String, kind: String?): RecordFilter = RecordFilter(
        recordTypes = presetTypes,
        packages = presetPackages,
        kinds = kind?.let { setOf(it) },
        query = q,
    )

    private val filterFlow = combine(query, kindName) { q, k -> buildFilter(q, k) }

    /** 分页记录流(时间倒序;多播缓存防二次收集)。 */
    val records: Flow<PagingData<RowModel>> =
        filterFlow.flatMapLatest { logs.observe(it) }.cachedIn(viewModelScope)

    /**
     * 匹配总数(V44 D1:列表计数副标唯一口径 = SQL COUNT,与 records_count 同源;
     * WhileSubscribed:仅列表页在前台时随表失效重查;null = 首帧未达。
     * 订阅面收敛于 RecordsCountSubtitle 叶组合器,页主体零连带)。
     */
    val totalCount: StateFlow<Long?> = filterFlow
        .flatMapLatest { logs.count(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 包名 → 应用名映射(全局列表行内上下文;目录未接入 = 恒空映射)。 */
    val packageLabels: StateFlow<Map<String, String>> =
        (catalog?.apps ?: MutableStateFlow(emptyList()))
            .map { list -> list.associate { it.packageName to it.label } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuery(value: String) {
        query.value = value
    }

    /** 类别过滤(名称跨枚举;null = 不限)。 */
    fun setKindName(value: String?) {
        kindName.value = value
    }

    /**
     * 清空当前语境全部记录(V37 T3.2 Appbar 动作;V40 T4 双形态:单应用 = 该应用
     * 该类型,全局 = 该类型全部应用——忽略搜索词与类别过滤,按预设语境
     * deleteByFilter;结果计数回调供确认提示)。
     */
    fun clearCurrentScope(onResult: (Long) -> Unit) {
        viewModelScope.launch {
            onResult(logs.deleteByFilter(buildFilter("", null)))
        }
    }
}

/**
 * 列表 VM 工厂:[packageName] 非 null = 单应用列表页(既有 10 目的地),
 * **null = 按类型全局列表**(V40 T4,数据管理页类型计数行入口)。
 */
class RecordsViewModelFactory(
    private val recordType: RecordType,
    private val packageName: String?,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordsViewModel(
            logs = container.records,
            presetTypes = setOf(recordType.wire),
            presetPackages = packageName?.let { setOf(it) },
            catalog = if (packageName == null) container.catalog else null,
        ) as T
    }
}
