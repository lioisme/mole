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
import java.security.Key
import java.security.Provider
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

/**
 * Cipher 加解密捕获(AES / DES / RSA / SM4 等)。
 *
 * 关联链路:getInstance(算法/provider) → init(before 采集入参 → after 产出记录并自动补采随机 IV)
 * → update/updateAAD/doFinal(before 暂存 ByteBuffer 入参,after 捕获输出,含 int 返回型出参缓冲)。
 */
class CipherHooker(
    engine: HookEngine,
    config: () -> HookSwitch,
    tracker: CryptoContextTracker,
    sink: RecordSink,
    packageName: String,
) : BaseHooker(engine, config, tracker, sink, packageName, CryptoKind.CIPHER) {

    override fun install() {
        val f = MethodFinder
        // 实例化(算法 + provider)
        hookInstanceOf(f.find(CIPHER, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookInstanceOf(f.find(CIPHER, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookInstanceOf(f.find(CIPHER, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }

        // init:before 采集入参,after 产出记录 + 自动补采随机 IV
        val initOverloads = listOfNotNull(
            f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java),
            f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java, AlgorithmParameterSpec::class.java),
            f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java, SecureRandom::class.java),
            f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java, AlgorithmParameterSpec::class.java, SecureRandom::class.java),
        )
        for (m in initOverloads) {
            hookBefore(m) { inv, ctx -> beforeInit(inv, ctx) }
            hookAfter(m) { inv, ctx, _ -> afterInit(inv, ctx) }
        }

        // update:byte[] 在 after 直接读取;ByteBuffer 需 before 暂存
        hookAfter(f.find(CIPHER, "update", ByteArray::class.java)) { inv, ctx, _ -> afterUpdate(inv, ctx, null) }
        hookAfter(f.find(CIPHER, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, ctx, _ ->
            afterUpdate(inv, ctx, null)
        }
        hookBefore(f.find(CIPHER, "update", ByteBuffer::class.java)) { inv, _ -> HookerSupport.stash(inv.getArg(0)) }
        hookAfter(f.find(CIPHER, "update", ByteBuffer::class.java)) { inv, ctx, result -> afterUpdate(inv, ctx, result) }

        // updateAAD
        hookBefore(f.find(CIPHER, "updateAAD", ByteArray::class.java)) { inv, ctx -> beforeUpdateAad(inv, ctx) }
        hookBefore(f.find(CIPHER, "updateAAD", ByteBuffer::class.java)) { inv, ctx -> beforeUpdateAad(inv, ctx) }

        // doFinal:全部重载,含 int 返回型出参缓冲与 ByteBuffer 入参
        hookAfter(f.find(CIPHER, "doFinal")) { inv, ctx, result -> afterDoFinal(inv, ctx, null, result) }
        hookAfter(f.find(CIPHER, "doFinal", ByteArray::class.java)) { inv, ctx, result -> afterDoFinal(inv, ctx, null, result) }
        hookAfter(f.find(CIPHER, "doFinal", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, ctx, result -> afterDoFinal(inv, ctx, null, result) }
        hookBefore(f.find(CIPHER, "doFinal", ByteBuffer::class.java)) { inv, _ -> HookerSupport.stash(inv.getArg(0)) }
        hookAfter(f.find(CIPHER, "doFinal", ByteBuffer::class.java)) { inv, ctx, result -> afterDoFinal(inv, ctx, null, result) }
        hookBefore(f.find(CIPHER, "doFinal", ByteBuffer::class.java, ByteBuffer::class.java)) { inv, _ -> HookerSupport.stash(inv.getArg(0)) }
        hookAfter(f.find(CIPHER, "doFinal", ByteBuffer::class.java, ByteBuffer::class.java)) { inv, ctx, result -> afterDoFinal(inv, ctx, inv.getArg(1), result) }

        // 动态 IV
        hookAfter(f.find(CIPHER, "getIV")) { inv, ctx, result -> afterGetIv(inv, ctx, result) }

        // clone:上下文传播
        hookInstanceOf(f.find(CIPHER, "clone")) { inv, result -> afterClone(inv, result) }
    }

    private fun afterClone(invocation: Invocation, result: Any?) {
        val src = invocation.thisObject ?: return
        val dst = result ?: return
        tracker.cloneContext(src, dst)
    }

    private fun hookBefore(
        method: java.lang.reflect.Method?,
        before: (Invocation, CryptoContext) -> Unit,
    ) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun before(invocation: Invocation) {
                val instance = invocation.thisObject ?: return
                if (enabled()) {
                    before(invocation, tracker.contextFor(instance))
                }
            }
        })
    }

    private fun hookAfter(
        method: java.lang.reflect.Method?,
        after: (Invocation, CryptoContext, Any?) -> Unit,
    ) {
        if (method == null) return
        engine.hook(method, object : MethodHook() {
            override fun after(invocation: Invocation) {
                val instance = invocation.thisObject ?: return
                if (enabled()) {
                    after(invocation, tracker.contextFor(instance), invocation.result)
                }
            }
        })
    }

    private fun hookInstanceOf(
        method: java.lang.reflect.Method?,
        after: (Invocation, Any?) -> Unit,
    ) {
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

    private fun beforeInit(invocation: Invocation, ctx: CryptoContext) {
        ctx.direction = invocation.getArg(0) as? Int
        extractKey(invocation.getArg(1) as? Key, ctx, ::viewOf)
        extractIv(invocation.getArg(2) as? AlgorithmParameterSpec, ctx, ::viewOf)
        ctx.salt = null
        ctx.aad = null
        ctx.aadData = null
        ctx.gcmTagLen = null
        ctx.iterationCount = null
        ctx.keyLength = null
        ctx.keysize = null
    }

    private fun afterInit(invocation: Invocation, ctx: CryptoContext) {
        val instance = invocation.thisObject ?: return
        // CBC/GCM 无显式 spec 时,init 后由实现自动生成随机 IV,主动补采
        if (ctx.iv == null && ctx.direction == Cipher.ENCRYPT_MODE) {
            val iv = runCatching { (instance as Cipher).iv }.getOrNull()
            if (iv != null && iv.isNotEmpty()) {
                ctx.iv = viewOf(iv)
                ctx.lastInitIv = ctx.iv
            }
        }
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
        )
    }

    private fun afterUpdate(invocation: Invocation, ctx: CryptoContext, result: Any?) {
        val instance = invocation.thisObject ?: return
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

    private fun beforeUpdateAad(invocation: Invocation, ctx: CryptoContext) {
        val instance = invocation.thisObject ?: return
        val aad = HookerSupport.bufferInput(invocation.getArg(0)) ?: return
        ctx.aadData = (ctx.aadData ?: ByteArray(0)) + aad
        ctx.aad = viewOf(ctx.aadData)
        emit(
            operation = CryptoOperation.UPDATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            aad = ctx.aad,
        )
    }

    private fun afterDoFinal(invocation: Invocation, ctx: CryptoContext, outArg: Any?, result: Any?) {
        val instance = invocation.thisObject ?: return
        val input = when (val arg = invocation.getArg(0)) {
            is ByteArray -> {
                val off = invocation.getArg(1) as? Int
                val len = invocation.getArg(2) as? Int
                if (off != null && len != null) HookerSupport.slice(arg, off, len) else arg
            }
            else -> HookerSupport.take()
        }
        ctx.inputTotal += input?.size ?: 0
        // int 返回型:结果写入出参缓冲 arg0[off, off+result)
        val output = when {
            result is ByteArray -> result
            result is ByteBuffer -> HookerSupport.bufferRemaining(result)
            result is Int && invocation.getArg(0) is ByteArray -> {
                HookerSupport.slice(invocation.getArg(0) as ByteArray, invocation.getArg(1) as? Int, result)
            }
            outArg is ByteBuffer -> HookerSupport.bufferRemaining(outArg)
            else -> null
        }
        ctx.outputTotal += output?.size ?: 0
        emit(
            operation = CryptoOperation.DO_FINAL,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = viewOf(input),
            output = viewOf(output),
        )
    }

    private fun afterGetIv(invocation: Invocation, ctx: CryptoContext, result: Any?) {
        val instance = invocation.thisObject ?: return
        val iv = result as? ByteArray
        ctx.iv = viewOf(iv)
        if (iv != null) {
            emit(
                operation = CryptoOperation.GET_IV,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(instance),
                context = ctx,
                iv = ctx.iv,
            )
        }
    }

    companion object {
        private const val CIPHER = "javax.crypto.Cipher"

        /**
         * 提取密钥:优先 getEncoded();AndroidKeyStore 硬件密钥返回 null 时标记不可导出并记录别名。
         */
        internal fun extractKey(key: Key?, ctx: CryptoContext, toView: (ByteArray?) -> BytesView? = { BytesView.of(it) }) {
            if (key == null) {
                ctx.key = null
                return
            }
            val encoded = runCatching { key.encoded }.getOrNull()
            if (encoded != null && encoded.isNotEmpty()) {
                ctx.key = toView(encoded)
                ctx.keyAlias = null
                ctx.keyExportable = true
            } else {
                ctx.key = null
                ctx.keyExportable = false
                ctx.keyAlias = runCatching {
                    key.javaClass.getMethod("getAlias").invoke(key) as? String
                }.getOrNull()
            }
        }

        internal fun extractIv(spec: AlgorithmParameterSpec?, ctx: CryptoContext, toView: (ByteArray?) -> BytesView? = { BytesView.of(it) }) {
            when (spec) {
                is IvParameterSpec -> ctx.iv = toView(spec.iv)
                is GCMParameterSpec -> {
                    ctx.iv = toView(spec.iv)
                    ctx.gcmTagLen = spec.tLen
                }
                else -> Unit
            }
        }
    }
}