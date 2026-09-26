package com.lioisme.modev.data.repo

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用列表偏好持久化单一入口(2026-08-17 V7 对齐 ThemeSettingsManager 范式;
 * V11 L7 自 ui.apps 移入数据层——持久化基础设施不再停留于 UI 包;
 * V14 R7/R8:置顶默认开 + 筛选项持久化,ViewModel 零 Context 依赖;
 * V22 R2:置顶开关自通用设置页迁回应用列表筛选菜单(与系统应用 / 模块并列);
 * V23 R4:init 缓存 applicationContext,写接口去 Context 化——UI 不再直调本对象,
 * 变更一律经 ViewModel 下发(UI 仅收集 [StateFlow] 展示)):
 * StateFlow 承载内存态,SharedPreferences 持久化;init 须在进程启动
 * (ModevApp.onCreate)调用一次,此后各 setX 同步写双份。
 * 应用列表排序(名称 Collator / 已作用域置顶)与设置页共享本单一事实源。
 */
object AppListSettings : com.lioisme.modev.domain.app.AppListPreferences {
    private const val PREF_NAME = "modev_applist"
    private const val KEY_PIN_SCOPED = "pin_scoped_on_top"
    private const val KEY_SHOW_SYSTEM = "show_system"
    private const val KEY_SHOW_MODULES = "show_xposed_modules"

    private lateinit var appContext: Context

    private val _pinScoped = MutableStateFlow(true)

    /** 已作用域应用置顶(默认开,V22 R2 筛选菜单入口)。 */
    override val pinScoped: StateFlow<Boolean> = _pinScoped.asStateFlow()

    private val _showSystem = MutableStateFlow(true)

    /** 显示系统应用。 */
    override val showSystem: StateFlow<Boolean> = _showSystem.asStateFlow()

    private val _showModules = MutableStateFlow(false)

    /** 显示 Xposed 模块应用(V17 R1 默认隐藏,设置项可显示)。 */
    override val showModules: StateFlow<Boolean> = _showModules.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _pinScoped.value = prefs.getBoolean(KEY_PIN_SCOPED, true)
        _showSystem.value = prefs.getBoolean(KEY_SHOW_SYSTEM, true)
        _showModules.value = prefs.getBoolean(KEY_SHOW_MODULES, false)
    }

    override fun setPinScoped(value: Boolean) {
        persist(KEY_PIN_SCOPED, value)
        _pinScoped.value = value
    }

    override fun setShowSystem(value: Boolean) {
        persist(KEY_SHOW_SYSTEM, value)
        _showSystem.value = value
    }

    override fun setShowModules(value: Boolean) {
        persist(KEY_SHOW_MODULES, value)
        _showModules.value = value
    }

    private fun persist(
        key: String,
        value: Boolean,
    ) {
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }
}
