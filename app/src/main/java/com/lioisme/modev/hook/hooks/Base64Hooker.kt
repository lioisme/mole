package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.HookerSupport
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.RecordSink
import java.nio.ByteBuffer

/**
 * Base64 编码/解码捕获(android.util.Base64 全 7 个公开方法 + java.util.Base64 编解码实例)。
 * 默认开关关闭(噪声大),用于辅助定位编码链路。
 * android.util.Base64 为静态入口,使用进程级占位上下文;java.util.Base64 按实例关联(经工厂方法)。
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
        // ---- android.util.Base64(静态,全 7 个公开方法)----
        hook(f.find(ANDROID_B64, "encode", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "encode", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "encodeToString", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "encodeToString", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "decode", String::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result, null) }
        hook(f.find(ANDROID_B64, "decode", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result, null) }
        hook(f.find(ANDROID_B64, "decode", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result, null) }

        // ---- java.util.Base64(工厂 → 实例关联上下文)----
        val javaB64 = "java.util.Base64"
        hook(f.find(javaB64, "getEncoder")) { inv, result -> afterFactory(inv, result) }
        hook(f.find(javaB64, "getDecoder")) { inv, result -> afterFactory(inv, result) }
        hook(f.find(javaB64, "getUrlEncoder")) { inv, result -> afterFactory(inv, result) }
        hook(f.find(javaB64, "getUrlDecoder")) { inv, result -> afterFactory(inv, result) }
        hook(f.find(javaB64, "getMimeEncoder")) { inv, result -> afterFactory(inv, result) }
        hook(f.find(javaB64, "getMimeEncoder", Int::class.javaPrimitiveType, ByteArray::class.java)) { inv, result -> afterFactory(inv, result) }
        hook(f.find(javaB64, "getMimeDecoder")) { inv, result -> afterFactory(inv, result) }

        val enc = "java.util.Base64\$Encoder"
        hook(f.find(enc, "encode", ByteArray::class.java)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(enc, "encodeToString", ByteArray::class.java)) { inv, result -> afterEncode(inv, result, null) }
        hookBefore(f.find(enc, "encode", ByteBuffer::class.java)) { inv -> HookerSupport.stash(inv.getArg(0)) }
        hook(f.find(enc, "encode", ByteBuffer::class.java)) { inv, result -> afterEncode(inv, result, null) }

        val dec = "java.util.Base64\$Decoder"
        hook(f.find(dec, "decode", ByteArray::class.java)) { inv, result -> afterDecode(inv, result, null) }
        hook(f.find(dec, "decode", String::class.java)) { inv, result -> afterDecode(inv, result, null) }
        hookBefore(f.find(dec, "decode", ByteBuffer::class.java)) { inv -> HookerSupport.stash(inv.getArg(0)) }
        hook(f.find(dec, "decode", ByteBuffer::class.java)) { inv, result -> afterDecode(inv, result, null) }
    }

    private fun hookBefore(method: java.lang.reflect.Method?, before: (Invocation) -> Unit) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (enabled()) {
                    before(invocation)
                }
            }
        })
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

    private fun afterFactory(invocation: Invocation, result: Any?) {
        val target = result ?: return
        tracker.contextFor(target).algorithm = "Base64"
    }

    private fun afterEncode(invocation: Invocation, result: Any?, _unused: Any?) {
        val input = when (val arg = invocation.getArg(0)) {
            is ByteArray -> {
                val off = invocation.getArg(1) as? Int
                val len = invocation.getArg(2) as? Int
                if (off != null && len != null) HookerSupport.slice(arg, off, len) else arg
            }
            else -> HookerSupport.take()
        }
        val output = when (result) {
            is String -> result.toByteArray(Charsets.US_ASCII)
            is ByteArray -> result
            is ByteBuffer -> HookerSupport.bufferRemaining(result)
            else -> null
        }
        if (input == null || output == null) return
        emit(
            operation = CryptoOperation.ENCODE,
            algorithm = "Base64",
            contextId = CryptoContextTracker.identity(contextOf(invocation)),
            input = viewOf(input),
            output = viewOf(output),
            params = flagsParam(invocation),
        )
    }

    private fun afterDecode(invocation: Invocation, result: Any?, _unused: Any?) {
        val input = when (val arg = invocation.getArg(0)) {
            is String -> arg.toByteArray(Charsets.US_ASCII)
            is ByteArray -> {
                val off = invocation.getArg(1) as? Int
                val len = invocation.getArg(2) as? Int
                if (off != null && len != null) HookerSupport.slice(arg, off, len) else arg
            }
            else -> HookerSupport.take()
        }
        val output = when (result) {
            is ByteArray -> result
            is ByteBuffer -> HookerSupport.bufferRemaining(result)
            else -> null
        }
        if (input == null || output == null) return
        emit(
            operation = CryptoOperation.DECODE,
            algorithm = "Base64",
            contextId = CryptoContextTracker.identity(contextOf(invocation)),
            input = viewOf(input),
            output = viewOf(output),
            params = flagsParam(invocation),
        )
    }

    /** 实例上下文:java.util.Base64 按实例关联;android.util.Base64 静态调用用进程级占位。 */
    private fun contextOf(invocation: Invocation): Any =
        invocation.thisObject ?: STATIC_CTX

    private fun flagsParam(invocation: Invocation): Map<String, String>? {
        val flags = invocation.getArg(1) as? Int
        if (flags == null || flags == 0) return null
        return mapOf("flags" to flags.toString())
    }

    companion object {
        private const val ANDROID_B64 = "android.util.Base64"
        private val STATIC_CTX = Any()
    }
}