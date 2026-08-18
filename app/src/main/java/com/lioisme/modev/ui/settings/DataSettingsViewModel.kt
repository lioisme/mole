package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.repo.LogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 数据管理页(V10 设置分级):全库记录总数 + 清空全部记录(危险操作)。
 * V11 M4:依赖收窄为 [LogRepository],不再持有整个容器。
 */
class DataSettingsViewModel(
    private val logs: LogRepository,
) : ViewModel() {

    val totalRecords: StateFlow<Long> = logs.observeRecordCounts()
        .map { counts -> counts.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

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
