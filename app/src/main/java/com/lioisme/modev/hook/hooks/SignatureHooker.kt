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
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate

/**
 * 数字签名捕获(RSA / ECDSA 的 sign / verify)。
 */
class SignatureHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.SIGNATURE) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(SIG, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SIG, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SIG, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SIG, "initSign", PrivateKey::class.java)) { inv -> afterInitSign(inv) }
        hook(f.find(SIG, "initSign", PrivateKey::class.java, SecureRandom::class.java)) { inv -> afterInitSign(inv) }
        hook(f.find(SIG, "initVerify", PublicKey::class.java)) { inv -> afterInitVerify(inv) }
        hook(f.find(SIG, "initVerify", Certificate::class.java)) { inv -> afterInitVerify(inv) }
        hook(f.find(SIG, "update", ByteArray::class.java)) { inv -> afterUpdate(inv) }
        hook(f.find(SIG, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv -> afterUpdate(inv) }
        hook(f.find(SIG, "update", java.nio.ByteBuffer::class.java)) { inv -> afterUpdate(inv) }
        hook(f.find(SIG, "sign")) { inv, result -> afterSign(inv, result) }
        hook(f.find(SIG, "sign", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterSign(inv, result) }
        hook(f.find(SIG, "verify", ByteArray::class.java)) { inv, result -> afterVerify(inv, result) }
        hook(f.find(SIG, "verify", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterVerify(inv, result) }
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

    private fun afterInitSign(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        CipherHooker.extractKey(invocation.getArg(0) as? PrivateKey, ctx)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
        )
    }

    private fun afterInitVerify(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val key = invocation.getArg(0) as? PublicKey
        CipherHooker.extractKey(key, ctx)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
        )
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

    private fun afterSign(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val output = result as? ByteArray
        emit(
            operation = CryptoOperation.SIGN,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            output = BytesView.of(output),
        )
    }

    private fun afterVerify(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val signature = invocation.getArg(0) as? ByteArray
        ctx.verified = result == true
        emit(
            operation = CryptoOperation.VERIFY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = BytesView.of(signature),
        )
    }

    companion object {
        private const val SIG = "java.security.Signature"
    }
}