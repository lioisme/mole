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
        hook(f.find(MAC, "getInstance", String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(MAC, "getInstance", String::class.java, String::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(MAC, "getInstance", String::class.java, Provider::class.java)) { c, result -> afterGetInstance(c, result) }
        hook(f.find(MAC, "init", Key::class.java)) { c -> afterInit(c) }
        hook(f.find(MAC, "init", Key::class.java, AlgorithmParameterSpec::class.java)) { c -> afterInit(c) }
        hook(f.find(MAC, "update", ByteArray::class.java)) { c -> afterUpdate(c) }
        hook(f.find(MAC, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { c -> afterUpdate(c) }
        hook(f.find(MAC, "update", java.nio.ByteBuffer::class.java)) { c -> afterUpdate(c) }
        hook(f.find(MAC, "doFinal")) { c, result -> afterDoFinal(c, result) }
        hook(f.find(MAC, "doFinal", ByteArray::class.java)) { c, result -> afterDoFinal(c, result) }
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

    private fun afterInit(chain: XposedInterface.Chain) {
        val ctx = tracker.contextFor(chain.getThisObject())
        CipherHooker.extractKey(chain.getArg(0) as? Key, ctx)
        if (enabled()) {
            emit(
                operation = CryptoOperation.INIT,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(chain.getThisObject()),
                context = ctx,
            )
        }
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

    private fun afterDoFinal(chain: XposedInterface.Chain, result: Any?) {
        val ctx = tracker.contextFor(chain.getThisObject())
        val input = chain.getArgOrNull(0) as? ByteArray
        val output = result as? ByteArray
        emit(
            operation = CryptoOperation.DO_FINAL,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(chain.getThisObject()),
            context = ctx,
            input = BytesView.of(input),
            output = BytesView.of(output),
        )
    }

    companion object {
        private const val MAC = "javax.crypto.Mac"
    }
}