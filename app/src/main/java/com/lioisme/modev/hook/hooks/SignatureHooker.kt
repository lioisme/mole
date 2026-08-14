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
        hook(f.find(SIG, "getInstance", String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(SIG, "getInstance", String::class.java, String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(SIG, "getInstance", String::class.java, Provider::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(SIG, "initSign", PrivateKey::class.java)) { c -> afterInitSign(c) }
        hook(f.find(SIG, "initSign", PrivateKey::class.java, SecureRandom::class.java)) { c -> afterInitSign(c) }
        hook(f.find(SIG, "initVerify", PublicKey::class.java)) { c -> afterInitVerify(c) }
        hook(f.find(SIG, "initVerify", Certificate::class.java)) { c -> afterInitVerify(c) }
        hook(f.find(SIG, "update", ByteArray::class.java)) { c -> afterUpdate(c) }
        hook(f.find(SIG, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c -> afterUpdate(c) }
        hook(f.find(SIG, "update", java.nio.ByteBuffer::class.java)) { c -> afterUpdate(c) }
        hook(f.find(SIG, "sign")) { c, result -> afterSign(c, result) }
        hook(f.find(SIG, "sign", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c, result -> afterSign(c, result) }
        hook(f.find(SIG, "verify", ByteArray::class.java)) { c, result -> afterVerify(c, result) }
        hook(f.find(SIG, "verify", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c, result -> afterVerify(c, result) }
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

    private fun afterInitSign(chain: XposedInterface.Chain) {
        val ctx = tracker.contextFor(chain.getThisObject())
        CipherHooker.extractKey(chain.getArg(0) as? PrivateKey, ctx)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
        )
    }

    private fun afterInitVerify(chain: XposedInterface.Chain) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val key = chain.getArg(0) as? PublicKey
        CipherHooker.extractKey(key, ctx)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
        )
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

    private fun afterSign(chain: XposedInterface.Chain, result: Any?) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val output = result as? ByteArray
        emit(
            operation = CryptoOperation.SIGN,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
            output = BytesView.of(output),
        )
    }

    private fun afterVerify(chain: XposedInterface.Chain, result: Any?) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val signature = chain.getArgOrNull(0) as? ByteArray
        ctx.verified = result == true
        emit(
            operation = CryptoOperation.VERIFY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
            input = BytesView.of(signature),
        )
    }

    companion object {
        private const val SIG = "java.security.Signature"
    }
}