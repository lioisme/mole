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
        hook(f.find(KF, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KF, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KF, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KF, "generatePrivate", java.security.spec.KeySpec::class.java)) { inv, result -> afterGenerate(inv, result, CryptoOperation.GENERATE_PRIVATE) }
        hook(f.find(KF, "generatePublic", java.security.spec.KeySpec::class.java)) { inv, result -> afterGenerate(inv, result, CryptoOperation.GENERATE_PUBLIC) }

        hook(f.find(SKF, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SKF, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SKF, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SKF, "generateSecret", java.security.spec.KeySpec::class.java)) { inv, result -> afterGenerate(inv, result, CryptoOperation.GENERATE_SECRET) }
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
        tracker.contextFor(target).algorithm = invocation.getArg(0) as? String ?: ""
    }

    private fun afterGenerate(invocation: Invocation, result: Any?, operation: CryptoOperation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
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
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = BytesView.of(keyBytes),
        )
    }

    companion object {
        private const val KF = "java.security.KeyFactory"
        private const val SKF = "javax.crypto.SecretKeyFactory"
    }
}