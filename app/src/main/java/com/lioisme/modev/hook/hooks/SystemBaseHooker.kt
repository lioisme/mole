package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.core.model.SystemRecord
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.io.File

/**
 * 系统信息 Hooker 基类(V33):统一开关判断(master + kind 双门控)、栈 denylist、
 * 会话节流与记录组装;子类拦截逻辑均在 engine PROTECTIVE 模式下执行,异常不影响宿主。
 */
abstract class SystemBaseHooker(
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
