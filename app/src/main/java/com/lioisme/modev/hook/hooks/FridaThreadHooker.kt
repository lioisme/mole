package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext

/**
 * Frida 线程名清理 Hooker:
 * 拦截 Thread.getName / Thread.currentThread.getName,
 * 剥离 Frida gadget/stalker 注入的线程名特征。
 *
 * momo 检测项覆盖:
 * - Frida 线程名检测 → 线程名含 "gadget"/"frida"/"stalker" 等关键词
 *
 * 设计:仅在 procStatus 启用时激活(共享 Frida 检测维度)。
 */
class FridaThreadHooker(
    context: ProbeContext,
) : DeviceEnvBaseHooker(context) {

    private val inHook = ThreadLocal.withInitial { false }

    override fun install() {
        hookThreadGetName()
    }

    private fun hookThreadGetName() {
        val method = runCatching {
            Thread::class.java.getMethod("getName")
        }.getOrNull() ?: return

        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (!featureOn(DeviceEnvKind.PROC_STATUS)) return
                if (inHook.get() == true) return
                inHook.set(true)
                try {
                    val name = invocation.result as? String ?: return
                    val lower = name.lowercase()
                    val matched = FRIDA_THREAD_KEYWORDS.firstOrNull { lower.contains(it) } ?: return
                    val sanitized = name.replace(Regex("(?i)($FRIDA_THREAD_KEYWORDS_JOIN)"), "")
                        .trim()
                        .ifEmpty { "Thread-${System.currentTimeMillis()}" }
                    if (sanitized == name) return
                    invocation.setResult(sanitized)
                    emit(
                        kind = DeviceEnvKind.PROC_STATUS,
                        operation = DeviceEnvOperation.PROC_STATUS_READ,
                        originalValue = name,
                        maskedValue = sanitized,
                        detectionPattern = "frida-thread-$matched",
                        interventionAction = ACTION_BLOCKED,
                    )
                } finally {
                    inHook.remove()
                }
            }
        })
    }

    companion object {
        private const val ACTION_BLOCKED = "BLOCKED"

        private val FRIDA_THREAD_KEYWORDS = listOf(
            "gadget", "frida", "stalker",
            "gmain", "gdb", "pool-spawner",
        )

        private val FRIDA_THREAD_KEYWORDS_JOIN = FRIDA_THREAD_KEYWORDS.joinToString("|")
    }
}
