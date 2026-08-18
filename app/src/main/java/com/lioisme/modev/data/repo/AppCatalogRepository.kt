package com.lioisme.modev.data.repo

import android.content.pm.PackageManager
import com.lioisme.modev.core.util.installedApplications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 已安装应用目录仓库:全量应用列表(PackageManager)。
 * 应用列表在启动时与包变化广播后刷新;仅含元数据,作用域/连接/计数由上层合并。
 * V16 R4 性能整改:剔除逐包 getApplicationEnabledSetting / getLaunchIntentForPackage
 * (每应用 2 次 binder 往返)→ 启动入口改按需惰性解析 + 内存缓存;新增 loading 态。
 */
class AppCatalogRepository(
    private val pm: PackageManager,
    private val scope: CoroutineScope,
) : AppCatalogSource {

    data class AppInfo(
        val packageName: String,
        val label: String,
        val system: Boolean,
    )

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    override val apps: StateFlow<List<AppInfo>> = _apps

    private val _loading = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = _loading

    /** 串行化刷新(并发调用只保留首笔,防 loading 态抖动与乱序终态)。 */
    private var refreshJob: Job? = null

    /** 启动入口解析缓存(包级,解析一次终身可用;refresh 不失效——入口随包安装决定)。 */
    private val launchableCache = mutableMapOf<String, Boolean>()
    private val cacheMutex = Mutex()

    init {
        refresh()
    }

    /** 重新扫描已安装应用(启动 / 包变化广播 / 下拉刷新)。 */
    override fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch(Dispatchers.IO) {
            _loading.value = true
            try {
                val list = runCatching {
                    // V16 R6:弃用 API 现代化(ApplicationInfoFlags 重载,统一入口)
                    pm.installedApplications().mapNotNull { ai ->
                        val packageName = ai.packageName ?: return@mapNotNull null
                        AppInfo(
                            packageName = packageName,
                            label = pm.getApplicationLabel(ai)?.toString() ?: packageName,
                            system = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                        )
                    }
                }.getOrDefault(emptyList())
                // 终态排序唯一在 AppsViewModel(名称 Collator + 置顶,2026-08-17 V7):
                // repository 仅提供原始目录,不承担展示排序,防双源漂移。
                _apps.value = list
            } finally {
                _loading.value = false
            }
        }
    }

    /** 按需解析启动入口(V16 R4:单次 binder 调用 + 缓存,列表扫描零开销;getLaunchIntentForPackage 全 API 稳定)。 */
    override suspend fun resolveLaunchable(packageName: String): Boolean {
        cacheMutex.withLock { launchableCache[packageName] }?.let { return it }
        val resolved = withContext(Dispatchers.IO) {
            runCatching { pm.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false)
        }
        cacheMutex.withLock {
            launchableCache[packageName] = resolved
        }
        return resolved
    }
}