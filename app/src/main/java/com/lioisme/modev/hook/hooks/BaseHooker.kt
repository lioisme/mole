package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.core.util.StackTraceFilter
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.ProbeContext
import java.io.File
import java.security.Provider

/**
 * Hooker 鍩虹被:缁熶竴寮€鍏冲垽鏂€佽褰曠粍瑁呬笌鍫嗘爤閲囬泦銆? * 鎵€鏈夊瓙绫荤殑鎷︽埅閫昏緫鍧囧湪 [ProbeContext.engine] 鐨?PROTECTIVE 妯″紡涓嬫墽琛?寮傚父涓嶄細褰卞搷瀹夸富銆? */
abstract class BaseHooker(
    context: ProbeContext,
    protected val kind: CryptoKind,
) {

    protected val engine = context.engine
    protected val config = context.config
    protected val tracker = context.tracker
    protected val sink = context.sink
    protected val packageName = context.packageName

    /** 鐩爣杩涚▼鍚?娉ㄥ叆渚т綅浜庣洰鏍囪繘绋嬪唴,/proc/self/cmdline 鍗崇洰鏍囪繘绋?銆?*/
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

    /** 閫氱敤 provider 鎻愬彇:getInstance 杩斿洖瀵硅薄鐨勬爣鍑嗛棬闈㈠潎甯?getProvider()銆?*/
    protected fun providerOf(instance: Any?): String? =
        runCatching {
            instance?.javaClass?.getMethod("getProvider")?.invoke(instance) as? Provider
        }.getOrNull()?.name

    /**
     * 鍏ㄩ噺鎹曡幏绛栫暐涓嬬殑鍙屾€佽鍥?涓婇檺闅?[HookSwitch.maxBytesPerField](榛樿 1MB,0=鏃犻檺)浼哥缉銆?     * 娉ㄥ叆渚т笉鎵挎媴 UI 鎴柇鑱岃矗,浠呭湪瓒呬繚闄╀笣鏃舵爣璁?truncated銆?     */
    protected fun viewOf(bytes: ByteArray?): BytesView? {
        if (bytes == null) return null
        val max = config().maxBytesPerField
        if (max <= 0) return BytesView.of(bytes)
        return BytesView.of(bytes, maxHexChars = max * 2, maxTextChars = max)
    }

    /**
     * 缁勮骞跺彂閫佷竴鏉¤褰?寮€鍏冲叧闂垨鍛戒腑璋冪敤鏍?denylist 鏃剁洿鎺ヨ烦杩?闆跺紑閿€銆?     */
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
