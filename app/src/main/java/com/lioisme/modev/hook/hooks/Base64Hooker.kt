package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.RecordSink

/**
 * Base64 编码/解码捕获(android.util.Base64)。默认开关关闭(噪声大),用于辅助定位编码链路。
 */
class Base64Hooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.BASE64) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(B64, "encodeToString", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result) }
        hook(f.find(B64, "decode", String::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result) }
        hook(f.find(B64, "decode", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result) }
    }

    private fun hook(method: java.lang.reflect.Method?, after: (Invocation, Any?) -> Unit) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (enabled()) {
                    after(invocation, invocation.result)
                }
            }
        })
    }

    private fun afterEncode(invocation: Invocation, result: Any?) {
        val input = invocation.getArg(0) as? ByteArray
        val output = (result as? String)?.toByteArray(Charsets.US_ASCII)
        if (input == null || output == null) return
        emit(
            operation = CryptoOperation.ENCODE,
            algorithm = "Base64",
            contextId = CryptoContextTracker.identity(STATIC_CTX),
            input = BytesView.of(input),
            output = BytesView.of(output),
        )
    }

    private fun afterDecode(invocation: Invocation, result: Any?) {
        val arg = invocation.getArg(0)
        val input = when (arg) {
            is String -> arg.toByteArray(Charsets.US_ASCII)
            is ByteArray -> arg
            else -> null
        }
        val output = result as? ByteArray
        if (input == null || output == null) return
        emit(
            operation = CryptoOperation.DECODE,
            algorithm = "Base64",
            contextId = CryptoContextTracker.identity(STATIC_CTX),
            input = BytesView.of(input),
            output = BytesView.of(output),
        )
    }

    companion object {
        private const val B64 = "android.util.Base64"
        private val STATIC_CTX = Any()
    }
}