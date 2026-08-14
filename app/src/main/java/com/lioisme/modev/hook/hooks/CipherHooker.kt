package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.util.BytesView
import com.lioisme.modev.hook.CryptoContext
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.RecordSink
import java.security.Key
import java.security.Provider
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

/**
 * Cipher 加解密捕获(AES / DES / RSA / SM4 等)。
 *
 * 关联链路:getInstance(记录算法) → init(提取密钥/IV/方向) → update/doFinal(明密文)
 * → getIV(动态 IV,如 GCM/CBC 随机 IV)。
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
        // 实例化
        hookInstanceOf(f.find(CIPHER, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookInstanceOf(f.find(CIPHER, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hookInstanceOf(f.find(CIPHER, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        // 初始化(在 proceed 前采集,参数形态安全)
        hookBefore(f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java)) { inv, ctx -> beforeInit(inv, ctx) }
        hookBefore(f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java, AlgorithmParameterSpec::class.java)) { inv, ctx -> beforeInit(inv, ctx) }
        hookBefore(f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java, java.security.SecureRandom::class.java)) { inv, ctx -> beforeInit(inv, ctx) }
        hookBefore(f.find(CIPHER, "init", Int::class.javaPrimitiveType, Key::class.java, AlgorithmParameterSpec::class.java, java.security.SecureRandom::class.java)) { inv, ctx -> beforeInit(inv, ctx) }
        // 分块更新
        hookAfter(f.find(CIPHER, "update", ByteArray::class.java)) { inv, ctx, _ -> afterUpdate(inv, ctx) }
        hookAfter(f.find(CIPHER, "update", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, ctx, _ -> afterUpdate(inv, ctx) }
        hookAfter(f.find(CIPHER, "update", java.nio.ByteBuffer::class.java)) { inv, ctx, _ -> afterUpdate(inv, ctx) }
        // 收尾
        hookAfter(f.find(CIPHER, "doFinal")) { inv, ctx, result -> afterDoFinal(inv, ctx, result) }
        hookAfter(f.find(CIPHER, "doFinal", ByteArray::class.java)) { inv, ctx, result -> afterDoFinal(inv, ctx, result) }
        hookAfter(f.find(CIPHER, "doFinal", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)) { inv, ctx, result -> afterDoFinal(inv, ctx, result) }
        hookAfter(f.find(CIPHER, "doFinal", java.nio.ByteBuffer::class.java)) { inv, ctx, result -> afterDoFinal(inv, ctx, result) }
        // 动态 IV
        hookAfter(f.find(CIPHER, "getIV")) { inv, ctx, result -> afterGetIv(inv, ctx, result) }
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
        ctx.provider = providerOf(invocation)
    }

    private fun beforeInit(invocation: Invocation, ctx: CryptoContext) {
        val instance = invocation.thisObject ?: return
        ctx.direction = invocation.getArg(0) as? Int
        extractKey(invocation.getArg(1) as? Key, ctx)
        extractIv(invocation.getArg(2) as? AlgorithmParameterSpec, ctx)
        emit(
            operation = CryptoOperation.INIT,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
        )
    }

    private fun afterUpdate(invocation: Invocation, ctx: CryptoContext) {
        val instance = invocation.thisObject ?: return
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

    private fun afterDoFinal(invocation: Invocation, ctx: CryptoContext, result: Any?) {
        val instance = invocation.thisObject ?: return
        val arg = invocation.getArg(0)
        val input = when (arg) {
            is ByteArray -> arg
            is java.nio.ByteBuffer -> {
                val b = arg
                val a = ByteArray(b.remaining())
                b.duplicate().get(a)
                a
            }
            else -> null
        }
        ctx.inputTotal += input?.size ?: 0
        val output = result as? ByteArray
        ctx.outputTotal += output?.size ?: 0
        emit(
            operation = CryptoOperation.DO_FINAL,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            input = BytesView.of(input),
            output = BytesView.of(output),
        )
    }

    private fun afterGetIv(invocation: Invocation, ctx: CryptoContext, result: Any?) {
        val instance = invocation.thisObject ?: return
        val iv = result as? ByteArray
        ctx.iv = BytesView.of(iv)
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

    private fun providerOf(invocation: Invocation): String? {
        return when (val p = invocation.getArg(1)) {
            is String -> p
            is Provider -> p.name
            else -> null
        }
    }

    companion object {
        private const val CIPHER = "javax.crypto.Cipher"

        /**
         * 提取密钥:优先 getEncoded();AndroidKeyStore 硬件密钥返回 null 时标记不可导出并记录别名。
         */
        internal fun extractKey(key: Key?, ctx: CryptoContext) {
            if (key == null) {
                ctx.key = null
                return
            }
            val encoded = runCatching { key.encoded }.getOrNull()
            if (encoded != null && encoded.isNotEmpty()) {
                ctx.key = BytesView.of(encoded)
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

        internal fun extractIv(spec: AlgorithmParameterSpec?, ctx: CryptoContext) {
            val iv: ByteArray? = when (spec) {
                is IvParameterSpec -> spec.iv
                is GCMParameterSpec -> spec.iv
                else -> null
            }
            if (iv != null) {
                ctx.iv = BytesView.of(iv)
            }
        }
    }
}