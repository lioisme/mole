package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lioisme.modev.AppContainer
import com.lioisme.modev.domain.capability.Capability

/**
 * 设置页(通用):仅承载能力注册表驱动的「功能」区。
 * 数据/诊断/捕获配置各自收敛至二级页专属 ViewModel(V10 设置分级)。
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    /** 已注册监听能力(V10 能力化架构):设置页「功能」区驱动源,新增能力自动出现入口。 */
    val capabilities: List<Capability> = container.capabilityRegistry.all()
}

class SettingsViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(container) as T
    }
}
