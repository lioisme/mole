package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.HookerSupport
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.nio.ByteBuffer
import java.security.Key
import java.security.Provider
import java.security.spec.AlgorithmParameterSpec

/**
 * 带密钥哈希捕获(HMAC-MD5 / HMAC-SHA1 / HMAC-SHA256 等)。
 * 含 int 返回型 doFinal(byte[],int) 出参缓冲回读与 ByteBuffer 入参暂存。
 */
class MacHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.MAC) {

    override fun install() {
        val f = MethodFinder
        hookAfter(f.find(MAC, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(MAC, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(MAC, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(MAC, "init", Key::class.java)) { inv, _ -> afterInit(inv) }
        hookAfter(f.find(MAC, "init", Key::class.java, AlgorithmParameterSpec::class.java)) { inv, _ -> afterInit(inv) }
        hookAfter(f.find(MAC, "update", ByteArray::class.java)) { inv, _ -> afterUpdate(inv) }
        hookAfter(f.find(MAC, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, _ -> afterUpdate(inv) }
        hookBefore(f.find(MAC, "update", ByteBuffer::class.java)) { inv -> HookerSupport.stash(inv, inv.getArg(0)) }
        hookAfter(f.find(MAC, "update", ByteBuffer::class.java)) { inv, _ -> afterUpdate(inv) }
        hookAfter(f.find(MAC, "doFinal")) { inv, result -> afterDoFinal(inv, result) }
        hookAfter(f.find(MAC, "doFinal", ByteArray::class.java)) { inv, result -> afterDoFinal(inv, result) }
        hookAfter(f.find(MAC, "doFinal", ByteArray::class.java, Int::class.javaPrimitiveType)) { inv, result -> afterDoFinal(inv, result) }
        hookAfter(f.find(MAC, "clone")) { inv, result -> afterClone(inv, result) }
        hookAfter(f.find(MAC, "reset")) { inv, _ -> afterReset(inv) }
    }

    private fun afterClone(invocation: Invocation, result: Any?) {
        val src = invocation.thisObject ?: return
        val dst = result ?: return
        tracker.cloneContext(src, dst)
    }

    private fun afterReset(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        tracker.reset(instance)
    }

    private fun hookBefore(method: java.lang.reflect.Method?, before: (Invocation) -> Unit) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                if (enabled()) {
                    before(invocation)
                }
            }
        })
    }

    private fun hookAfter(method: java.lang.reflect.Method?, after: (Invocation, Any?) -> Unit) {
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

    private fun afterUpdate(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val input = when (val arg = invocation.getArg(0)) {
            is ByteArray -> {
                val off = invocation.getArg(1) as? Int
                val len = invocation.getArg(2) as? Int
                if (off != null && len != null) HookerSupport.slice(arg, off, len) else arg
            }
            else -> HookerSupport.take(invocation)
        }
        ctx.inputTotal += input?.size ?: 0
        if (input != null) {
            emit(
                operation = CryptoOperation.UPDATE,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(instance),
                context = ctx,
                input = viewOf(input),
            )
        }
    }

    private fun afterDoFinal(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val input = when (val arg = invocation.getArg(0)) {
            is ByteArray -> arg
            else -> HookerSupport.take(invocation)
        }
        ctx.inputTotal += input?.size ?: 0
        // doFinal(byte[],int):result 为写入字节数,MAC 落在 arg0[0, result)
        val output = when {
            result is ByteArray -> result
            result is Int && invocation.getArg(0) is ByteArray -> {
                HookerSupport.slice(invocation.getArg(0) as ByteArray, 0, result)
            }
            else -> null
        }
        emit(
            operation = CryptoOperation.DO_FINAL,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = viewOf(input),
            output = viewOf(output),
        )
    }

    companion object {
        private const val MAC = "javax.crypto.Mac"
    }
}
