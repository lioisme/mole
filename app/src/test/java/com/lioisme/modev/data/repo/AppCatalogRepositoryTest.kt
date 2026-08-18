package com.lioisme.modev.data.repo

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AppCatalogRepository 契约(V16 R4 性能整改):
 * 全量/系统标记/label 兜底/加载态(loading 时序)/启动入口按需惰性解析 + 缓存;
 * enabled / 逐包 launchable 字段已移除(getInstalledApplications(0) 恒为 enabled 应用,
 * 启动入口改按需单次解析,列表扫描零 binder 往返)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppCatalogRepositoryTest {

    private fun packageInfo(packageName: String, system: Boolean = false): PackageInfo =
        PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                if (system) flags = flags or ApplicationInfo.FLAG_SYSTEM
            }
        }

    @Test
    fun `refresh maps installed apps with system flag and label fallback`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val shadow = shadowOf(pm)
        shadow.addPackage(packageInfo("com.user.app"))
        shadow.addPackage(packageInfo("com.sys.app", system = true))

        val repo = AppCatalogRepository(pm, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val apps = withTimeout(5_000) { repo.apps.first { it.isNotEmpty() } }
        // Robolectric 环境下被测应用自身也在已安装列表,过滤后断言目标包
        val targets = apps.filter { it.packageName in setOf("com.user.app", "com.sys.app") }

        assertEquals(setOf("com.user.app", "com.sys.app"), targets.map { it.packageName }.toSet())
        val user = targets.first { it.packageName == "com.user.app" }
        val sys = targets.first { it.packageName == "com.sys.app" }
        assertFalse(user.system)
        assertTrue(sys.system)
        // 未配置 labelRes 时兜底为包名
        assertEquals("com.user.app", user.label)
    }

    @Test
    fun `refresh after package removal drops the app`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        shadowOf(pm).addPackage(packageInfo("com.tmp.app"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = AppCatalogRepository(pm, scope)

        withTimeout(5_000) { repo.apps.first { it.any { a -> a.packageName == "com.tmp.app" } } }
        shadowOf(pm).removePackage("com.tmp.app")
        repo.refresh()

        val after = withTimeout(5_000) { repo.apps.first { it.none { a -> a.packageName == "com.tmp.app" } } }
        assertTrue(after.all { it.packageName != "com.tmp.app" })
    }

    @Test
    fun `resolveLaunchable resolves once and caches per package`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val shadow = shadowOf(pm)
        shadow.addPackage(packageInfo("com.launchable.app"))
        shadow.addPackage(packageInfo("com.nolaunch.app"))
        // 为 launchable 包注册 ACTION_MAIN + CATEGORY_LAUNCHER 入口(其余包无入口)
        val component = ComponentName("com.launchable.app", "com.launchable.app.MainActivity")
        shadow.addOrUpdateActivity(
            ActivityInfo().apply {
                this.packageName = "com.launchable.app"
                name = component.className
            }
        )
        shadow.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )

        val repo = AppCatalogRepository(pm, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.apps.first { it.isNotEmpty() } }

        assertTrue(repo.resolveLaunchable("com.launchable.app"))
        assertFalse(repo.resolveLaunchable("com.nolaunch.app"))
        // 缓存命中:重复解析(模拟多次进入同一应用记录页)不重新走 PackageManager
        assertTrue(repo.resolveLaunchable("com.launchable.app"))
    }

@Test
    fun `loading true during scan then false`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        // 800 个包拉宽扫描窗口:单包扫描 <1ms 与 flow 订阅存在竞态(loading 可能
        // 未被观测到即复位),包量足够时扫描耗时 ≫ 订阅耗时,观测确定性成立。
        repeat(800) { shadowOf(pm).addPackage(packageInfo("com.load.$it")) }

        val repo = AppCatalogRepository(pm, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        // init 触发首扫,loading 至少观测到 true(防首帧误显空态)
        withTimeout(10_000) { repo.loading.first { it } }
        withTimeout(10_000) { repo.loading.first { !it } }
        withTimeout(10_000) { repo.apps.first { it.size >= 800 } }
        Unit
    }

    @Test
    fun `concurrent refresh is serialized - single in-flight scan`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        shadowOf(pm).addPackage(packageInfo("com.serial.app"))

        val repo = AppCatalogRepository(pm, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        withTimeout(5_000) { repo.apps.first { it.isNotEmpty() } }

        repo.refresh()
        repo.refresh()
        repo.refresh()
        // 串行化后终态稳定(loading 恢复 false 且列表非空)
        withTimeout(5_000) { repo.loading.first { !it } }
        assertTrue(repo.apps.value.any { it.packageName == "com.serial.app" })
    }
}