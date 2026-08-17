package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 数据管理页(V10 设置分级):全库记录总数 + 清空全部记录(危险操作)。
 */
class DataSettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val totalRecords: StateFlow<Long> = container.logs.observeRecordCounts()
        .map { counts -> counts.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun clearAll() {
        viewModelScope.launch { container.logs.clearAll() }
    }
}

class DataSettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DataSettingsViewModel(container) as T
    }
}
