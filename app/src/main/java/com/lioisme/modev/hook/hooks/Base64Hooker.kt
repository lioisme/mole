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
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.nio.ByteBuffer

/**
 * Base64 缂栫爜/瑙ｇ爜鎹曡幏(android.util.Base64 鍏?7 涓叕寮€鏂规硶 + java.util.Base64 缂栬В鐮佸疄渚?銆? * 榛樿寮€鍏冲叧闂?鍣０澶?,鐢ㄤ簬杈呭姪瀹氫綅缂栫爜閾捐矾銆? * android.util.Base64 涓洪潤鎬佸叆鍙?浣跨敤杩涚▼绾у崰浣嶄笂涓嬫枃;java.util.Base64 鎸夊疄渚嬪叧鑱?缁忓伐鍘傛柟娉?銆? */
class Base64Hooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.BASE64) {

    override fun install() {
        val f = MethodFinder
        // ---- android.util.Base64(闈欐€?鍏?7 涓叕寮€鏂规硶)----
        hook(f.find(ANDROID_B64, "encode", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "encode", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "encodeToString", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "encodeToString", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterEncode(inv, result, null) }
        hook(f.find(ANDROID_B64, "decode", String::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result, null) }
        hook(f.find(ANDROID_B64, "decode", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result, null) }
        hook(f.find(ANDROID_B64, "decode", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterDecode(inv, result, null) }

        // ---- java.util.Base64(宸ュ巶 鈫?瀹炰緥鍏宠仈涓婁笅鏂?----
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

    /** 瀹炰緥涓婁笅鏂?java.util.Base64 鎸夊疄渚嬪叧鑱?android.util.Base64 闈欐€佽皟鐢ㄧ敤杩涚▼绾у崰浣嶃€?*/
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
