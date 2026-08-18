package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.HookerSupport
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.security.Key
import java.security.Provider

/**
 * 瀵嗛挜鍗忓晢鎹曡幏(KeyAgreement,ECDH/DH):
 * getInstance 鈫?init(宸辨柟瀵嗛挜)鈫?doPhase(瀵圭鍏挜,lastPhase)鈫?generateSecret(鍏变韩瀵嗛挜,鍚?int 杩斿洖鍨?銆? */
class KeyAgreementHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.KEY_AGREEMENT) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(KA, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KA, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KA, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KA, "init", Key::class.java)) { inv -> afterInit(inv) }
        hook(f.find(KA, "init", Key::class.java, java.security.spec.AlgorithmParameterSpec::class.java)) { inv -> afterInit(inv) }
        hook(f.find(KA, "doPhase", Key::class.java, Boolean::class.javaPrimitiveType)) { inv, result -> afterDoPhase(inv, result) }
        hook(f.find(KA, "generateSecret")) { inv, result -> afterGenerateSecret(inv, result) }
        hook(f.find(KA, "generateSecret", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterGenerateSecret(inv, result) }
        hook(f.find(KA, "generateSecret", String::class.java)) { inv, result -> afterGenerateSecret(inv, result) }
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

    private fun afterInit(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        CipherHooker.extractKey(invocation.getArg(0) as? Key, ctx, ::viewOf)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
        )
    }

    private fun afterDoPhase(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val peer = invocation.getArg(0) as? Key
        val peerBytes = runCatching { peer?.encoded }.getOrNull()
        ctx.publicKey = viewOf(peerBytes)
        emit(
            operation = CryptoOperation.DO_PHASE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            publicKey = ctx.publicKey,
            params = mapOf("lastPhase" to (invocation.getArg(1) == true).toString()),
        )
    }

    private fun afterGenerateSecret(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val output = when {
            result is ByteArray -> result
            result is javax.crypto.SecretKey -> runCatching { result.encoded }.getOrNull()
            result is Int && invocation.getArg(0) is ByteArray -> {
                HookerSupport.slice(invocation.getArg(0) as ByteArray, invocation.getArg(1) as? Int, result)
            }
            else -> null
        }
        emit(
            operation = CryptoOperation.GENERATE_SECRET,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            output = viewOf(output),
        )
    }

    companion object {
        private const val KA = "javax.crypto.KeyAgreement"
    }
}
