package com.lioisme.modev.data.repo

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
 * AppCatalogRepository 契约:
 * 全量/系统标记/label 兜底/加载态(loading 时序)/启动入口按需惰性解析 + 缓存;
 * enabled / 逐包 launchable 字段已移除(getInstalledApplications(0) 恒为 enabled 应用,
 * 启动入口改按需单次解析,列表扫描零 binder 往返)。
 * V17 R1 模块识别:meta-data 通道(免 IO)+ 资产通道(临时 zip 模拟 APK)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppCatalogRepositoryTest {

    private fun packageInfo(
        packageName: String,
        system: Boolean = false,
        metaData: Bundle? = null,
        sourceDir: String? = null,
    ): PackageInfo =
        PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().apply {
                this.packageName = packageName
                if (system) flags = flags or ApplicationInfo.FLAG_SYSTEM
                this.metaData = metaData
                this.sourceDir = sourceDir
            }
        }

    /** 以 zip 结构模拟 APK 文件(资产通道检测目标)。 */
    private fun fakeApk(vararg entries: Pair<String, String>): String {
        val dir = createTempDir(prefix = "modev-apk-")
        val file = File(dir, "fake.apk")
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return file.absolutePath
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
    fun `metaData channel marks xposed modules`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val shadow = shadowOf(pm)
        shadow.addPackage(
            packageInfo(
                "com.module.bool",
                metaData = Bundle().apply { putBoolean("xposedmodule", true) },
            ),
        )
        shadow.addPackage(
            packageInfo(
                "com.module.min",
                metaData = Bundle().apply { putInt("xposedminversion", 82) },
            ),
        )
        shadow.addPackage(packageInfo("com.plain.app"))

        val repo = AppCatalogRepository(pm, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val apps = withTimeout(5_000) { repo.apps.first { it.isNotEmpty() } }
        // 被测应用自身(真实 manifest 含 xposedminversion)也会命中 meta-data 通道,过滤后断言目标包
        val modules = apps
            .filter { it.isXposedModule && it.packageName in setOf("com.module.bool", "com.module.min") }
            .map { it.packageName }
            .toSet()
        assertEquals(setOf("com.module.bool", "com.module.min"), modules)
    }

    @Test
    fun `asset channel flags module apks via xposed_init and java_init_list`() = runBlocking {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val shadow = shadowOf(pm)
        shadow.addPackage(
            packageInfo("com.module.assets", sourceDir = fakeApk("assets/xposed_init" to "com.example.Init\n")),
        )
        shadow.addPackage(
            packageInfo("com.module.java", sourceDir = fakeApk("META-INF/xposed/java_init.list" to "")),
        )
        shadow.addPackage(
            packageInfo("com.plain.apk", sourceDir = fakeApk("classes.dex" to "dex")),
        )
        shadow.addPackage(
            packageInfo("com.empty.init", sourceDir = fakeApk("assets/xposed_init" to "")),
        )

        val repo = AppCatalogRepository(pm, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        val apps = withTimeout(5_000) { repo.apps.first { it.isNotEmpty() } }
        // 被测应用自身命中 meta-data 通道,过滤后断言目标包
        val modules = apps
            .filter { it.isXposedModule && it.packageName in setOf("com.module.assets", "com.module.java") }
            .map { it.packageName }
            .toSet()
        assertEquals(setOf("com.module.assets", "com.module.java"), modules)
    }

    @Test
    fun `isXposedModuleApk detects by asset entry and tolerates missing files`() {
        assertTrue(isXposedModuleApk(fakeApk("assets/xposed_init" to "com.example.Init")))
        assertTrue(isXposedModuleApk(fakeApk("META-INF/xposed/java_init.list" to "")))
        assertFalse(isXposedModuleApk(fakeApk("assets/xposed_init" to "")))
        assertFalse(isXposedModuleApk(fakeApk("classes.dex" to "x")))
        val missing = File(createTempDir(prefix = "modev-missing-"), "missing.apk")
        assertFalse(isXposedModuleApk(missing.absolutePath))
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
