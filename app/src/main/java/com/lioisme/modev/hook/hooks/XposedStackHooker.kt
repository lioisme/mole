package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

class XposedStackHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    override fun install() {
        hookThrowableGetStackTrace()
        hookThreadGetStackTrace()
    }

    private fun hookThrowableGetStackTrace() {
        val method = runCatching {
            Throwable::class.java.getMethod("getStackTrace")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().xposedStack) return
                if (IN_HOOK.get() == true) return
                IN_HOOK.set(true)
                try {
                    val original = invocation.result as? Array<StackTraceElement> ?: return
                    val filtered = original.filter { !isXposedFrame(it) }
                    if (filtered.size != original.size) {
                        invocation.setResult(filtered.toTypedArray())
                        emit(
                            kind = DeviceEnvKind.XPOSED_STACK,
                            operation = DeviceEnvOperation.GET_STACK_TRACE,
                            params = mapOf(
                                "removed" to (original.size - filtered.size).toString(),
                            ),
                        )
                    }
                } finally {
                    IN_HOOK.set(false)
                }
            }
        })
    }

    private fun hookThreadGetStackTrace() {
        val method = runCatching {
            Thread::class.java.getMethod("getStackTrace")
        }.getOrNull() ?: return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!config().xposedStack) return
                if (IN_HOOK.get() == true) return
                IN_HOOK.set(true)
                try {
                    val original = invocation.result as? Array<StackTraceElement> ?: return
                    val filtered = original.filter { !isXposedFrame(it) }
                    if (filtered.size != original.size) {
                        invocation.setResult(filtered.toTypedArray())
                        emit(
                            kind = DeviceEnvKind.XPOSED_STACK,
                            operation = DeviceEnvOperation.THREAD_STACK,
                            params = mapOf(
                                "removed" to (original.size - filtered.size).toString(),
                            ),
                        )
                    }
                } finally {
                    IN_HOOK.set(false)
                }
            }
        })
    }

    private fun isXposedFrame(element: StackTraceElement): Boolean {
        val className = element.className.lowercase()
        return XPOSED_KEYWORDS.any { className.contains(it) }
    }

    companion object {
        private val IN_HOOK = ThreadLocal.withInitial { false }

        private val XPOSED_KEYWORDS = listOf(
            "xposed", "lspd", "lsplant", "de.robv",
        )
    }
}
