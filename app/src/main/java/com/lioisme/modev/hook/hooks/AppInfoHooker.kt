package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.SharedMethodBroadcast

/**
 * 应用信息 Hooker(V33):hook PackageManager.getPackageInfo(String, int),
 * 拦截 GET_SIGNATURES/GET_SIGNING_CERTIFICATES 标记的调用并记录版本号。
 */
class AppInfoHooker(
    context: ProbeContext,
) : SystemBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.app.ApplicationPackageManager")

    /**
     * 字段句柄按类缓存(V44 T2.5):旧形态每次 getPackageInfo 做 4~6 次
     * `getField` + 异常构造(getPackageInfo 是应用高频 API)。
     * [com.lioisme.modev.hook.ClassValueCache] 语义 = 每类一份缓存、随类卸载回收,无泄漏;
     * 类内 map 同步包装,允许 null 值(字段缺失同样缓存,避免反复抛 NoSuchFieldException)。
     */
    private val fieldCache = com.lioisme.modev.hook.ClassValueCache {
        java.util.Collections.synchronizedMap(HashMap<String, java.lang.reflect.Field?>())
    }

    /** 取 [target] 的公开字段句柄(按类缓存;缺失也缓存为 null)。 */
    private fun fieldOf(target: Any?, name: String): java.lang.reflect.Field? {
        val t = target ?: return null
        val perClass = fieldCache.get(t.javaClass)
        synchronized(perClass) {
            if (perClass.containsKey(name)) return perClass[name]
            val resolved = runCatching { t.javaClass.getField(name) }.getOrNull()
            perClass[name] = resolved
            return resolved
        }
    }

    override fun install() {
        // Android 隐藏类名可能变化,尝试多种路径
        val pmRegistry = registry
            ?: BehaviorMethodRegistry.forName("android.app.PackageManagerImpl")
            ?: return
        val methods = pmRegistry.named("getPackageInfo")
        if (methods.isEmpty()) return
        // V43 T3/D-4:getPackageInfo 与 RootPackageManagerHooker(干预)共享单一广播注册点;
        // 命中 root 包短路抛 NameNotFoundException 后原方法不执行,本观测 after 自然跳过
        for (method in methods) {
            broadcast.subscribe(
                method,
                object : SharedMethodBroadcast.Sub {
                    override fun after(invocation: Invocation, result: Any?) {
                    // V43 T2:四次 getField 反射 + Throwable 构造先于任何门控——APP_INFO
                    // kind 关闭必须首行短路(V2.8)
                    if (!config().systemEnabled(SystemKind.APP_INFO)) return
                    val pkgName = invocation.getArg(0) as? String ?: return
                    val flags = invocation.getArg(1) as? Int ?: return
                    val info = invocation.result ?: return
                    // V44 T2.5:字段句柄按类缓存,不再每次调用 getField + 异常构造
                    val versionName = fieldOf(info, "versionName")?.let { f ->
                        runCatching { f.get(info) as? String }.getOrNull()
                    }
                    val versionCode = fieldOf(info, "versionCode")?.let { f ->
                        runCatching { f.get(info) as? Long }.getOrNull()
                    }?.toInt()
                    // V43 T2 修复:旧实现链式取 applicationInfo 后在错误对象上再 get
                    // (minSdkVersion/targetSdkVersion 挂在 applicationInfo 实例上,却把
                    // PackageInfo 传入)→ 取值恒 null 且白白构造 Throwable
                    val appInfo = fieldOf(info, "applicationInfo")?.let { f ->
                        runCatching { f.get(info) }.getOrNull()
                    }
                    val minSdk = fieldOf(appInfo, "minSdkVersion")?.let { f ->
                        runCatching { f.get(appInfo) as? Int }.getOrNull()
                    }
                    val targetSdk = fieldOf(appInfo, "targetSdkVersion")?.let { f ->
                        runCatching { f.get(appInfo) as? Int }.getOrNull()
                    }
                    emit(
                        kind = SystemKind.APP_INFO,
                        operation = SystemOperation.APP_VERSION,
                        appVersion = versionName,
                        appVersionCode = versionCode,
                        appMinSdk = minSdk,
                        appTargetSdk = targetSdk,
                        params = mapOf(
                            "targetPackage" to pkgName,
                            "flags" to flags.toString(),
                        ),
                    )
                }
                },
            )
        }
    }
}
