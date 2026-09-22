package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.ProcessIdentity
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink

/**
 * 系统信息 Hooker 基类(V33):统一开关判断(master + kind 双门控)、栈 denylist、
 * 会话节流与记录组装;子类拦截逻辑均在 engine PROTECTIVE 模式下执行,异常不影响宿主。
 */
abstract class SystemBaseHooker(
    context: ProbeContext,
) {
    protected val engine = context.engine
    protected val config = context.config
    protected val broadcast = context.broadcast // V43 T3/D-4:热门平台方法单一广播注册点
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
     * 热路径门控先行(V44 T2.2,与 [NetworkBaseHooker.featureOn] 同构):
     * 回调首条有效语句即可判定,禁止把取参/字符串化/反射等重成本排在门控之前。
     * master ∧ kind 双门控,单次 volatile 读。
     */
    protected fun featureOn(kind: SystemKind): Boolean = config().systemEnabled(kind)

    protected fun emit(
        kind: SystemKind,
        operation: SystemOperation,
        logcatTag: String? = null,
        logcatLevel: String? = null,
        logcatMessage: String? = null,
        appVersion: String? = null,
        appVersionCode: Int? = null,
        appMinSdk: Int? = null,
        appTargetSdk: Int? = null,
        appPermissions: List<String>? = null,
        signatureHash: String? = null,
        signatureBase64: String? = null,
        params: Map<String, String>? = null,
    ) {
        val switch = config()
        if (!switch.systemEnabled(kind)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val throttleKey = "$kind|$operation|${logcatTag ?: signatureHash ?: ""}"
        if (throttle.shouldSkip(throttleKey)) return
        val record = SystemRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            logcatTag = logcatTag,
            logcatLevel = logcatLevel,
            logcatMessage = logcatMessage,
            appVersion = appVersion,
            appVersionCode = appVersionCode,
            appMinSdk = appMinSdk,
            appTargetSdk = appTargetSdk,
            appPermissions = appPermissions,
            signatureHash = signatureHash,
            signatureBase64 = signatureBase64,
            params = params,
            processName = processName,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }
}
