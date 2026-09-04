package com.lioisme.modev.hook.hooks

import android.annotation.SuppressLint
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 录屏限制解除 Hooker(V36 D-11 重写):入参改写式干预 FLAG_SECURE,
 * 阻止应用通过任何途径设置安全窗口标志。
 *
 * 覆盖路径:
 * - Window.setFlags(flags, mask):双入参清除 FLAG_SECURE 位,原方法以净化参数执行
 * - Window.addFlags(flags):入参清除 FLAG_SECURE 位
 * - Window.clearFlags(flags):若 FLAG_SECURE 已被本模块清除,抑制冗余清除调用
 *
 * 干预机制(D-11 根因):此前版本在 before() 反射清 mFlags,但原方法随即
 * `mFlags |= flags` 把位设回 → 绕过无效;且 getDeclaredField 在 PhoneWindow 上
 * 找不到声明于父类 Window 的 mFlags。现改为改写实参,原方法正常执行但位已被剔除。
 */
class ScreenRecordHooker(
    context: ProbeContext,
) : ProcessControlBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("android.view.Window")

    override fun install() {
        hookSetFlags()
        hookAddFlags()
        hookClearFlags()
    }

    /**
     * hook Window.setFlags(int, int):从 flags 与 mask 双入参清除 FLAG_SECURE 位。
     */
    private fun hookSetFlags() {
        val methods = registry?.named("setFlags").orEmpty()
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(ProcessKind.SCREEN_RECORD)) return
                    val flags = (invocation.getArg(0) as? Number)?.toInt() ?: return
                    if (flags and FLAG_SECURE == 0) return
                    val mask = (invocation.getArg(1) as? Number)?.toInt() ?: return
                    invocation.setArg(0, flags and FLAG_SECURE.inv())
                    invocation.setArg(1, mask and FLAG_SECURE.inv())
                    emit(
                        kind = ProcessKind.SCREEN_RECORD,
                        operation = ProcessOperation.FLAG_SECURE,
                        interventionAction = ACTION_BLOCKED,
                        params = mapOf(
                            "windowClass" to (invocation.thisObject?.javaClass?.name ?: ""),
                            "originalFlags" to flags.toString(),
                            "path" to "setFlags",
                        ),
                    )
                }
            })
        }
    }

    /**
     * hook Window.addFlags(int):入参清除 FLAG_SECURE 位后放行原方法。
     */
    private fun hookAddFlags() {
        val methods = registry?.named("addFlags").orEmpty()
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(ProcessKind.SCREEN_RECORD)) return
                    val flags = (invocation.getArg(0) as? Number)?.toInt() ?: return
                    if (flags and FLAG_SECURE == 0) return
                    invocation.setArg(0, flags and FLAG_SECURE.inv())
                    emit(
                        kind = ProcessKind.SCREEN_RECORD,
                        operation = ProcessOperation.ADD_FLAGS,
                        interventionAction = ACTION_BLOCKED,
                        params = mapOf(
                            "windowClass" to (invocation.thisObject?.javaClass?.name ?: ""),
                            "originalFlags" to flags.toString(),
                            "path" to "addFlags",
                        ),
                    )
                }
            })
        }
    }

    /**
     * hook Window.clearFlags(int):若窗口已无 FLAG_SECURE(被本模块入参改写拦截),
     * 抑制应用的冗余清除调用(setResult(null) 短路,原方法不执行)。
     */
    private fun hookClearFlags() {
        val methods = registry?.named("clearFlags").orEmpty()
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(ProcessKind.SCREEN_RECORD)) return
                    val flags = (invocation.getArg(0) as? Number)?.toInt() ?: return
                    if (flags and FLAG_SECURE == 0) return
                    val window = invocation.thisObject ?: return
                    val currentFlags = readFlags(window) ?: return
                    if (currentFlags and FLAG_SECURE == 0) {
                        invocation.setResult(null)
                        emit(
                            kind = ProcessKind.SCREEN_RECORD,
                            operation = ProcessOperation.FLAG_SECURE,
                            interventionAction = ACTION_BLOCKED,
                            params = mapOf(
                                "windowClass" to window.javaClass.name,
                                "path" to "clearFlags_suppressed",
                            ),
                        )
                    }
                }
            })
        }
    }

    /**
     * D-11:mFlags 声明于 android.view.Window(protected),PhoneWindow 等子类的
     * getDeclaredField 找不到 → 沿父类链查找。
     */
    @SuppressLint("SoonBlockedPrivateApi")
    private fun findFlagsField(window: Any): java.lang.reflect.Field? {
        var c: Class<*>? = window.javaClass
        while (c != null) {
            val f = runCatching { c.getDeclaredField("mFlags") }.getOrNull()
            if (f != null) {
                f.isAccessible = true
                return f
            }
            c = c.superclass
        }
        return null
    }

    private fun readFlags(window: Any): Int? =
        runCatching { findFlagsField(window)?.getInt(window) }.getOrNull()

    companion object {
        /** WindowManager.LayoutParams.FLAG_SECURE */
        private const val FLAG_SECURE = 0x2000

        /** interventionAction:已阻止 FLAG_SECURE 设置 */
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
