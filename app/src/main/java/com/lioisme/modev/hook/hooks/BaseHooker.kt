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
import java.io.File
import java.security.Provider

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

    /** 目标进程名(注入侧位于目标进程内,/proc/self/cmdline 即目标进程)。 */
    protected val processName: String by lazy {
        runCatching { File("/proc/self/cmdline").readBytes() }
            .getOrNull()
            ?.toString(Charsets.UTF_8)
            ?.substringBefore('\u0000')
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }

    abstract fun install()

    protected fun enabled(): Boolean = config().enabled(kind)

    /** 通用 provider 提取:getInstance 返回对象的标准门面均带 getProvider()。 */
    protected fun providerOf(instance: Any?): String? =
        runCatching {
            instance?.javaClass?.getMethod("getProvider")?.invoke(instance) as? Provider
        }.getOrNull()?.name

    /**
     * 全量捕获策略下的双态视图:上限随 [HookSwitch.maxBytesPerField](默认 1MB,0=无限)伸缩。
     * 注入侧不承担 UI 截断职责,仅在超保险丝时标记 truncated。
     */
    protected fun viewOf(bytes: ByteArray?): BytesView? {
        if (bytes == null) return null
        val max = config().maxBytesPerField
        if (max <= 0) return BytesView.of(bytes)
        return BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
    }

    /**
     * 组装并发送一条记录;开关关闭或命中调用栈 denylist 时直接跳过,零开销。
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
        salt: BytesView? = null,
        aad: BytesView? = null,
        publicKey: BytesView? = null,
        params: Map<String, String>? = null,
        verifyResult: Boolean? = null,
        keyAlias: String? = null,
        keyExportable: Boolean = true,
        direction: Int? = null,
        provider: String? = null,
    ) {
        if (!enabled()) return
        val switch = config()
        // 调用栈 denylist:任一帧命中即跳过整条记录(默认空列表,零成本)
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
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
            salt = salt ?: context?.salt,
            aad = aad ?: context?.aad,
            publicKey = publicKey ?: context?.publicKey,
            params = params,
            verifyResult = verifyResult ?: context?.verified,
            processName = processName,
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