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
 * 密钥还原捕获:KeyFactory.generatePrivate/Public 与 SecretKeyFactory.generateSecret。
 */
class KeyFactoryHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.KEY_FACTORY) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(KF, "getInstance", String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KF, "getInstance", String::class.java, String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KF, "getInstance", String::class.java, Provider::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(KF, "generatePrivate", java.security.spec.KeySpec::class.java)) { c, result -> afterGenerate(c, result, CryptoOperation.GENERATE_PRIVATE) }
        hook(f.find(KF, "generatePublic", java.security.spec.KeySpec::class.java)) { c, result -> afterGenerate(c, result, CryptoOperation.GENERATE_PUBLIC) }

        hook(f.find(SKF, "getInstance", String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(SKF, "getInstance", String::class.java, String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(SKF, "getInstance", String::class.java, Provider::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(SKF, "generateSecret", java.security.spec.KeySpec::class.java)) { c, result -> afterGenerate(c, result, CryptoOperation.GENERATE_SECRET) }
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

    private fun afterGenerate(chain: XposedInterface.Chain, result: Any?, operation: CryptoOperation) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val keyBytes = when (result) {
            is java.security.PrivateKey, is javax.crypto.SecretKey ->
                (result as? java.security.Key)?.encoded
            is java.security.PublicKey ->
                (result as java.security.PublicKey).encoded
            else -> null
        }
        emit(
            operation = operation,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
            key = BytesView.of(keyBytes),
        )
    }

    companion object {
        private const val KF = "java.security.KeyFactory"
        private const val SKF = "javax.crypto.SecretKeyFactory"
    }
}