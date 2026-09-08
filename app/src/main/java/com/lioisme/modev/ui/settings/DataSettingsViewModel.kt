package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.LogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 数据管理页(V10 设置分级;V19 T7 扩展;V40 T4 双维度记录浏览;V41 T5 收敛):
 * 类型计数 + 全库总数 + 清空全部(危险操作;V40 起按类型清理并入各记录列表,
 * 本页退役 clearByType)。V41:「按应用浏览」退役——packageCounts/packageLabels
 * 页面接线拆除(仓库 `observeRecordCounts` 保留,AppsViewModel 列表徽章在消费);
 * 应用维度浏览唯一入口 = 主界面应用列表。
 * V11 M4:依赖收窄为 [LogRepository],不再持有整个容器。
 * V23 R4:存储占用改经仓库出口([LogRepository.storageUsage],DB+wal+shm),
 * UI 禁再直触 data.db;计数流为 Room 实时流(清理后自动刷新),
 * 存储占用为快照读,由 [invalidated] 触发器在清理完成后重取。
 */
class DataSettingsViewModel(
    private val logs: LogRepository,
) : ViewModel() {

    /** 存储占用重取信号(replay=1 保证订阅即取初值;DROP_OLDEST 防连发堆积)。 */
    private val invalidated = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(Unit) }

    val totalRecords: StateFlow<Long> = logs.observeRecordCounts()
        .map { counts -> counts.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 按记录类型计数(wire → count;缺失类型 = 0,UI 层按 [RecordType] 取用)。 */
    val typeCounts: StateFlow<Map<String, Long>> = logs.observeRecordTypeCounts()
        .map { list -> list.associate { it.recordType to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 存储占用字节(DB + -wal + -shm;仓库唯一出口,V23 R4 解耦契约)。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val storageBytes: StateFlow<Long> = invalidated
        .flatMapLatest { flow { emit(logs.storageUsage()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun clearAll() {
        viewModelScope.launch {
            logs.clearAll()
            invalidated.tryEmit(Unit)
        }
    }
}

class DataSettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DataSettingsViewModel(container.logs) as T
    }
}
