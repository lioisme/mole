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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 单应用监听记录列表(V20:算法 / 数据库双界面共用一个 VM,recordType 分流):
 * 单一职责 = 某监听类型的**单应用**记录列表(packageName 必选)。
 * 数据流:分页记录(时间倒序,随搜索词 / 类别切换重建)。
 *
 * V20 R3 滚动位置保持:records 为 `cachedIn(viewModelScope)` 多播缓存流,
 * 详情页返回时 LazyPagingItems 重收集命中缓存(不再重建 Pager generation);
 * 列表滚动状态(listState)持有于 VM 级,返回后原位恢复(与应用列表同级体验)。
 *
 * V20 R3.1 分页契约修复(设备测试实锤,双问题):
 * 1. DAO 固定小 LIMIT 使 Room LimitOffsetPagingSource 计数被钳制 → 分页死锁
 *    (仓库层 PAGING_WINDOW_CAP 修复,见 LogRepository);
 * 2. 冷流改热流后,同一 PagingData 实例被返回重收集的 LazyPagingItems 二次
 *    收集 → PageFetcherSnapshot 单收集守卫抛 IllegalStateException;
 *    `cachedIn` 将每个 PagingData 包装为 MulticastedPagingData(多播),
 *    UI 收集者永不直接触碰 snapshot 单次流(官方推荐形态)。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonitorRecordsViewModel(
    private val recordType: RecordType,
    val packageName: String,
    private val logs: LogRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val kindName = MutableStateFlow<String?>(null)

    /** 列表滚动状态(VM 级:跨详情往返保持位置)。 */
    val listState = LazyListState()

    /** 分页记录流(多播缓存:kindName 承载 CryptoKind.name / SqlKind.name)。 */
    val records: Flow<PagingData<RowModel>> =
        combine(query, kindName) { q, k -> q to k }
            .flatMapLatest { (q, k) ->
                logs.pagedRecords(packageName = packageName, kindName = k, query = q, recordType = recordType.wire)
            }
            .cachedIn(viewModelScope)

    fun setQuery(value: String) {
        query.value = value
    }

    /** 类别过滤(名称跨枚举;null = 不限)。 */
    fun setKindName(value: String?) {
        kindName.value = value
    }
}

/** 简单工厂,避免在 Compose 中手动拼参。 */
class MonitorRecordsViewModelFactory(
    private val recordType: RecordType,
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MonitorRecordsViewModel(
            recordType = recordType,
            packageName = packageName,
            logs = container.logs,
        ) as T
    }
}