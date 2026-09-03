package com.lioisme.modev.hook.hooks

import android.annotation.SuppressLint
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 录屏限制解除 Hooker(V34 增强):多路径干预 FLAG_SECURE 设置,
 * 阻止应用通过任何途径设置安全窗口标志。
 *
 * 覆盖路径:
 * - Window.setFlags(flags, mask):清除 mask 中的 FLAG_SECURE 位
 * - Window.addFlags(flags):清除 flags 中的 FLAG_SECURE 位
 * - Window.clearFlags(flags):若含 FLAG_SECURE 则阻止清除(保留清除效果)
 *
 * 干预机制:在 before() 中通过反射清除 Window.mFlags 中的 FLAG_SECURE(0x2000) 位,
 * 原方法正常执行但 FLAG_SECURE 位已被清除。
 *
 * 设计决策:不使用 setResult(null) 阻止 setFlags/addFlags,因为这些方法可能携带其他
 * 正常标志位(如 FLAG_LAYOUT_IN_SCREEN),阻止会导致窗口布局异常。
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
     * hook Window.setFlags(int, int):原始路径,清除 mask 中的 FLAG_SECURE 位。
     */
    private fun hookSetFlags() {
        val methods = registry?.named("setFlags").orEmpty()
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().screenRecordBypass) return
                    val flags = (invocation.getArg(0) as? Number)?.toInt() ?: return
                    if (flags and FLAG_SECURE == 0) return
                    val window = invocation.thisObject ?: return
                    if (clearFlagSecure(window)) {
                        emit(
                            kind = ProcessKind.SCREEN_RECORD,
                            operation = ProcessOperation.FLAG_SECURE,
                            interventionAction = ACTION_BLOCKED,
                            params = mapOf(
                                "windowClass" to window.javaClass.name,
                                "originalFlags" to flags.toString(),
                                "path" to "setFlags",
                            ),
                        )
                    }
                }
            })
        }
    }

    /**
     * hook Window.addFlags(int):拦截通过 addFlags 设置 FLAG_SECURE 的路径。
     */
    private fun hookAddFlags() {
        val methods = registry?.named("addFlags").orEmpty()
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().screenRecordBypass) return
                    val flags = (invocation.getArg(0) as? Number)?.toInt() ?: return
                    if (flags and FLAG_SECURE == 0) return
                    val window = invocation.thisObject ?: return
                    if (clearFlagSecure(window)) {
                        emit(
                            kind = ProcessKind.SCREEN_RECORD,
                            operation = ProcessOperation.ADD_FLAGS,
                            interventionAction = ACTION_BLOCKED,
                            params = mapOf(
                                "windowClass" to window.javaClass.name,
                                "originalFlags" to flags.toString(),
                                "path" to "addFlags",
                            ),
                        )
                    }
                }
            })
        }
    }

    /**
     * hook Window.clearFlags(int):阻止应用通过 clearFlags 移除我们已清除的 FLAG_SECURE。
     * 如果应用调用 clearFlags(FLAG_SECURE),我们阻止该操作(因为 FLAG_SECURE 已被清除)。
     */
    private fun hookClearFlags() {
        val methods = registry?.named("clearFlags").orEmpty()
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!config().screenRecordBypass) return
                    val flags = (invocation.getArg(0) as? Number)?.toInt() ?: return
                    if (flags and FLAG_SECURE == 0) return
                    val window = invocation.thisObject ?: return
                    // 如果 Window 已经没有 FLAG_SECURE,阻止 clearFlags(避免重复操作)
                    val currentFlags = runCatching {
                        val field = window.javaClass.getDeclaredField("mFlags")
                        field.isAccessible = true
                        field.getInt(window)
                    }.getOrDefault(0)
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
     * 通过反射清除 Window.mFlags 中的 FLAG_SECURE 位。
     * 返回 true 表示成功清除。
     */
    @SuppressLint("SoonBlockedPrivateApi")
    private fun clearFlagSecure(window: Any): Boolean = runCatching {
        val flagsField = window.javaClass.getDeclaredField("mFlags")
        flagsField.isAccessible = true
        val currentFlags = flagsField.getInt(window)
        if (currentFlags and FLAG_SECURE != 0) {
            flagsField.setInt(window, currentFlags and FLAG_SECURE.inv())
            true
        } else {
            false
        }
    }.getOrDefault(false)

    companion object {
        /** WindowManager.LayoutParams.FLAG_SECURE */
        private const val FLAG_SECURE = 0x2000

        /** interventionAction:已阻止 FLAG_SECURE 设置 */
        private const val ACTION_BLOCKED = "BLOCKED"
    }
}
