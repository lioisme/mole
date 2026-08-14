package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.RecordSink
import io.github.libxposed.api.XposedInterface

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
        hookCtor(f.findCtor(SECRET_KEY_SPEC, ByteArray::class.java, String::class.java)) { c, r ->
            record(c, r, CryptoOperation.CONSTRUCT, "SecretKeySpec", key = chainBytes(c, 0), alg = chainStr(c, 1))
        }
        hookCtor(f.findCtor(SECRET_KEY_SPEC, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)) { c, r ->
            record(c, r, CryptoOperation.CONSTRUCT, "SecretKeySpec", key = chainBytes(c, 0), alg = chainStr(c, 3))
        }
        hookCtor(f.findCtor(IV_SPEC, ByteArray::class.java)) { c, r ->
            record(c, r, CryptoOperation.CONSTRUCT, "IvParameterSpec", iv = chainBytes(c, 0))
        }
        hookCtor(f.findCtor(IV_SPEC, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c, r ->
            record(c, r, CryptoOperation.CONSTRUCT, "IvParameterSpec", iv = chainBytes(c, 0))
        }
        hookCtor(f.findCtor(GCM_SPEC, Int::class.javaPrimitiveType, ByteArray::class.java)) { c, r ->
            record(c, r, CryptoOperation.CONSTRUCT, "GCMParameterSpec", iv = chainBytes(c, 1))
        }
        hookCtor(f.findCtor(GCM_SPEC, Int::class.javaPrimitiveType, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c, r ->
            record(c, r, CryptoOperation.CONSTRUCT, "GCMParameterSpec", iv = chainBytes(c, 1))
        }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java)) { c, r -> record(c, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chainChars(c, 0)) }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType)) { c, r -> record(c, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chainChars(c, 0), iv = chainBytes(c, 1)) }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c, r -> record(c, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chainChars(c, 0), iv = chainBytes(c, 1)) }
        hookCtor(f.findCtor(PBE_SPEC, CharArray::class.java, ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c, r -> record(c, r, CryptoOperation.CONSTRUCT, "PBEKeySpec", key = chainChars(c, 0), iv = chainBytes(c, 1)) }
    }

    private fun hookCtor(ctor: java.lang.reflect.Constructor<*>?, after: (XposedInterface.Chain, Any?) -> Unit) {
        if (ctor == null) return
        engine.hook(ctor) { chain ->
            val result = chain.proceed()
            if (enabled()) {
                after(chain, result)
            }
            result
        }
    }

    private fun record(
        chain: XposedInterface.Chain,
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

    private fun chainBytes(chain: XposedInterface.Chain, index: Int): BytesView? =
        (chain.getArgOrNull(index) as? ByteArray)?.let { BytesView.of(it) }

    private fun chainChars(chain: XposedInterface.Chain, index: Int): BytesView? =
        (chain.getArgOrNull(index) as? CharArray)?.let {
            BytesView.of(String(it).toByteArray(Charsets.UTF_8))
        }

    private fun chainStr(chain: XposedInterface.Chain, index: Int): String? =
        chain.getArgOrNull(index) as? String

    companion object {
        private const val SECRET_KEY_SPEC = "javax.crypto.spec.SecretKeySpec"
        private const val IV_SPEC = "javax.crypto.spec.IvParameterSpec"
        private const val GCM_SPEC = "javax.crypto.spec.GCMParameterSpec"
        private const val PBE_SPEC = "javax.crypto.spec.PBEKeySpec"
    }
}