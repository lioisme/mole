package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.core.model.ProcessRecord
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.ProcessIdentity
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink

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

    /**
     * 目标进程名(V44 T2.6):经 [com.lioisme.modev.hook.ProcessIdentity] 单例读取——
     * 旧形态每个 Hooker 基类各自 `by lazy` 读一次 `/proc/self/cmdline`,冷启动期
     * 约 50 个实例 = 50 次文件读 + 字节解码,而进程身份在进程内恒定不变。
     */
    protected val processName: String get() = ProcessIdentity.processName

    abstract fun install()

    /**
     * 干预闸门(V37 T0.1 FeatureGate 红线):干预动作本身必须经 master ∧ kind 双门控,
     * 禁止裸布尔位直读——master=false 或 kind=false 时 setResult 等干预一律不得发生。
     */
    protected fun featureOn(kind: ProcessKind): Boolean = config().processControlEnabled(kind)

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
