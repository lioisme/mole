package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.io.File

/**
 * 进程控制 Hooker 基类(V33):统一开关判断(master + kind 双门控)、栈 denylist、
 * 会话节流与记录组装;子类拦截逻辑均在 engine PROTECTIVE 模式下执行,异常不影响宿主。
 *
 * V34 审计后:emit/emitUrgent 记录事件到 [RecordSink],不直接执行干预。
 * 干预行为由子类在 before()/after() 中通过 [Invocation.setResult] 实现,
 * 干预结果通过 [interventionAction] 参数传递给记录("BLOCKED"|"DETECTED"|null)。
 */
abstract class ProcessControlBaseHooker(
    context: ProbeContext,
) {
    protected val engine = context.engine
    protected val config = context.config
    protected val sink: RecordSink = context.sink
    protected val packageName = context.packageName

    private val throttle = SessionThrottle()

    protected val processName: String by lazy {
        runCatching { File("/proc/self/cmdline").readBytes() }
            .getOrNull()
            ?.toString(Charsets.UTF_8)
            ?.substringBefore('\u0000')
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }

    abstract fun install()

    protected fun emit(
        kind: ProcessKind,
        operation: ProcessOperation,
        exitCode: Int? = null,
        processPid: Int? = null,
        crashType: String? = null,
        crashMessage: String? = null,
        crashBlocked: Boolean? = null,
        screenRecordBlocked: Boolean? = null,
        interventionAction: String? = null,
        params: Map<String, String>? = null,
    ) {
        val switch = config()
        if (!switch.processControlEnabled(kind)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val throttleKey = "$kind|$operation|${exitCode ?: crashType ?: ""}"
        if (throttle.shouldSkip(throttleKey)) return
        val record = ProcessRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            exitCode = exitCode,
            processPid = processPid,
            crashType = crashType,
            crashMessage = crashMessage,
            crashBlocked = crashBlocked,
            screenRecordBlocked = screenRecordBlocked,
            interventionAction = interventionAction,
            params = params,
            processName = processName,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }

    protected fun emitUrgent(
        kind: ProcessKind,
        operation: ProcessOperation,
        exitCode: Int? = null,
        processPid: Int? = null,
    ) {
        val switch = config()
        if (!switch.processControlEnabled(kind)) return
        val record = ProcessRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            exitCode = exitCode,
            processPid = processPid,
            processName = processName,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
        sink.emitUrgent(record)
    }
}
