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
        hook(f.find(DIGEST, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(DIGEST, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(DIGEST, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(DIGEST, "update", ByteArray::class.java)) { inv -> afterUpdate(inv) }
        hook(f.find(DIGEST, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv -> afterUpdate(inv) }
        hook(f.find(DIGEST, "update", java.nio.ByteBuffer::class.java)) { inv -> afterUpdate(inv) }
        hook(f.find(DIGEST, "digest")) { inv, result -> afterDigest(inv, result) }
        hook(f.find(DIGEST, "digest", ByteArray::class.java)) { inv, result -> afterDigest(inv, result) }
        hook(f.find(DIGEST, "digest", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterDigest(inv, result) }
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

    private fun afterUpdate(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val input = invocation.getArg(0) as? ByteArray
        val n = input?.size ?: 0
        ctx.inputTotal += n
        if (n > 0) {
            emit(
                operation = CryptoOperation.UPDATE,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(instance),
                context = ctx,
                input = BytesView.of(input),
            )
        }
    }

    private fun afterDigest(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val input = invocation.getArg(0) as? ByteArray
        val output = result as? ByteArray
        emit(
            operation = CryptoOperation.DIGEST,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = BytesView.of(input),
            output = BytesView.of(output),
        )
    }

    companion object {
        private const val DIGEST = "java.security.MessageDigest"
    }
}