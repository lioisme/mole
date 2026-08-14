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
import java.security.Provider

/**
 * 密钥生成捕获:KeyGenerator(对称随机密钥)与 KeyPairGenerator(RSA/EC 密钥对)。
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
        hook(f.find(KG, "getInstance", String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KG, "getInstance", String::class.java, String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KG, "getInstance", String::class.java, Provider::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KG, "generateKey")) { c, result -> afterGenerateKey(c, result) }

        hook(f.find(KPG, "getInstance", String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KPG, "getInstance", String::class.java, String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KPG, "getInstance", String::class.java, Provider::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KPG, "generateKeyPair")) { c, result -> afterGenerateKeyPair(c, result) }
    }

    private fun hook(method: java.lang.reflect.Method?, after: (XposedInterface.Chain) -> Unit) {
        hook(method) { chain, _ -> after(chain) }
    }

    private fun hook(method: java.lang.reflect.Method?, after: (XposedInterface.Chain, Any?) -> Unit) {
        if (method == null) return
        engine.hook(method) { chain ->
            val result = chain.proceed()
            if (enabled()) {
                after(chain, result)
            }
            result
        }
    }

    private fun afterGetInstance(chain: XposedInterface.Chain, result: Any?) {
        val target = result ?: return
        tracker.contextFor(target).algorithm = chain.getArg(0) as? String ?: ""
    }

    private fun afterGenerateKey(chain: XposedInterface.Chain, result: Any?) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val keyBytes = (result as? javax.crypto.SecretKey)?.encoded
        emit(
            operation = CryptoOperation.GENERATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
            key = BytesView.of(keyBytes),
        )
    }

    private fun afterGenerateKeyPair(chain: XposedInterface.Chain, result: Any?) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val pair = result as? java.security.KeyPair
        val publicBytes = pair?.public?.encoded
        val privateBytes = pair?.private?.encoded
        emit(
            operation = CryptoOperation.GENERATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
            key = BytesView.of(privateBytes),
            output = BytesView.of(publicBytes),
        )
    }

    companion object {
        private const val KG = "javax.crypto.KeyGenerator"
        private const val KPG = "java.security.KeyPairGenerator"
    }
}