package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.security.KeyPair
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec

/**
 * 瀵嗛挜鐢熸垚鎹曡幏:KeyGenerator(瀵圭О闅忔満瀵嗛挜)涓?KeyPairGenerator(RSA/EC 瀵嗛挜瀵?銆? * 鍚?init 鍙傛暟鎹曡幏(keysize / AlgorithmParameterSpec / SecureRandom,鍏?8 涓噸杞?
 * 涓庡瘑閽ュ鍒嗙(绉侀挜鈫択ey銆佸叕閽モ啋publicKey)鍙?RSA/DSA/EC 缁撴瀯鍙傛暟鎻愬彇銆? */
class KeyGenerationHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.KEY_GENERATION) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(KG, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KG, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KG, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KG, "init", Int::class.javaPrimitiveType)) { inv -> afterInitKeyGenerator(inv) }
        hook(f.find(KG, "init", Int::class.javaPrimitiveType, SecureRandom::class.java)) { inv -> afterInitKeyGenerator(inv) }
        hook(f.find(KG, "init", AlgorithmParameterSpec::class.java)) { inv -> afterInitKeyGenerator(inv) }
        hook(f.find(KG, "init", AlgorithmParameterSpec::class.java, SecureRandom::class.java)) { inv -> afterInitKeyGenerator(inv) }
        hook(f.find(KG, "generateKey")) { inv, result -> afterGenerateKey(inv, result) }

        hook(f.find(KPG, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KPG, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KPG, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KPG, "initialize", Int::class.javaPrimitiveType)) { inv -> afterInitKeyPairGenerator(inv) }
        hook(f.find(KPG, "initialize", Int::class.javaPrimitiveType, SecureRandom::class.java)) { inv -> afterInitKeyPairGenerator(inv) }
        hook(f.find(KPG, "initialize", AlgorithmParameterSpec::class.java)) { inv -> afterInitKeyPairGenerator(inv) }
        hook(f.find(KPG, "initialize", AlgorithmParameterSpec::class.java, SecureRandom::class.java)) { inv -> afterInitKeyPairGenerator(inv) }
        hook(f.find(KPG, "generateKeyPair")) { inv, result -> afterGenerateKeyPair(inv, result) }
    }

    private fun hook(method: java.lang.reflect.Method?, after: (Invocation) -> Unit) {
        hook(method) { inv, _ -> after(inv) }
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

    private fun afterGetInstance(invocation: Invocation, result: Any?) {
        val target = result ?: return
        val ctx = tracker.contextFor(target)
        ctx.algorithm = invocation.getArg(0) as? String ?: ""
        ctx.provider = providerOf(target)
    }

    private fun afterInitKeyGenerator(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val params = specParams(invocation.getArg(0), ctx)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            params = params,
        )
    }

    private fun afterInitKeyPairGenerator(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val params = specParams(invocation.getArg(0), ctx)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            params = params,
        )
    }

    /** 浠?init 棣栧弬鎻愬彇 keysize/鏇茬嚎/keystore 鍙傛暟,鍐欏叆涓婁笅鏂囧苟杩斿洖 params 鎵胯浇銆?*/
    private fun specParams(arg: Any?, ctx: CryptoContext): Map<String, String>? {
        if (arg == null) return null
        val params = mutableMapOf<String, String>()
        when (arg) {
            is Int -> {
                ctx.keysize = arg
                params["keysize"] = arg.toString()
            }
            is AlgorithmParameterSpec -> {
                val ext = SpecExtractor.extractKeystoreSpec(arg) ?: SpecExtractor.extract(arg)
                params.putAll(ext.params)
                ext.params["keysize"]?.toIntOrNull()?.let { ctx.keysize = it }
            }
        }
        return params.takeIf { it.isNotEmpty() }
    }

    private fun afterGenerateKey(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val secret = result as? javax.crypto.SecretKey
        val keyBytes = secret?.encoded
        val params = mutableMapOf<String, String>()
        secret?.algorithm?.let { params["algorithm"] = it }
        params.putAll(KeyParamExtractor.paramsOf(secret))
        emit(
            operation = CryptoOperation.GENERATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = viewOf(keyBytes),
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    private fun afterGenerateKeyPair(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val pair = result as? KeyPair
        val privateBytes = pair?.private?.encoded
        val publicBytes = pair?.public?.encoded
        val params = KeyParamExtractor.paramsOf(pair?.private)
        emit(
            operation = CryptoOperation.GENERATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = viewOf(privateBytes),
            publicKey = viewOf(publicBytes),
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        private const val KG = "javax.crypto.KeyGenerator"
        private const val KPG = "java.security.KeyPairGenerator"
    }
}
