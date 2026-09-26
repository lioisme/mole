package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * 退出拦截 Hooker(V33,V34 审计修复):hook System.exit(int) 和 Process.killProcess(int),
 * 在 before 中调用 setResult(null) 阻止进程退出,记录退出码/pid。
 *
 * 干预机制:before 中 setResult(null) 阻止原方法执行,进程不会退出。
 * 干预结果通过 [ProcessRecord.interventionAction] 记录("BLOCKED"|"DETECTED"|null)。
 *
 * 安全注意:此功能会阻止应用正常退出,可能导致应用状态不一致。
 * 仅在用户显式开启时生效(默认关)。
 */
class ExitInterceptHooker(
    context: ProbeContext,
) : ProcessControlBaseHooker(context) {

    private val systemRegistry = BehaviorMethodRegistry.forName("java.lang.System")
    private val processRegistry = BehaviorMethodRegistry.forName("android.os.Process")
    private val runtimeRegistry = BehaviorMethodRegistry.forName("java.lang.Runtime")

    override fun install() {
        hookSystemExit()
        hookRuntimeExit()
        hookKillProcess()
    }

    /** V37 T0.5:直调 Runtime.exit(int) 的退出路径同样拦截(此前漏装)。 */
    private fun hookRuntimeExit() {
        val methods = runtimeRegistry?.named("exit").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(ProcessKind.EXIT_INTERCEPT)) return
                    val code = (invocation.getArg(0) as? Number)?.toInt() ?: -1
                    emitUrgent(
                        kind = ProcessKind.EXIT_INTERCEPT,
                        operation = ProcessOperation.RUNTIME_EXIT,
                        exitCode = code,
                    )
                    invocation.setResult(null)
                }
            })
        }
    }

    private fun hookSystemExit() {
        val methods = systemRegistry?.named("exit").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(ProcessKind.EXIT_INTERCEPT)) return
                    val code = (invocation.getArg(0) as? Number)?.toInt() ?: -1
                    emitUrgent(
                        kind = ProcessKind.EXIT_INTERCEPT,
                        operation = ProcessOperation.SYSTEM_EXIT,
                        exitCode = code,
                    )
                    invocation.setResult(null)
                }
            })
        }
    }

    private fun hookKillProcess() {
        val methods = processRegistry?.named("killProcess").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(ProcessKind.EXIT_INTERCEPT)) return
                    val pid = (invocation.getArg(0) as? Number)?.toInt() ?: -1
                    emitUrgent(
                        kind = ProcessKind.EXIT_INTERCEPT,
                        operation = ProcessOperation.KILL_PROCESS,
                        processPid = pid,
                    )
                    invocation.setResult(null)
                }
            })
        }
    }
}
