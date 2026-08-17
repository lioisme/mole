package com.lioisme.modev.ui.apps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用列表偏好持久化单一入口(2026-08-17 V7,对齐 ThemeSettingsManager 范式):
 * StateFlow 承载内存态,SharedPreferences 持久化;init 须在进程启动
 * (ModevApp.onCreate)调用一次,此后 setPinScoped 同步写双份。
 * 应用列表排序(名称 Collator / 已作用域置顶)与设置页共享本单一事实源。
 */
object AppListSettings {
    private const val PREF_NAME = "modev_applist"
    private const val KEY_PIN_SCOPED = "pin_scoped_on_top"

    private val _pinScoped = MutableStateFlow(false)

    /** 已作用域应用置顶(默认关,社区规范 = 默认纯名称排序)。 */
    val pinScoped: StateFlow<Boolean> = _pinScoped.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _pinScoped.value = prefs.getBoolean(KEY_PIN_SCOPED, false)
    }

    fun setPinScoped(
        value: Boolean,
        context: Context,
    ) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PIN_SCOPED, value)
            .apply()
        _pinScoped.value = value
    }
}