package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import com.lioisme.modev.hook.ProcessIdentity
import java.lang.reflect.Method

/**
 * 行为 Hooker 基类(V27,镜像 [SqlBaseHooker]):
 * 统一开关判断(behavior 类别,master+kind 双门控前置)、路径降噪([PathGate])、
 * 栈 denylist、会话节流([SessionThrottle])与记录组装;所有子类拦截逻辑均在
 * engine 的 PROTECTIVE 模式下执行,异常不影响宿主。
 *
 * 闸门顺序(性能关键):config 快照 → kind 门控(volatile 前置)→ 路径降噪 →
 * 栈 denylist → 节流 → 组装/发送。开关关闭时仅一次 volatile 读。
 */
abstract class BehaviorBaseHooker(
    context: ProbeContext,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val sink: RecordSink = context.sink
    protected val packageName = context.packageName

    /** 会话节流(同键 1s 窗口去重;LRU 容量护栏)。 */
    private val throttle = SessionThrottle()

    /**
     * 目标进程名(V44 T2.6):经 [com.lioisme.modev.hook.ProcessIdentity] 单例读取——
     * 旧形态每个 Hooker 基类各自 `by lazy` 读一次 `/proc/self/cmdline`,冷启动期
     * 约 50 个实例 = 50 次文件读 + 字节解码,而进程身份在进程内恒定不变。
     */
    protected val processName: String get() = ProcessIdentity.processName

    abstract fun install()

    /** 字节内容视图(maxBytesPerField 伸缩;0=无限;镜像既有 viewOf 契约)。 */
    protected fun viewOf(bytes: ByteArray?): BytesView? {
        if (bytes == null) return null
        val max = config().maxBytesPerField
        if (max <= 0) return BytesView.of(bytes)
        return BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
    }

    /**
     * 热路径门控先行(V44 T2.2,与 [NetworkBaseHooker.featureOn] 同构):
     * 回调首条有效语句即可判定,禁止把取参/整文件拼接/字节物化排在门控之前
     * (fileRead 默认关时旧形态每次调用白造整文件字符串)。
     */
    protected fun featureOn(kind: BehaviorKind): Boolean = config().behaviorEnabled(kind)

    /**
     * 组装并发送一条行为记录。闸门序见类注释;fileSize 仅在调用即可得时由
     * 调用方传入(本基类不做任何额外探测调用——被动采集红线 #11)。
     */
    protected fun emit(
        kind: BehaviorKind,
        operation: BehaviorOperation,
        path: String? = null,
        destPath: String? = null,
        command: List<String>? = null,
        fileSize: Long? = null,
        content: BytesView? = null,
        params: Map<String, String>? = null,
        contextId: Long = 0L,
    ) {
        // 配置单次快照:开关判断与记录组装共用同一次读取
        val switch = config()
        if (!switch.behaviorEnabled(kind)) return
        if (PathGate.isIgnored(path, switch.ignorePathPrefixes)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val throttleKey = "$kind|$operation|${command?.joinToString(" ") ?: path.orEmpty()}"
        if (throttle.shouldSkip(throttleKey)) return
        val record = BehaviorRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            path = path,
            destPath = destPath,
            command = command,
            fileSize = fileSize,
            content = content,
            params = params,
            processName = processName,
            contextId = contextId,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }

    /** 按方法注册 after hook(仅原方法正常返回后触发,Invocation 契约保证)。 */
    protected fun hookAfter(methods: List<Method>, after: (Invocation, Any?) -> Unit) {
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    after(invocation, invocation.result)
                }
            })
        }
    }
}
