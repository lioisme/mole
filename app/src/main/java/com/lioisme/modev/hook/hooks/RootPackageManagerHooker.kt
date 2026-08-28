package com.lioisme.modev.hook.hooks

import android.annotation.SuppressLint
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Root 包管理器查询干预 Hooker(V31):拦截 PackageManager 的包列表查询方法,
 * 从返回结果中过滤 root 管理应用,阻止应用通过已安装包列表检测 root。
 *
 * 注意:PackageInfo 的包名是 public field `packageName`,不是方法 `getPackageName()`。
 * 使用 field 访问而非反射调用不存在的方法。
 */
class RootPackageManagerHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookGetInstalledPackages()
        hookGetInstalledApplications()
        hookQueryIntentActivities()
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun hookGetInstalledPackages() {
        val clazz = runCatching { Class.forName("android.app.ApplicationPackageManager") }.getOrNull()
            ?: runCatching { Class.forName("android.content.pm.PackageManager") }.getOrNull()
            ?: return
        val method = runCatching {
            clazz.getMethod("getInstalledPackages", Int::class.javaPrimitiveType)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().rootPackageManager) return
                val packages = invocation.result as? List<*> ?: return
                val filtered = packages.filter { pkg ->
                    val name = runCatching {
                        pkg?.javaClass?.getField("packageName")?.get(pkg) as? String
                    }.getOrNull()
                    name == null || !isRootPackage(name)
                }
                if (filtered.size != packages.size) {
                    invocation.setResult(filtered)
                    emit(
                        kind = DeviceEnvKind.ROOT_PACKAGE_MANAGER,
                        operation = DeviceEnvOperation.GET_INSTALLED_PACKAGES,
                        originalValue = packages.size.toString(),
                        maskedValue = filtered.size.toString(),
                        targetClass = clazz.name,
                        detectionPattern = ROOT_PACKAGE_PATTERN,
                        params = mapOf(
                            "filtered" to (packages.size - filtered.size).toString(),
                        ),
                    )
                }
            }
        })
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun hookGetInstalledApplications() {
        val clazz = runCatching { Class.forName("android.app.ApplicationPackageManager") }.getOrNull()
            ?: runCatching { Class.forName("android.content.pm.PackageManager") }.getOrNull()
            ?: return
        val method = runCatching {
            clazz.getMethod("getInstalledApplications", Int::class.javaPrimitiveType)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().rootPackageManager) return
                val apps = invocation.result as? List<*> ?: return
                val filtered = apps.filter { app ->
                    val name = runCatching {
                        app?.javaClass?.getField("packageName")?.get(app) as? String
                    }.getOrNull()
                    name == null || !isRootPackage(name)
                }
                if (filtered.size != apps.size) {
                    invocation.setResult(filtered)
                    emit(
                        kind = DeviceEnvKind.ROOT_PACKAGE_MANAGER,
                        operation = DeviceEnvOperation.GET_INSTALLED_APPLICATIONS,
                        originalValue = apps.size.toString(),
                        maskedValue = filtered.size.toString(),
                        targetClass = clazz.name,
                        detectionPattern = ROOT_PACKAGE_PATTERN,
                        params = mapOf(
                            "filtered" to (apps.size - filtered.size).toString(),
                        ),
                    )
                }
            }
        })
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun hookQueryIntentActivities() {
        val clazz = runCatching { Class.forName("android.app.ApplicationPackageManager") }.getOrNull()
            ?: runCatching { Class.forName("android.content.pm.PackageManager") }.getOrNull()
            ?: return
        val intentClass = runCatching { Class.forName("android.content.Intent") }.getOrNull() ?: return
        val flagsClass = Int::class.javaPrimitiveType ?: return
        val method = runCatching {
            clazz.getMethod("queryIntentActivities", intentClass, flagsClass)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().rootPackageManager) return
                val activities = invocation.result as? List<*> ?: return
                val filtered = activities.filter { activity ->
                    val name = runCatching {
                        activity?.javaClass?.getField("packageName")?.get(activity) as? String
                    }.getOrNull()
                    name == null || !isRootPackage(name)
                }
                if (filtered.size != activities.size) {
                    invocation.setResult(filtered)
                    emit(
                        kind = DeviceEnvKind.ROOT_PACKAGE_MANAGER,
                        operation = DeviceEnvOperation.QUERY_INTENT_ACTIVITIES,
                        originalValue = activities.size.toString(),
                        maskedValue = filtered.size.toString(),
                        targetClass = clazz.name,
                        detectionPattern = ROOT_PACKAGE_PATTERN,
                        params = mapOf(
                            "filtered" to (activities.size - filtered.size).toString(),
                        ),
                    )
                }
            }
        })
    }

    private fun isRootPackage(packageName: String): Boolean =
        ROOT_PACKAGES.any { it.equals(packageName, ignoreCase = true) }

    companion object {

        private const val ROOT_PACKAGE_PATTERN = "root-package"

        private val ROOT_PACKAGES = listOf(
            "com.topjohnwu.magisk",
            "com.topjohnwu.magisk.manager",
            "com.koushikdutta.superuser",
            "com.koushikdutta.superuser.debug",
            "eu.chainfire.supersu",
            "eu.chainfire.supersu.pro",
            "eu.chainfire.supersu-free",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "com.thirdparty.superuser",
            "com.thirdparty.superuser.debug",
            "com.kingroot.kinguser",
            "com.kingroot.kinguserRSA",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot",
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "com.saurik.substrate",
            "me.phh.superuser",
            "eu.chainfire.supersu.su",
            "com.topjohnwu.magisk.tool",
            "com.kingoroot.rootdeluxetools",
            "com.permutive.root.rootmanagement",
        )
    }
}
