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
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.security.Provider

/**
 * 闅忔満鏁版崟鑾?SecureRandom):绠楁硶/provider銆乻etSeed 绉嶅瓙銆乶extBytes 杈撳嚭銆乬enerateSeed銆? * 闅忔満婧愭槸 IV/瀵嗛挜/鐩愮殑鐢熸垚婧愬ご,瀵瑰畾浣?璋佸湪鐢熸垚闅忔満 IV"鏈夎緟鍔╀环鍊笺€? */
class SecureRandomHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.RANDOM) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(SR, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SR, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SR, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SR, "setSeed", ByteArray::class.java)) { inv -> afterSetSeed(inv) }
        hook(f.find(SR, "nextBytes", ByteArray::class.java)) { inv -> afterNextBytes(inv) }
        hook(f.find(SR, "generateSeed", Int::class.javaPrimitiveType)) { inv, result -> afterGenerateSeed(inv, result) }
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

    private fun afterSetSeed(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = (invocation.getArg(0) as? ByteArray)?.let { viewOf(it) },
        )
    }

    private fun afterNextBytes(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val out = invocation.getArg(0) as? ByteArray
        emit(
            operation = CryptoOperation.GENERATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            output = viewOf(out),
        )
    }

    private fun afterGenerateSeed(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        emit(
            operation = CryptoOperation.GENERATE_SEED,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            output = (result as? ByteArray)?.let { viewOf(it) },
        )
    }

    companion object {
        private const val SR = "java.security.SecureRandom"
    }
}
