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
        hookOnCreate()
    }

    private fun hookStartActivity() {
        val methods = activityRegistry?.named("startActivity").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
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
                        operation = UiInteractionOperation.ACTIVITY_START_INTENT,
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

    private fun hookOnCreate() {
        val methods = activityRegistry?.named("onCreate").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    val activity = invocation.thisObject ?: return
                    val activityName = activity.javaClass.name
                    emit(
                        kind = UiInteractionKind.ACTIVITY_RESUME,
                        operation = UiInteractionOperation.ACTIVITY_ON_CREATE,
                        activityName = activityName,
                    )
                }
            })
        }
    }
}
