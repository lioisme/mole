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
import com.lioisme.modev.hook.RecordSink
import java.nio.ByteBuffer
import java.security.Provider

/**
 * 摘要算法捕获(MD5 / SHA-1 / SHA-256 / SM3 等)。
 * ByteBuffer 入参在 before 阶段暂存;int 返回型 digest(byte[],int,int) 出参缓冲在 after 回读。
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
        hookAfter(f.find(DIGEST, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(DIGEST, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(DIGEST, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(DIGEST, "update", ByteArray::class.java)) { inv, _ -> afterUpdate(inv) }
        hookAfter(f.find(DIGEST, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, _ -> afterUpdate(inv) }
        hookBefore(f.find(DIGEST, "update", ByteBuffer::class.java)) { inv -> HookerSupport.stash(inv.getArg(0)) }
        hookAfter(f.find(DIGEST, "update", ByteBuffer::class.java)) { inv, _ -> afterUpdate(inv) }
        hookAfter(f.find(DIGEST, "digest")) { inv, result -> afterDigest(inv, result) }
        hookAfter(f.find(DIGEST, "digest", ByteArray::class.java)) { inv, result -> afterDigest(inv, result) }
        hookAfter(f.find(DIGEST, "digest", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterDigest(inv, result) }
        hookBefore(f.find(DIGEST, "digest", ByteBuffer::class.java)) { inv -> HookerSupport.stash(inv.getArg(0)) }
        hookAfter(f.find(DIGEST, "digest", ByteBuffer::class.java)) { inv, result -> afterDigest(inv, result) }
        hookAfter(f.find(DIGEST, "clone")) { inv, result -> afterClone(inv, result) }
        hookAfter(f.find(DIGEST, "reset")) { inv, _ -> afterReset(inv) }
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

    private fun afterUpdate(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val input = when (val arg = invocation.getArg(0)) {
            is ByteArray -> {
                val off = invocation.getArg(1) as? Int
                val len = invocation.getArg(2) as? Int
                if (off != null && len != null) HookerSupport.slice(arg, off, len) else arg
            }
            else -> HookerSupport.take()
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

    private fun afterDigest(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val input = when (val arg = invocation.getArg(0)) {
            is ByteArray -> {
                val off = invocation.getArg(1) as? Int
                val len = invocation.getArg(2) as? Int
                if (off != null && len != null) HookerSupport.slice(arg, off, len) else arg
            }
            else -> HookerSupport.take()
        }
        ctx.inputTotal += input?.size ?: 0
        // digest(byte[],int,int):result 为写入字节数,摘要落在 arg0[off, off+result)
        val output = when {
            result is ByteArray -> result
            result is ByteBuffer -> HookerSupport.bufferRemaining(result)
            result is Int && invocation.getArg(0) is ByteArray -> {
                HookerSupport.slice(invocation.getArg(0) as ByteArray, invocation.getArg(1) as? Int, result)
            }
            else -> null
        }
        emit(
            operation = CryptoOperation.DIGEST,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = viewOf(input),
            output = viewOf(output),
        )
    }

    companion object {
        private const val DIGEST = "java.security.MessageDigest"
    }
}