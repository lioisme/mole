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

/** AppCatalogRepository 映射:全量/系统标记/enabled 状态/label 兜底(Robolectric ShadowPackageManager)。 */
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
        assertTrue(user.enabled)
    }

    @Test
    fun `disabled app maps enabled false`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        shadowOf(pm).addPackage(packageInfo("com.dis.app"))
        pm.setApplicationEnabledSetting("com.dis.app", PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)

        val repo = AppCatalogRepository(pm, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val apps = withTimeout(5_000) { repo.apps.first { it.isNotEmpty() } }

        assertFalse(apps.first { it.packageName == "com.dis.app" }.enabled)
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
    fun `launchable reflects launcher activity availability`() = runBlocking {
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
        val apps = withTimeout(5_000) { repo.apps.first { it.isNotEmpty() } }

        assertTrue(apps.first { it.packageName == "com.launchable.app" }.launchable)
        assertFalse(apps.first { it.packageName == "com.nolaunch.app" }.launchable)
    }
}