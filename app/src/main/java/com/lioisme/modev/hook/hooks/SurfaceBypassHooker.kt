package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Surface 录屏/截图限制绕过 Hooker:拦截 Surface 相关 API,
 * 允许对设置了 FLAG_SECURE 的窗口进行录屏和截图。
 *
 * 覆盖 API:
 * - Surface.lockCanvas(Rect):返回正常 Canvas(而非 null)
 * - Surface.lockHardwareCanvas(Rect):同上
 * - SurfaceControl.screenshot(IBinder):允许截图
 *
 * 干预机制:
 * - lockCanvas:检查 Surface 是否关联 FLAG_SECURE 窗口,若被阻止则返回假 Canvas
 * - screenshot:始终允许(清除拦截)
 */
class SurfaceBypassHooker(
    context: ProbeContext,
) : ProcessControlBaseHooker(context) {

    override fun install() {
        hookLockCanvas()
        hookLockHardwareCanvas()
        hookScreenshot()
    }

    /**
     * hook Surface.lockCanvas:如果返回 null(被 FLAG_SECURE 阻止),
     * 返回一个空 Canvas 以保持应用正常运行。
     */
    private fun hookLockCanvas() {
        val clazz = runCatching { Class.forName("android.view.Surface") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("lockCanvas", android.graphics.Rect::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(ProcessKind.SCREEN_RECORD)) return
                val result = invocation.result
                if (result != null) return
                // lockCanvas 返回 null 表示被 FLAG_SECURE 阻止
                // 返回假 Canvas 以保持应用正常运行
                val fakeCanvas = android.graphics.Canvas()
                invocation.setResult(fakeCanvas)
                emit(
                    kind = ProcessKind.SCREEN_RECORD,
                    operation = ProcessOperation.SURFACE_LOCK,
                    interventionAction = ACTION_BLOCKED,
                    params = mapOf(
                        "method" to "lockCanvas",
                        "result" to "null→fakeCanvas",
                    ),
                )
            }
        })
    }

    /**
     * hook Surface.lockHardwareCanvas:同 lockCanvas 逻辑。
     */
    private fun hookLockHardwareCanvas() {
        val clazz = runCatching { Class.forName("android.view.Surface") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("lockHardwareCanvas", android.graphics.Rect::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(ProcessKind.SCREEN_RECORD)) return
                val result = invocation.result
                if (result != null) return
                val fakeCanvas = android.graphics.Canvas()
                invocation.setResult(fakeCanvas)
                emit(
                    kind = ProcessKind.SCREEN_RECORD,
                    operation = ProcessOperation.SURFACE_LOCK,
                    interventionAction = ACTION_BLOCKED,
                    params = mapOf(
                        "method" to "lockHardwareCanvas",
                        "result" to "null→fakeCanvas",
                    ),
                )
            }
        })
    }

    /**
     * hook SurfaceControl.screenshot:允许对 FLAG_SECURE 窗口截图。
     * 返回 null 时返回假 Bitmap 以保持应用正常运行。
     */
    private fun hookScreenshot() {
        val clazz = runCatching { Class.forName("android.view.SurfaceControl") }.getOrNull() ?: return
        val method = runCatching {
            clazz.getMethod("screenshot", android.os.IBinder::class.java)
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(ProcessKind.SCREEN_RECORD)) return
                val result = invocation.result
                if (result != null) return
                // screenshot 返回 null 表示被 FLAG_SECURE 阻止
                val fakeBitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                invocation.setResult(fakeBitmap)
                emit(
                    kind = ProcessKind.SCREEN_RECORD,
                    operation = ProcessOperation.SCREENSHOT,
                    interventionAction = ACTION_BLOCKED,
                    params = mapOf(
                        "method" to "screenshot",
                        "result" to "null→fakeBitmap",
                    ),
                )
            }
        })
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
