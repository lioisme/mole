package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.RecordSink

/**
 * Hooker 基类:统一开关判断、记录组装与堆栈采集。
 * 所有子类的拦截逻辑均在 [HookEngine] 的 PROTECTIVE 模式下执行,异常不会影响宿主。
 */
abstract class BaseHooker(
    protected val engine: HookEngine,
    protected val config: () -> HookSwitch,
    protected val tracker: CryptoContextTracker,
    protected val sink: RecordSink,
    protected val packageName: String,
    protected val kind: CryptoKind,
) {

    abstract fun install()

    protected fun enabled(): Boolean = config().enabled(kind)

    /**
     * 组装并发送一条记录;开关关闭时直接跳过,零开销。
     */
    protected fun emit(
        operation: CryptoOperation,
        algorithm: String,
        contextId: Long = 0L,
        context: CryptoContext? = null,
        input: BytesView? = null,
        output: BytesView? = null,
        key: BytesView? = null,
        iv: BytesView? = null,
        keyAlias: String? = null,
        keyExportable: Boolean = true,
        direction: Int? = null,
        provider: String? = null,
    ) {
        if (!enabled()) return
        val switch = config()
        val record = CryptoRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            algorithm = algorithm,
            provider = provider ?: context?.provider,
            direction = direction ?: context?.direction,
            key = key ?: context?.key,
            iv = iv ?: context?.iv,
            keyAlias = keyAlias ?: context?.keyAlias,
            keyExportable = keyExportable && (context?.keyExportable ?: true),
            input = input,
            output = output,
            contextId = contextId,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) {
                StackTraceFilter.capture(switch.maxStackDepth)
            } else {
                emptyList()
            },
        )
        sink.emit(record)
    }
}