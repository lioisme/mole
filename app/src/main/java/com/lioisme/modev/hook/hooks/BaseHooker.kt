package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.ProcessIdentity
import java.security.Provider

/**
 * Hooker 基类:统一开关判断、记录组装与堆栈采集。
 * 所有子类的拦截逻辑均在 [ProbeContext.engine] 的 PROTECTIVE 模式下执行,异常不影响宿主。
 */
abstract class BaseHooker(
    context: ProbeContext,
    protected val kind: CryptoKind,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val tracker = context.tracker
    protected val sink = context.sink
    protected val packageName = context.packageName

    /** 装配上下文(V38:app dex 类显式装载 appClassLoader 消费点)。 */
    protected val contextRef = context

    /**
     * 目标进程名(V44 T2.6):经 [com.lioisme.modev.hook.ProcessIdentity] 单例读取——
     * 旧形态每个 Hooker 基类各自 `by lazy` 读一次 `/proc/self/cmdline`,冷启动期
     * 约 50 个实例 = 50 次文件读 + 字节解码,而进程身份在进程内恒定不变。
     */
    protected val processName: String get() = ProcessIdentity.processName

    abstract fun install()

    protected fun enabled(): Boolean = config().enabled(kind)

    /** 通用 provider 提取:getInstance 返回对象的标准化门面均带 getProvider()。 */
    protected fun providerOf(instance: Any?): String? =
        runCatching {
            instance?.javaClass?.getMethod("getProvider")?.invoke(instance) as? Provider
        }.getOrNull()?.name

    /**
     * 全量捕获策略下的双态视图:上限闸 [HookSwitch.maxBytesPerField](默认 1MB,0=无限)伸缩。
     * 注入侧不承担 UI 截断职责,仅在超保险丝时标记 truncated。
     */
    protected fun viewOf(bytes: ByteArray?): BytesView? {
        if (bytes == null) return null
        val max = config().maxBytesPerField
        if (max <= 0) return BytesView.of(bytes)
        return BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
    }

    /**
     * 组装并发送一条记录:开关关闭或命中调用栈 denylist 时直接跳过(零开销)。
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
        // P3-3: 配置单次快照——开关判断与记录组装共用同一次读取,避免两次读取间配置变更
        // 导致的判断/内容不一致,且不重复走通道读取
        val switch = config()
        if (!switch.enabled(kind)) return
        // 调用栈 denylist:任一帧命中即跳过整条记录(默认空列表,零成本)
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        // 堆栈采集成本说明:仅开关开启时执行(高频路径默认关闭);capture 在 Hook 线程
        // 内同步执行,成本与 maxStackDepth 线性相关,属功能开启时的预期开销
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
