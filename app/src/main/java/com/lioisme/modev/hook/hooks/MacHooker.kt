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
import java.security.Key
import java.security.Provider
import java.security.spec.AlgorithmParameterSpec

/**
 * 带密钥哈希捕获(HMAC-MD5 / HMAC-SHA1 / HMAC-SHA256 等)。
 */
class MacHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.MAC) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(MAC, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(MAC, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(MAC, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(MAC, "init", Key::class.java)) { inv -> afterInit(inv) }
        hook(f.find(MAC, "init", Key::class.java, AlgorithmParameterSpec::class.java)) { inv -> afterInit(inv) }
        hook(f.find(MAC, "update", ByteArray::class.java)) { inv -> afterUpdate(inv) }
        hook(f.find(MAC, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv -> afterUpdate(inv) }
        hook(f.find(MAC, "update", java.nio.ByteBuffer::class.java)) { inv -> afterUpdate(inv) }
        hook(f.find(MAC, "doFinal")) { inv, result -> afterDoFinal(inv, result) }
        hook(f.find(MAC, "doFinal", ByteArray::class.java)) { inv, result -> afterDoFinal(inv, result) }
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

    private fun afterInit(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        CipherHooker.extractKey(invocation.getArg(0) as? Key, ctx)
        if (enabled()) {
            emit(
                operation = CryptoOperation.INIT,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(instance),
                context = ctx,
            )
        }
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

    private fun afterDoFinal(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val input = invocation.getArg(0) as? ByteArray
        val output = result as? ByteArray
        emit(
            operation = CryptoOperation.DO_FINAL,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = BytesView.of(input),
            output = BytesView.of(output),
        )
    }

    companion object {
        private const val MAC = "javax.crypto.Mac"
    }
}