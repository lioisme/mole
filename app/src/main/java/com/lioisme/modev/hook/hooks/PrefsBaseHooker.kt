package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.ProcessIdentity
import java.lang.reflect.Method

/**
 * 偏好 Hooker 基类(V28,镜像 [BehaviorBaseHooker]):
 * 统一开关判断(prefs 类别,master+kind 双门控前置)、路径降噪([PathGate])、
 * 栈 denylist、会话节流([SessionThrottle])与记录组装;所有子类拦截逻辑均在
 * engine 的 PROTECTIVE 模式下执行,异常不影响宿主。
 *
 * 闸门顺序(性能关键):config 快照 → kind 门控(volatile 前置)→ 路径降噪 →
 * 栈 denylist → 节流 → 组装/发送。开关关闭时仅一次 volatile 读。
 *
 * 被动红线:值域仅承载「随调用即可得」的实参/返回值文本;本基类不做任何
 * 额外探测调用,绝不反射读取 SharedPreferencesImpl 内部字段。
 */
abstract class PrefsBaseHooker(
    context: ProbeContext,
    /** 实例索引(probe 级共享:OPEN 登记、get/put 反查)。 */
    protected val index: PrefsInstanceIndex,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val sink = context.sink
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

    /**
     * 文本值视图(fileContentCapture 闸门由调用方判定后传入;
     * 按 maxBytesPerField 截断,0=无限,镜像 [BehaviorBaseHooker] viewOf 契约)。
     */
    protected fun textViewOf(value: String?): BytesView? {
        if (value.isNullOrEmpty()) return null
        val max = config().maxBytesPerField
        val bytes = value.toByteArray(Charsets.UTF_8)
        return if (max <= 0) {
            BytesView.of(bytes)
        } else {
            BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
        }
    }

    /**
     * 组装并发送一条偏好记录。闸门序见类注释;value 仅在调用即可得时由
     * 调用方传入(被动采集红线,与行为监听 #11 同源)。
     */
    protected fun emit(
        kind: PrefsKind,
        operation: PrefsOperation,
        key: String? = null,
        value: BytesView? = null,
        path: String? = null,
        params: Map<String, String>? = null,
    ) {
        // 配置单次快照:开关判断与记录组装共用同一次读取
        val switch = config()
        if (!switch.prefsEnabled(kind)) return
        if (PathGate.isIgnored(path, switch.ignorePathPrefixes)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val throttleKey = "$kind|$operation|${path.orEmpty()}|${key.orEmpty()}"
        if (throttle.shouldSkip(throttleKey)) return
        sink.emit(
            PrefsRecord(
                packageName = packageName,
                kind = kind,
                operation = operation,
                key = key,
                value = value,
                path = path,
                params = params,
                processName = processName,
                threadName = Thread.currentThread().name,
                stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
            ),
        )
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
