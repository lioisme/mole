package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.core.model.NetworkRecord
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.io.File

/**
 * 网络环境 Hooker 基类(V30):统一开关判断(network 类别,master+kind 双门控前置)、
 * 栈 denylist、会话节流与记录组装;所有子类拦截逻辑均在 engine 的 PROTECTIVE 模式下
 * 执行,异常不影响宿主。
 *
 * 与 [BehaviorBaseHooker]/[SqlBaseHooker] 同构。
 */
abstract class NetworkBaseHooker(
    context: ProbeContext,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val sink: RecordSink = context.sink
    protected val packageName = context.packageName

    /** 装配上下文(V38:app dex 类显式装载 appClassLoader 消费点)。 */
    protected val contextRef = context

    /** 目标进程名(/proc/self/cmdline)。 */
    protected val processName: String by lazy {
        runCatching { File("/proc/self/cmdline").readBytes() }
            .getOrNull()
            ?.toString(Charsets.UTF_8)
            ?.substringBefore('\u0000')
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }

    abstract fun install()

    /**
     * 干预闸门(V37 T0.1 FeatureGate 红线):干预动作本身必须经 master ∧ kind 双门控,
     * 禁止裸布尔位直读——master=false 或 kind=false 时 setResult 等干预一律不得发生。
     */
    protected fun featureOn(kind: NetworkKind): Boolean = config().networkEnabled(kind)

    /** 字节内容视图(maxBytesPerField 伸缩;0=无限)。 */
    protected fun viewOf(bytes: ByteArray?): BytesView? {
        if (bytes == null) return null
        val max = config().maxBytesPerField
        if (max <= 0) return BytesView.of(bytes)
        return BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
    }

    /**
     * 组装并发送一条网络记录。闸门序:config 快照 → kind 门控 → 栈 denylist → 组装/发送。
     */
    protected fun emit(
        kind: NetworkKind,
        operation: NetworkOperation,
        url: String? = null,
        method: String? = null,
        statusCode: Int? = null,
        requestHeaders: Map<String, String>? = null,
        responseHeaders: Map<String, String>? = null,
        requestBody: BytesView? = null,
        responseBody: BytesView? = null,
        sslProtocol: String? = null,
        cipherSuite: String? = null,
        peerCertificates: List<String>? = null,
        proxyHost: String? = null,
        proxyPort: Int? = null,
        vpnActive: Boolean? = null,
        params: Map<String, String>? = null,
        contextId: Long = 0L,
    ) {
        val switch = config()
        if (!switch.networkEnabled(kind)) return
        if (switch.ignoreStackPrefixes.isNotEmpty() &&
            StackTraceFilter.isDenied(Thread.currentThread().stackTrace, switch.ignoreStackPrefixes)
        ) {
            return
        }
        val record = NetworkRecord(
            packageName = packageName,
            kind = kind,
            operation = operation,
            url = url,
            method = method,
            statusCode = statusCode,
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            requestBody = requestBody,
            responseBody = responseBody,
            sslProtocol = sslProtocol,
            cipherSuite = cipherSuite,
            peerCertificates = peerCertificates,
            proxyHost = proxyHost,
            proxyPort = proxyPort,
            vpnActive = vpnActive,
            params = params,
            processName = processName,
            threadName = Thread.currentThread().name,
            stack = if (switch.logStack) StackTraceFilter.capture(switch.maxStackDepth) else emptyList(),
        )
        sink.emit(record)
    }
}
