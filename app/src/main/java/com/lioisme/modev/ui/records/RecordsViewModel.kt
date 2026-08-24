package com.lioisme.modev.ui.records

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.data.mapping.RowModel
import com.lioisme.modev.data.repo.LogRepository
import com.lioisme.modev.data.repo.RecordFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 记录列表统一 ViewModel(V23 T4.1,自单应用双页 VM 泛化;全局/单应用三页共用):
 * - 单一职责 = 以 [RecordFilter] 契约驱动分页记录流(MCP-ready 同一契约面);
 * - 单应用页:presetTypes/presetPackages 锁定语境(类型 + 包名不可变);
 * - 全局页:类型(全部/算法/数据库)与应用多选均可变,D6 联动 =
 *   类别筛选仅在生效类型唯一时进入 filter;
 * - 数据流:records `cachedIn` 多播缓存(V20 R3 防二次收集)+ VM 级 listState
 *   滚动保持(V20 R3.1 分页大窗口契约由仓库层承担)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    private val logs: LogRepository,
    /** 预置锁定记录类型(单应用页);null = 全局页。 */
    private val presetTypes: Set<String>? = null,
    /** 预置锁定包名(单应用页);null = 全局页。 */
    private val presetPackages: Set<String>? = null,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val kindName = MutableStateFlow<String?>(null)

    /** 记录类型选择(仅全局页可变;null = 全部类型)。 */
    private val typeFilter = MutableStateFlow<RecordType?>(null)

    /** 应用多选(仅全局页可变;null = 不限)。 */
    private val packageFilter = MutableStateFlow<Set<String>?>(presetPackages)

    /** 列表滚动状态(VM 级:跨详情往返保持位置)。 */
    val listState = LazyListState()

    private fun buildFilter(
        q: String,
        kind: String?,
        type: RecordType?,
        packages: Set<String>?,
    ): RecordFilter {
        val types = presetTypes ?: type?.let { setOf(it.wire) }
        val kinds = if (types != null) kind?.let { setOf(it) } else null
        return RecordFilter(
            recordTypes = types,
            packages = presetPackages ?: packages?.ifEmpty { null },
            kinds = kinds,
            query = q,
        )
    }

    private val filterFlow = combine(query, kindName, typeFilter, packageFilter) { q, k, t, p ->
        buildFilter(q, k, t, p)
    }

    /** 分页记录流(时间倒序;多播缓存防二次收集)。 */
    val records: Flow<PagingData<RowModel>> =
        filterFlow.flatMapLatest { logs.paged(it) }.cachedIn(viewModelScope)

    /** 匹配总数(统计行/测试对账)。 */
    val totalCount: Flow<Long> = filterFlow.flatMapLatest { logs.count(it) }

    /** 出现过记录的包名集合(应用多选选择器数据源)。 */
    val knownPackages: Flow<List<String>> = logs.observePackages()

    fun setQuery(value: String) {
        query.value = value
    }

    /** 类别过滤(名称跨枚举;null = 不限)。 */
    fun setKindName(value: String?) {
        kindName.value = value
    }

    /** 记录类型切换(仅全局页;切走时类别失效防跨类型残留)。 */
    fun setTypeFilter(value: RecordType?) {
        if (presetTypes != null) return
        typeFilter.value = value
        if (value == null) kindName.value = null
    }

    /**
     * 应用多选集合下发(仅全局页;空集 = 不限)。
     * 与 query/kind 同模式:UI saveable 态经 LaunchedEffect 单向下发。
     */
    fun setPackages(value: Set<String>) {
        if (presetPackages != null) return
        packageFilter.value = value.ifEmpty { null }
    }
}

/** 简单工厂(recordType/packageName 均为 null 时即全局记录页)。 */
class RecordsViewModelFactory(
    private val recordType: RecordType?,
    private val packageName: String?,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordsViewModel(
            logs = container.logs,
            presetTypes = recordType?.let { setOf(it.wire) },
            presetPackages = packageName?.let { setOf(it) },
        ) as T
    }
}
