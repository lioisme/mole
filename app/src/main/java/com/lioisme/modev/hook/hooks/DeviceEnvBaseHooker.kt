package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.DeviceEnvRecord
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import com.lioisme.modev.hook.ProcessIdentity

/**
 * 设备环境 Hooker 基类(V31):统一开关判断(deviceEnv 类别,master+kind 双门控)、
 * 栈 denylist 与记录组装;所有子类拦截逻辑均在 engine 的 PROTECTIVE 模式下执行,
 * 异常不影响宿主。
 *
 * 与 [NetworkBaseHooker]/[BehaviorBaseHooker]/[SqlBaseHooker] 同构。
 *
 * V34 审计后:emit 新增 [interventionAction] 参数("BLOCKED"|"DETECTED"|null),
 * 替代旧 [intervention] boolean 的模糊语义。
 */
abstract class DeviceEnvBaseHooker(
    context: ProbeContext,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val broadcast = context.broadcast // V43 T3/D-4:热门平台方法单一广播注册点
    protected val configWatcher = context.configWatcher
    protected val sink: RecordSink = context.sink
    protected val packageName = context.packageName

    /**
     * 目标进程名(V44 T2.6):经 [com.lioisme.modev.hook.ProcessIdentity] 单例读取——
     * 旧形态每个 Hooker 基类各自 `by lazy` 读一次 `/proc/self/cmdline`,冷启动期
     * 约 50 个实例 = 50 次文件读 + 字节解码,而进程身份在进程内恒定不变。
     */
    protected val processName: String get() = ProcessIdentity.processName

    abstract fun install()

    /**
     * 干预闸门(V37 T0.1 FeatureGate 红线):干预动作本身必须经 master ∧ kind 双门控,
     * 禁止裸布尔位直读——master=false 或 kind=false 时 setResult/字段改写一律不得发生。
     */
    protected fun featureOn(kind: DeviceEnvKind): Boolean = config().deviceEnvEnabled(kind)

    /**
     * 组装并发送一条设备环境记录。闸门序:config 快照 → kind 门控 → 栈 denylist →
     * 组装/发送。开关关闭时仅一次 volatile 读。
     */
    protected fun emit(
        kind: DeviceEnvKind,
        operation: DeviceEnvOperation,
        originalValue: String? = null,
        maskedValue: String? = null,
        intervention: Boolean = true,
        interventionAction: String? = null,
        targetPath: String? = null,
        targetClass: String? = null,
        targetProperty: String? = null,
        detectionPattern: String? = null,
        params: Map<String, String>? = null,
    ) {
        val switch = config()
        if (!switch.deviceEnvEnabled(kind)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val record = DeviceEnvRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            originalValue = originalValue,
            maskedValue = maskedValue,
            intervention = intervention,
            interventionAction = interventionAction,
            targetPath = targetPath,
            targetClass = targetClass,
            targetProperty = targetProperty,
            detectionPattern = detectionPattern,
            params = params,
            processName = processName,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }
}
