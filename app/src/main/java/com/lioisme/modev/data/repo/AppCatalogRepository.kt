package com.lioisme.modev.data.repo

import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 已安装应用目录仓库:全量应用列表(PackageManager)。
 * 应用列表在启动时与包变化广播后刷新;仅含元数据,作用域/连接/计数由上层合并。
 */
class AppCatalogRepository(
    private val pm: PackageManager,
    private val scope: CoroutineScope,
) {

    data class AppInfo(
        val packageName: String,
        val label: String,
        val system: Boolean,
        val enabled: Boolean,

        /** 是否存在可用的启动入口(getLaunchIntentForPackage),决定"一键启动"按钮可用性。 */
        val launchable: Boolean,
    )

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    init {
        refresh()
    }

    /** 重新扫描已安装应用(启动 / 包变化广播 / 下拉刷新)。 */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val list = runCatching {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0).mapNotNull { ai ->
                    val packageName = ai.packageName ?: return@mapNotNull null
                    AppInfo(
                        packageName = packageName,
                        label = pm.getApplicationLabel(ai)?.toString() ?: packageName,
                        system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                        enabled = pm.getApplicationEnabledSetting(packageName) !=
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        launchable = pm.getLaunchIntentForPackage(packageName) != null,
                    )
                }
            }.getOrDefault(emptyList())
            // 终态排序唯一在 AppsViewModel(名称 Collator + 置顶,2026-08-17 V7):
            // repository 仅提供原始目录,不承担展示排序,防双源漂移。
            _apps.value = list
        }
    }
}