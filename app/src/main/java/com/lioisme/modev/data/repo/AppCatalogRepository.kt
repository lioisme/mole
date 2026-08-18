package com.lioisme.modev.data.repo

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.lioisme.modev.core.util.installedApplications
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val ASSETS_XPOSED_INIT = "assets/xposed_init"
private const val META_INF_JAVA_INIT = "META-INF/xposed/java_init.list"

/**
 * Xposed 模块 APK 资产级检测(V17 R1 模块识别第二通道):
 * 只读 zip 中央目录与目标条目,命中任一即视为模块——
 * - `META-INF/xposed/java_init.list`(新式 Java init 模块,条目存在即算);
 * - `assets/xposed_init` 非空(经典 Xposed 模块;空文件视为非模块,与框架语义一致)。
 * 任何 IO 异常一律返回 false(安全兜底,不阻断目录加载)。
 */
internal fun isXposedModuleApk(apkPath: String): Boolean = runCatching {
    ZipFile(apkPath).use { zip ->
        if (zip.getEntry(META_INF_JAVA_INIT) != null) {
            true
        } else {
            val init = zip.getEntry(ASSETS_XPOSED_INIT) ?: return@use false
            init.size > 0 || zip.getInputStream(init).use { it.read() != -1 }
        }
    }
}.getOrDefault(false)

/**
 * 已安装应用目录仓库:全量应用列表(PackageManager)。
 * 应用列表在启动时与包变化广播后刷新;仅含元数据,作用域/连接/计数由上层合并。
 * V16 R4 性能整改:剔除逐包 getApplicationEnabledSetting / getLaunchIntentForPackage
 * (每应用 2 次 binder 往返)→ 启动入口改按需惰性解析 + 内存缓存;新增 loading 态。
 * V17 R1 模块识别:GET_META_DATA 单次扫描 + meta-data 通道优先,资产通道按包缓存。
 */
class AppCatalogRepository(
    private val pm: PackageManager,
    private val scope: CoroutineScope,
) : AppCatalogSource {

    data class AppInfo(
        val packageName: String,
        val label: String,
        val system: Boolean,
        /** Xposed 模块(V17 R1 模块识别;列表默认隐藏,设置项可显示)。 */
        val isXposedModule: Boolean = false,
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

    /** 模块识别资产通道结果缓存(包级;refresh 不清——资产随安装决定,进程内一致)。 */
    private val moduleCache = mutableMapOf<String, Boolean>()

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
                    // V17 R1:GET_META_DATA,meta-data 通道所需
                    pm.installedApplications(PackageManager.GET_META_DATA).mapNotNull { ai ->
                        val packageName = ai.packageName ?: return@mapNotNull null
                        AppInfo(
                            packageName = packageName,
                            label = pm.getApplicationLabel(ai)?.toString() ?: packageName,
                            system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            isXposedModule = isModule(ai, packageName),
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

    /**
     * 模块识别双通道(V17 R1):
     * A. meta-data 通道(`xposedmodule` / `xposedminversion`,框架元数据约定);
     * B. 资产通道(APK zip 级检测,仅 A 未命中时触发,结果按包缓存)。
     * 仅 refresh 的 IO 协程内调用(串行化),缓存免锁。
     */
    private fun isModule(ai: ApplicationInfo, packageName: String): Boolean {
        val meta = ai.metaData
        if (meta != null && (meta.getBoolean("xposedmodule") || meta.containsKey("xposedminversion"))) {
            return true
        }
        val sourceDir = ai.sourceDir ?: return false
        return moduleCache.getOrPut(packageName) { isXposedModuleApk(sourceDir) }
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