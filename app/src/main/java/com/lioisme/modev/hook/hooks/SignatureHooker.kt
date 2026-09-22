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
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec

/**
 * 数字签名捕获(RSA / ECDSA 的 sign / verify)。
 * 含 initVerify(Certificate) 公钥提取、int 返回型 sign(byte[],int,int) 出参缓冲回读。
 */
class SignatureHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.SIGNATURE) {

    override fun install() {
        val f = MethodFinder
        hookAfter(f.find(SIG, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(SIG, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(SIG, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookAfter(f.find(SIG, "initSign", PrivateKey::class.java)) { inv, _ -> afterInitSign(inv) }
        hookAfter(f.find(SIG, "initSign", PrivateKey::class.java, SecureRandom::class.java)) { inv, _ -> afterInitSign(inv) }
        hookAfter(f.find(SIG, "initVerify", PublicKey::class.java)) { inv, _ -> afterInitVerify(inv) }
        hookAfter(f.find(SIG, "initVerify", PublicKey::class.java, SecureRandom::class.java)) { inv, _ -> afterInitVerify(inv) }
        hookAfter(f.find(SIG, "initVerify", Certificate::class.java)) { inv, _ -> afterInitVerify(inv) }
        hookAfter(f.find(SIG, "setParameter", AlgorithmParameterSpec::class.java)) { inv, _ -> afterSetParameter(inv) }
        hookAfter(f.find(SIG, "update", ByteArray::class.java)) { inv, _ -> afterUpdate(inv) }
        hookAfter(f.find(SIG, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, _ -> afterUpdate(inv) }
        hookBefore(f.find(SIG, "update", ByteBuffer::class.java)) { inv -> HookerSupport.stash(inv, inv.getArg(0)) }
        hookAfter(f.find(SIG, "update", ByteBuffer::class.java)) { inv, _ -> afterUpdate(inv) }
        hookAfter(f.find(SIG, "sign")) { inv, result -> afterSign(inv, result) }
        hookAfter(f.find(SIG, "sign", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterSign(inv, result) }
        hookAfter(f.find(SIG, "verify", ByteArray::class.java)) { inv, result -> afterVerify(inv, result) }
        hookAfter(f.find(SIG, "verify", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, result -> afterVerify(inv, result) }
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

    private fun afterInitSign(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        CipherHooker.extractKey(invocation.getArg(0) as? PrivateKey, ctx, ::viewOf)
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
        // Certificate 重载:公钥藏于证书内,必须显式提取
        val key = when (val arg = invocation.getArg(0)) {
            is PublicKey -> arg
            is Certificate -> runCatching { arg.publicKey }.getOrNull()
            else -> null
        }
        CipherHooker.extractKey(key, ctx, ::viewOf)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
        )
    }

    private fun afterSetParameter(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val spec = invocation.getArg(0) as? AlgorithmParameterSpec
        val params = mutableMapOf<String, String>()
        spec?.let {
            val cls = it.javaClass.name.substringAfterLast('.')
            params["specType"] = cls
            runCatching {
                val m = it.javaClass.getMethod("getDigestAlgorithm")
                params["digest"] = m.invoke(it)?.toString() ?: ""
            }
        }
        if (params.isNotEmpty()) {
            emit(
                operation = CryptoOperation.INIT,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(instance),
                context = ctx,
                params = params,
            )
        }
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

    private fun afterSign(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        // sign(byte[],int,int):result 为签名长度,签名落在 arg0[off, off+result)
        val output = when {
            result is ByteArray -> result
            result is Int && invocation.getArg(0) is ByteArray -> {
                HookerSupport.slice(invocation.getArg(0) as ByteArray, invocation.getArg(1) as? Int, result)
            }
            else -> null
        }
        emit(
            operation = CryptoOperation.SIGN,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            output = viewOf(output),
        )
    }

    private fun afterVerify(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val signature = when (val arg = invocation.getArg(0)) {
            is ByteArray -> {
                val off = invocation.getArg(1) as? Int
                val len = invocation.getArg(2) as? Int
                if (off != null && len != null) HookerSupport.slice(arg, off, len) else arg
            }
            else -> null
        }
        ctx.verified = result == true
        emit(
            operation = CryptoOperation.VERIFY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = viewOf(signature),
        )
    }

    companion object {
        private const val SIG = "java.security.Signature"
    }
}
