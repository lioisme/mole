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
 * 单应用记录列表 ViewModel(V24 G2 收敛:records/all 退役,预设语境必选):
 * - 单一职责 = 以 [RecordFilter] 契约驱动分页记录流(MCP-ready 同一契约面);
 * - presetTypes/presetPackages 锁定单应用语境(类型 + 包名不可变);
 * - 可变面仅查询词与类别(D6:类别仅在类型唯一时生效,预设天然满足);
 * - 数据流:records `cachedIn` 多播缓存(V20 R3 防二次收集)+ VM 级 listState
 *   滚动保持(V20 R3.1 分页大窗口契约由仓库层承担)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModel(
    private val logs: LogRepository,
    /** 预置锁定记录类型(wire 值;非空)。 */
    private val presetTypes: Set<String>,
    /** 预置锁定包名(非空)。 */
    private val presetPackages: Set<String>,
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
        filterFlow.flatMapLatest { logs.paged(it) }.cachedIn(viewModelScope)

    /** 匹配总数(过滤语义对账面;列表页统计行预留)。 */
    val totalCount: Flow<Long> = filterFlow.flatMapLatest { logs.count(it) }

    fun setQuery(value: String) {
        query.value = value
    }

    /** 类别过滤(名称跨枚举;null = 不限)。 */
    fun setKindName(value: String?) {
        kindName.value = value
    }
}

/** 简单工厂(recordType + packageName 必选,单应用列表页专用)。 */
class RecordsViewModelFactory(
    private val recordType: RecordType,
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordsViewModel(
            logs = container.logs,
            presetTypes = setOf(recordType.wire),
            presetPackages = setOf(packageName),
        ) as T
    }
}
