package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.data.repo.LogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 数据管理页(V10 设置分级;V19 T7 扩展):
 * 分类计数(算法/数据库)+ 全库总数 + 按类型清理 / 清空全部(危险操作)。
 * V11 M4:依赖收窄为 [LogRepository],不再持有整个容器。
 */
class DataSettingsViewModel(
    private val logs: LogRepository,
) : ViewModel() {

    val totalRecords: StateFlow<Long> = logs.observeRecordCounts()
        .map { counts -> counts.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 按记录类型计数(wire → count;缺失类型 = 0,UI 层按 [RecordType] 取用)。 */
    val typeCounts: StateFlow<Map<String, Long>> = logs.observeRecordTypeCounts()
        .map { list -> list.associate { it.recordType to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun clearByType(type: RecordType) {
        viewModelScope.launch { logs.clearByType(type.wire) }
    }

    fun clearAll() {
        viewModelScope.launch { logs.clearAll() }
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