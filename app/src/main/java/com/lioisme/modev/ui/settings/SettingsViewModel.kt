package com.lioisme.modev.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lioisme.modev.AppContainer

/**
 * 设置页(通用)V25:页面全部状态为本地组合态(外观/主题/语言 sheet),
 * 功能区仅剩静态入口(数据管理 / 服务与诊断),VM 无需承载任何流 ——
 * 监听配置入口已于 V25 移交每应用「监听设置」页。
 * V45 T2.6:原 `settings: SettingsRepository` 参数(带 @Suppress("unused"))为死依赖,
 * 已删除 —— 无消费方的依赖不占类型面。
 */
class SettingsViewModel : ViewModel()

class SettingsViewModelFactory(
    /** 与其余工厂同签名(页面装配统一传容器);本 VM 零依赖,故不消费。 */
    @Suppress("unused") private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel() as T
    }
}
