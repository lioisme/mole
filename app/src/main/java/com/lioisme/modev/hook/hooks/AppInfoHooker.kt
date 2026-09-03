package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 应用信息 Hooker(V33):hook PackageManager.getPackageInfo(String, int),
 * 拦截 GET_SIGNATURES/GET_SIGNING_CERTIFICATES 标记的调用并记录版本号。
 */
class AppInfoHooker(
    context: ProbeContext,
) : SystemBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.app.ApplicationPackageManager")

    override fun install() {
        // Android 隐藏类名可能变化,尝试多种路径
        val pmRegistry = registry
            ?: BehaviorMethodRegistry.forName("android.app.PackageManagerImpl")
            ?: return
        val methods = pmRegistry.named("getPackageInfo")
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    val pkgName = invocation.getArg(0) as? String ?: return
                    val flags = invocation.getArg(1) as? Int ?: return
                    val info = invocation.result ?: return
                    val versionName = runCatching {
                        info.javaClass.getField("versionName").get(info) as? String
                    }.getOrNull()
                    val versionCode = runCatching {
                        info.javaClass.getField("versionCode").get(info) as? Long
                    }.getOrNull()?.toInt()
                    val minSdk = runCatching {
                        info.javaClass.getField("applicationInfo")
                            .get(info)?.javaClass?.getField("minSdkVersion")?.get(info) as? Int
                    }.getOrNull()
                    val targetSdk = runCatching {
                        info.javaClass.getField("applicationInfo")
                            .get(info)?.javaClass?.getField("targetSdkVersion")?.get(info) as? Int
                    }.getOrNull()
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
            })
        }
    }
}
