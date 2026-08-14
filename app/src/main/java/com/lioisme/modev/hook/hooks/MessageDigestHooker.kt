package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.RecordSink
import io.github.libxposed.api.XposedInterface
import java.security.Provider
import java.security.MessageDigest

/**
 * 摘要算法捕获(MD5 / SHA-1 / SHA-256 / SM3 等)。
 */
class MessageDigestHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.MESSAGE_DIGEST) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(DIGEST, "getInstance", String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(DIGEST, "getInstance", String::class.java, String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(DIGEST, "getInstance", String::class.java, Provider::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(DIGEST, "update", ByteArray::class.java)) { c -> afterUpdate(c) }
        hook(f.find(DIGEST, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c -> afterUpdate(c) }
        hook(f.find(DIGEST, "update", java.nio.ByteBuffer::class.java)) { c -> afterUpdate(c) }
        hook(f.find(DIGEST, "digest")) { c, result -> afterDigest(c, result) }
        hook(f.find(DIGEST, "digest", ByteArray::class.java)) { c, result -> afterDigest(c, result) }
        hook(f.find(DIGEST, "digest", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c, result -> afterDigest(c, result) }
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

    private fun afterUpdate(chain: XposedInterface.Chain) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val input = chain.getArg(0) as? ByteArray
        val n = input?.size ?: 0
        ctx.inputTotal += n
        if (n > 0) {
            emit(
                operation = CryptoOperation.UPDATE,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(chain.getThisObject()),
                context = ctx,
                input = BytesView.of(input),
            )
        }
    }

    private fun afterDigest(chain: XposedInterface.Chain, result: Any?) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val input = chain.getArgOrNull(0) as? ByteArray
        val output = result as? ByteArray
        if (enabled()) {
            emit(
                operation = CryptoOperation.DIGEST,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(chain.getThisObject()),
                context = ctx,
                input = BytesView.of(input),
                output = BytesView.of(output),
            )
        }
    }

    companion object {
        private const val DIGEST = "java.security.MessageDigest"
    }
}