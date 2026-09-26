package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Activity 转场与 Intent 录制 Hooker(V33):hook Activity.startActivity(Intent)
 * 和 Activity.onCreate(Bundle?),捕获 intent action/data/flags/extras 与目标 Activity 类名。
 */
class ActivityTransitionHooker(
    context: ProbeContext,
) : UiInteractionBaseHooker(context) {

    private val activityRegistry = BehaviorMethodRegistry.forName("android.app.Activity")

    override fun install() {
        hookStartActivity()
        hookLifecycle()
    }

    private fun hookStartActivity() {
        val methods = activityRegistry?.named("startActivity").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    // V43 T2:反射查找/resolveActivity Binder IPC/Bundle 全量反序列化均为
                    // 主线程重成本,必须门控先行(V2.3);emit 内部门控太晚,不作为首行保护
                    if (!featureOn(UiInteractionKind.ACTIVITY_START)) return
                    val intent = invocation.getArg(0) as? android.content.Intent ?: return
                    val callerActivity = invocation.thisObject
                    val callerName = callerActivity?.javaClass?.name
                    val targetResolve = runCatching {
                        callerActivity?.javaClass?.getMethod("getPackageManager")
                            ?.invoke(callerActivity)?.let { pm ->
                                intent.resolveActivity(pm as android.content.pm.PackageManager)
                            }?.className
                    }.getOrNull()
                    val extrasJson = runCatching {
                        intent.extras?.let { bundle ->
                            org.json.JSONObject().apply {
                                for (key in bundle.keySet()) {
                                    put(key, bundle.get(key)?.toString() ?: "null")
                                }
                            }.toString()
                        }
                    }.getOrNull()
                    emit(
                        kind = UiInteractionKind.ACTIVITY_START,
                        // V50 孤儿整改:循环含 `startActivity(Intent, Bundle)` 重载,
                        // 但发射时不区分实参数 ⇒ ACTIVITY_START_OPTIONS 恒零发射。
                        operation = if (invocation.argCount >= 2) {
                            UiInteractionOperation.ACTIVITY_START_OPTIONS
                        } else {
                            UiInteractionOperation.ACTIVITY_START_INTENT
                        },
                        activityName = targetResolve ?: intent.component?.getClassName(),
                        intentAction = intent.action,
                        intentData = intent.dataString,
                        intentExtrasJson = extrasJson,
                        intentFlags = intent.flags,
                        params = mapOf(
                            "callerActivity" to (callerName ?: "unknown"),
                            "component" to (intent.component?.flattenToString() ?: "implicit"),
                        ),
                    )
                }
            })
        }
    }

    /**
     * Activity 生命周期观测(V50 孤儿整改):原 `hookOnCreate` 只挂 onCreate 却以
     * `ACTIVITY_RESUME`(kind 名 = 「Activity 恢复」)发射 ⇒ kind 名实相反,且枚举里
     * `ACTIVITY_ON_RESUME`/`ON_PAUSE`/`ON_STOP`/`ON_DESTROY`/`FINISH` 恒零发射
     * (四语标签与筛选菜单都有,选中即恒空)。`BehaviorMethodRegistry` 枚举
     * `methods ∪ 父类链 declaredMethods`(V36 D-13),故 protected 生命周期可得。
     */
    private fun hookLifecycle() {
        val targets = mapOf(
            "onCreate" to UiInteractionOperation.ACTIVITY_ON_CREATE,
            "onResume" to UiInteractionOperation.ACTIVITY_ON_RESUME,
            "onPause" to UiInteractionOperation.ACTIVITY_ON_PAUSE,
            "onStop" to UiInteractionOperation.ACTIVITY_ON_STOP,
            "onDestroy" to UiInteractionOperation.ACTIVITY_ON_DESTROY,
            "finish" to UiInteractionOperation.ACTIVITY_FINISH,
        )
        for ((method, operation) in targets) {
            val methods = activityRegistry?.named(method).orEmpty()
            if (methods.isEmpty()) continue
            for (m in methods) {
                engine.hook(m, object : MethodHook() {
                    override fun after(invocation: Invocation) {
                        if (!featureOn(UiInteractionKind.ACTIVITY_RESUME)) return // V43 T2
                        val activity = invocation.thisObject ?: return
                        emit(
                            kind = UiInteractionKind.ACTIVITY_RESUME,
                            operation = operation,
                            activityName = activity.javaClass.name,
                        )
                    }
                })
            }
        }
    }
}
