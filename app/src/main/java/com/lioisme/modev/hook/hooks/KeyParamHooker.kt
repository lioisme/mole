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
 * 密钥/参数对象构造捕获:
 * SecretKeySpec(密钥明文)、IvParameterSpec(IV)、GCMParameterSpec(IV+tag)、PBEKeySpec(口令)。
 */
class KeyParamHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.KEY_PARAM) {

    override fun install() {
        val f = MethodFinder
        hookCtor(f.findCtor(SECRET_KEY_SPEC, ByteArray::class.java, String::class.java)) { inv, r ->
            record(inv, r, CryptoOperation.CONSTRUCT, "SecretKeySpec", key = bytes(inv, 0), alg = str(inv, 1))
        }
        hookCtor(f.findCtor(SECRET_KEY_SPEC, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)) { inv, r ->
            record(inv, r, CryptoOperation.CONSTRUCT, "SecretKeySpec", key = bytes(inv, 0), alg = str(inv, 3))
        }
        hookCtor(f.findCtor(IV_SPEC, ByteArray::class.java)) { inv, r ->
            record(inv, r, CryptoOperation.CONSTRUCT, "IvParameterSpec", iv = bytes(inv, 0))
        }
        hookCtor(f.findCtor(IV_SPEC, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, r ->
            record(inv, r, CryptoOperation.CONSTRUCT, "IvParameterSpec", iv = bytes(inv, 0))
        }
        hookCtor(f.findCtor(GCM_SPEC, Int::class.javaPrimitiveType, ByteArray::class.java)) { inv, r ->
            record(inv, r, CryptoOperation.CONSTRUCT, "GCMParameterSpec", iv = bytes(inv, 1))
        }
        hookCtor(f.findCtor(GCM_SPEC, Int::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, r ->
            record(inv, r, CryptoOperation.CONSTRUCT, "GCMParameterSpec", iv = bytes(inv, 1))
        }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java)) { inv, r -> record(inv, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chars(inv, 0)) }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, r -> record(inv, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chars(inv, 0), iv = bytes(inv, 1)) }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, r -> record(inv, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chars(inv, 0), iv = bytes(inv, 1)) }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, r -> record(inv, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chars(inv, 0), iv = bytes(inv, 1)) }
    }

    private fun hookCtor(ctor: java.lang.reflect.Constructor<*>?, after: (Invocation, Any?) -> Unit) {
        if (ctor == null) return
        engine.hook(ctor, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (enabled()) {
                    after(invocation, invocation.result)
                }
            }
        })
    }

    private fun record(
        invocation: Invocation,
        result: Any?,
        operation: CryptoOperation,
        typeName: String,
        key: BytesView? = null,
        iv: BytesView? = null,
        alg: String? = null,
    ) {
        emit(
            operation = operation,
            algorithm = alg ?: typeName,
            contextId = result?.let { CryptoContextTracker.identity(it) } ?: 0L,
            key = key,
            iv = iv,
        )
    }

    private fun bytes(invocation: Invocation, index: Int): BytesView? =
        (invocation.getArg(index) as? ByteArray)?.let { BytesView.of(it) }

    private fun chars(invocation: Invocation, index: Int): BytesView? =
        (invocation.getArg(index) as? CharArray)?.let {
            BytesView.of(String(it).toByteArray(Charsets.UTF_8))
        }

    private fun str(invocation: Invocation, index: Int): String? =
        invocation.getArg(index) as? String

    companion object {
        private const val SECRET_KEY_SPEC = "javax.crypto.spec.SecretKeySpec"
        private const val IV_SPEC = "javax.crypto.spec.IvParameterSpec"
        private const val GCM_SPEC = "javax.crypto.spec.GCMParameterSpec"
        private const val PBE_SPEC = "javax.crypto.spec.PBEKeySpec"
    }
}