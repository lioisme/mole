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
import com.lioisme.modev.hook.RecordSink
import java.security.KeyPair
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec

/**
 * 密钥生成捕获:KeyGenerator(对称随机密钥)与 KeyPairGenerator(RSA/EC 密钥对)。
 * 含 init 参数捕获(keysize / AlgorithmParameterSpec / SecureRandom,共 8 个重载)
 * 与密钥对分离(私钥→key、公钥→publicKey)及 RSA/DSA/EC 结构参数提取。
 */
class KeyGenerationHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.KEY_GENERATION) {

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

    /** 从 init 首参提取 keysize/曲线/keystore 参数,写入上下文并返回 params 承载。 */
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