package com.lioisme.modev.hook.hooks

import android.annotation.SuppressLint
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

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
        hookGetPackageInfo()
    }

    /**
     * `packageName` 字段句柄按类缓存(V44 T2.5):旧形态在逐包 filter 里对每个元素
     * 做 `javaClass.getField("packageName")`——列表 300+ 项时每调用一次 getPackageInfo/
     * getInstalledPackages 就是 300+ 次反射解析(外加首次 miss 的异常构造)。
     * [com.lioisme.modev.hook.ClassValueCache] = 每类一份、随类卸载回收;
     * 类内同步 map 允许 null 值(缺失同样缓存)。
     */
    private val packageNameFields = com.lioisme.modev.hook.ClassValueCache {
        java.util.Collections.synchronizedMap(HashMap<String, java.lang.reflect.Field?>())
    }

    /** 元素包名(逐包反射句柄缓存;非 PackageInfo 形态返回 null)。 */
    private fun packageNameOf(element: Any?): String? {
        val e = element ?: return null
        val perClass = packageNameFields.get(e.javaClass)
        val field = synchronized(perClass) {
            if (perClass.containsKey(FIELD_PACKAGE_NAME)) {
                perClass[FIELD_PACKAGE_NAME]
            } else {
                runCatching { e.javaClass.getField(FIELD_PACKAGE_NAME) }
                    .getOrNull()
                    .also { perClass[FIELD_PACKAGE_NAME] = it }
            }
        } ?: return null
        return runCatching { field.get(e) as? String }.getOrNull()
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
                if (!featureOn(DeviceEnvKind.ROOT_PACKAGE_MANAGER)) return
                val packages = invocation.result as? List<*> ?: return
                val filtered = packages.filter { pkg ->
                    val name = packageNameOf(pkg)
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
                if (!featureOn(DeviceEnvKind.ROOT_PACKAGE_MANAGER)) return
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
                if (!featureOn(DeviceEnvKind.ROOT_PACKAGE_MANAGER)) return
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

    /** Root 包名判定(V37 T2.4):内置基线 ∪ 用户追加项(extraRootPackages)。 */
    private fun isRootPackage(packageName: String): Boolean =
        ROOT_PACKAGES.any { it.equals(packageName, ignoreCase = true) } ||
            config().feature.extraRootPackages.any { it.isNotBlank() && it.equals(packageName, ignoreCase = true) }

    @SuppressLint("DiscouragedPrivateApi")
    private fun hookGetPackageInfo() {
        val clazz = runCatching { Class.forName("android.app.ApplicationPackageManager") }.getOrNull()
            ?: runCatching { Class.forName("android.content.pm.PackageManager") }.getOrNull()
            ?: return
        val method = runCatching {
            clazz.getMethod("getPackageInfo", String::class.java, Int::class.javaPrimitiveType)
        }.getOrNull() ?: return

        // V43 T3/D-4:与 AppInfoHooker(观测)共享单一广播注册点;契约忠实化(V2.5):
        // setResult(null) 会让未判空的宿主 NPE——getPackageInfo 的「查无此包」契约是
        // 抛 NameNotFoundException,经 setThrowable 干预型注册抵达调用方 catch 分支。
        broadcast.subscribe(
            method,
            object : SharedMethodBroadcast.Sub {
                override fun before(invocation: Invocation) {
                    if (!featureOn(DeviceEnvKind.ROOT_PACKAGE_MANAGER)) return
                    val packageName = invocation.getArg(0) as? String ?: return
                    if (!isRootPackage(packageName)) return
                    emit(
                        kind = DeviceEnvKind.ROOT_PACKAGE_MANAGER,
                        operation = DeviceEnvOperation.GET_INSTALLED_PACKAGES,
                        originalValue = packageName,
                        maskedValue = null,
                        targetClass = clazz.name,
                        detectionPattern = ROOT_PACKAGE_PATTERN,
                        params = mapOf("api" to "getPackageInfo"),
                    )
                    invocation.setThrowable(
                        android.content.pm.PackageManager.NameNotFoundException(packageName),
                    )
                }
            },
        )
    }

    companion object {

        private const val ROOT_PACKAGE_PATTERN = "root-package"

        /** PackageInfo/ActivityInfo/ApplicationInfo 的公开包名字段(V44 T2.5 缓存键)。 */
        private const val FIELD_PACKAGE_NAME = "packageName"

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
            // V35: Lsposed/EdXposed/XposedManager
            "org.lsposed.lspatch",
            "org.lsposed.manager",
            "io.github.lsposed.edxposed.manager",
            "de.robv.android.xposed.installer",
            "com.topjohnwu.lsposed.manager",
        )
    }
}
