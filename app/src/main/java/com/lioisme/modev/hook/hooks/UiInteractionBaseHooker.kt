package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.core.model.UiInteractionRecord
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.ProcessIdentity
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink

/**
 * UI 交互 Hooker 基类:统一开关判断(master + kind 双门控)、栈 denylist、
 * 会话节流与记录组装;子类拦截逻辑均在 engine PROTECTIVE 模式下执行,异常不影响宿主。
 *
 * V34 审计后:emit 记录事件到 [RecordSink],不直接执行干预。
 * 干预行为由子类在 before/after 中通过 [Invocation.setResult] 实现,
 * 干预结果通过 [interventionAction] 参数传递给记录("BLOCKED"|"DETECTED"|null)。
 */
abstract class UiInteractionBaseHooker(
    context: ProbeContext,
) {
    protected val engine = context.engine
    protected val config = context.config
    protected val sink: RecordSink = context.sink
    protected val packageName = context.packageName

    private val throttle = SessionThrottle()

    /**
     * 目标进程名:经 [com.lioisme.modev.hook.ProcessIdentity] 单例读取——
     * 旧形态每个 Hooker 基类各自 `by lazy` 读一次 `/proc/self/cmdline`,冷启动期
     * 约 50 个实例 = 50 次文件读 + 字节解码,而进程身份在进程内恒定不变。
     */
    protected val processName: String get() = ProcessIdentity.processName

    abstract fun install()

    /**
     * 干预闸门(V37 T0.1 FeatureGate 红线):干预动作本身必须经 master ∧ kind 双门控,
     * 禁止裸布尔位/裸数据列表直读——master=false 或 kind=false 时 setResult 等干预一律不得发生。
     */
    protected fun featureOn(kind: UiInteractionKind): Boolean = config().uiInteractionEnabled(kind)

    protected fun emit(
        kind: UiInteractionKind,
        operation: UiInteractionOperation,
        dialogTitle: String? = null,
        dialogMessage: String? = null,
        dialogKeywords: List<String>? = null,
        dialogIntercepted: Boolean? = null,
        interventionAction: String? = null,
        widgetText: String? = null,
        widgetClassName: String? = null,
        activityName: String? = null,
        intentAction: String? = null,
        intentData: String? = null,
        intentExtrasJson: String? = null,
        intentFlags: Int? = null,
        params: Map<String, String>? = null,
    ) {
        val switch = config()
        if (!switch.uiInteractionEnabled(kind)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val throttleKey = "$kind|$operation|${dialogTitle ?: widgetText ?: activityName ?: ""}"
        if (throttle.shouldSkip(throttleKey)) return
        val record = UiInteractionRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            dialogTitle = dialogTitle,
            dialogMessage = dialogMessage,
            dialogKeywords = dialogKeywords,
            dialogIntercepted = dialogIntercepted,
            interventionAction = interventionAction,
            widgetText = widgetText,
            widgetClassName = widgetClassName,
            activityName = activityName,
            intentAction = intentAction,
            intentData = intentData,
            intentExtrasJson = intentExtrasJson,
            intentFlags = intentFlags,
            params = params,
            processName = processName,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }
}
