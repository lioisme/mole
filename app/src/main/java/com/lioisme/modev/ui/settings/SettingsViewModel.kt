package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.data.server.ConfigServer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页:服务状态与数据治理。
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val connectedCount: StateFlow<Int> = container.server.connectedPackagesFlow
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun clearAll() {
        viewModelScope.launch { container.logs.clearAll() }
    }
}

class SettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(container) as T
    }
}