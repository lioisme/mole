package com.lioisme.modev.hook.hooks

import android.annotation.SuppressLint
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.lang.Thread as JavaThread

/**
 * 崩溃预防 Hooker(V33,V34 审计修复):双通道拦截——
 *   A) hook Thread.setDefaultUncaughtExceptionHandler(设置器注册通道);
 *   B) hook Thread.dispatchUncaughtException(实际派发通道,兜底未注册 handler 场景)。
 *
 * 通道 A:捕获应用注册的 handler,注入 wrapper 包装器记录崩溃后转发给原始 handler。
 * 通道 B:在实际派发时记录崩溃并吞掉异常(阻止应用崩溃)。
 * 干预结果通过 [ProcessRecord.interventionAction] 记录("BLOCKED"|"DETECTED"|null)。
 *
 * 安全注意:此功能会阻止应用崩溃,可能导致应用处于不一致状态。
 * 仅在用户显式开启时生效(默认关)。
 */
class CrashPreventHooker(
    context: ProbeContext,
) : ProcessControlBaseHooker(context) {

    private val registry = BehaviorMethodRegistry.forName("java.lang.Thread")

    override fun install() {
        hookSetDefaultHandler()
        hookDispatch()
    }

    /** 通道 A:拦截 setDefaultUncaughtExceptionHandler,注入包装器。 */
    @SuppressLint("SoonBlockedPrivateApi")
    private fun hookSetDefaultHandler() {
        val methods = registry?.named("setDefaultUncaughtExceptionHandler").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    if (!featureOn(ProcessKind.CRASH_PREVENT)) return
                    val thread = invocation.thisObject as? JavaThread ?: return
                    val currentHandler = runCatching {
                        val field = JavaThread::class.java.getDeclaredField("uncaughtExceptionHandler")
                        field.isAccessible = true
                        field.get(thread) as? JavaThread.UncaughtExceptionHandler
                    }.getOrNull() ?: return
                    if (currentHandler is WrappedCrashHandler) return
                    val wrappedHandler = WrappedCrashHandler(currentHandler) { thread, throwable ->
                        recordCrash(thread, throwable)
                    }
                    runCatching {
                        val field = JavaThread::class.java.getDeclaredField("uncaughtExceptionHandler")
                        field.isAccessible = true
                        field.set(thread, wrappedHandler)
                    }
                }
            })
        }
    }

    /** 通道 B:拦截 Thread.dispatchUncaughtException,兜底未注册 handler 的崩溃。 */
    private fun hookDispatch() {
        val methods = registry?.named("dispatchUncaughtException").orEmpty()
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun before(invocation: Invocation) {
                    if (!featureOn(ProcessKind.CRASH_PREVENT)) return
                    val thread = invocation.thisObject as? JavaThread ?: return
                    val throwable = invocation.getArg(0) as? Throwable ?: return
                    recordCrash(thread, throwable)
                    invocation.setResult(null)
                }
            })
        }
    }

    private fun recordCrash(thread: JavaThread, throwable: Throwable) {
        val crashType = throwable.javaClass.name
        val crashMessage = throwable.message
        emit(
            kind = ProcessKind.CRASH_PREVENT,
            operation = ProcessOperation.UNCAUGHT_EXCEPTION,
            crashType = crashType,
            crashMessage = crashMessage?.take(config().maxBytesPerField),
            interventionAction = ACTION_BLOCKED,
            params = mapOf(
                "threadName" to thread.name,
                "isDaemon" to thread.isDaemon.toString(),
            ),
        )
    }

    companion object {
        /** interventionAction:已阻止崩溃传播 */
        private const val ACTION_BLOCKED = "BLOCKED"
    }

    /**
     * 包装 UncaughtExceptionHandler,在转发给原始 handler 之前记录崩溃。
     * 用于通道 A:包装应用自己注册的 handler。
     */
    private class WrappedCrashHandler(
        private val delegate: JavaThread.UncaughtExceptionHandler,
        private val onCrash: (JavaThread, Throwable) -> Unit,
    ) : JavaThread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: JavaThread, throwable: Throwable) {
            onCrash(thread, throwable)
            delegate.uncaughtException(thread, throwable)
        }
    }
}
