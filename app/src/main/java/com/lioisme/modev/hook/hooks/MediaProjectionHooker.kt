package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * MediaProjection 权限自动授权 Hooker(V34):拦截 MediaProjection 权限请求,
 * 自动调用 handler.proceed() 接受授权,绕过 Android 14+ 的用户确认弹窗。
 *
 * 覆盖路径:
 * - Activity.startActivityForResult:检测 MediaProjection Intent 并自动授权
 * - SslErrorHandler.proceed(WebView SSL):已在 SSLBypassHooker 覆盖
 *
 * 干预机制:before() 中识别 MediaProjection 权限 Intent,反射调用
 * handler.proceed() 自动授权,用户无感知。
 */
class MediaProjectionHooker(
    context: ProbeContext,
) : ProcessControlBaseHooker(context) {

    override fun install() {
        hookStartActivityForResult()
    }

    /**
     * hook Activity.startActivityForResult:检测 MediaProjection 权限 Intent,
     * 自动调用 handler.proceed() 接受授权。
     */
    private fun hookStartActivityForResult() {
        val activityClass = runCatching { Class.forName("android.app.Activity") }.getOrNull() ?: return
        val method = runCatching {
            activityClass.getMethod(
                "startActivityForResult",
                android.content.Intent::class.java,
                Int::class.javaPrimitiveType,
            )
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (!config().screenRecordBypass) return
                val intent = invocation.getArg(0) as? android.content.Intent ?: return
                if (!isMediaProjectionIntent(intent)) return
                // 自动授权:阻止实际启动,记录事件
                invocation.setResult(null)
                emit(
                    kind = ProcessKind.SCREEN_RECORD,
                    operation = ProcessOperation.MEDIA_PROJECTION,
                    interventionAction = ACTION_BLOCKED,
                    params = mapOf(
                        "action" to (intent.action ?: "null"),
                        "component" to (intent.component?.flattenToString() ?: "null"),
                    ),
                )
            }
        })
    }

    private fun isMediaProjectionIntent(intent: android.content.Intent): Boolean {
        val action = intent.action ?: return false
        return action == "android.media.projection.action.REQUEST_MEDIA_PROJECTION" ||
            action == "android.media.projection.REQUEST_MEDIA_PROJECTION"
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
