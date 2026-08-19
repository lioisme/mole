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
import java.security.Key
import java.security.Provider
import java.security.spec.KeySpec

/**
 * 密钥还原捕获:KeyFactory.generatePrivate/Public/getKeySpec/translateKey
 * 与 SecretKeyFactory.generateSecret。
 */
class KeyFactoryHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.KEY_FACTORY) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(KF, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KF, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KF, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KF, "generatePrivate", KeySpec::class.java)) { inv, result -> afterGenerate(inv, result, CryptoOperation.GENERATE_PRIVATE) }
        hook(f.find(KF, "generatePublic", KeySpec::class.java)) { inv, result -> afterGenerate(inv, result, CryptoOperation.GENERATE_PUBLIC) }
        hook(f.find(KF, "getKeySpec", Key::class.java, Class::class.java)) { inv, result -> afterGetKeySpec(inv, result) }
        hook(f.find(KF, "translateKey", Key::class.java)) { inv, result -> afterTranslateKey(inv, result) }

        hook(f.find(SKF, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SKF, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SKF, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(SKF, "generateSecret", KeySpec::class.java)) { inv, result -> afterGenerate(inv, result, CryptoOperation.GENERATE_SECRET) }
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

    private fun afterGenerate(invocation: Invocation, result: Any?, operation: CryptoOperation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val key = result as? Key
        val keyBytes = runCatching { key?.encoded }.getOrNull()
        val params = KeyParamExtractor.paramsOf(key)
        emit(
            operation = operation,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = viewOf(keyBytes),
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    /** getKeySpec(Key, Class):输入密钥材料 + 还原出的 KeySpec 结构参数。 */
    private fun afterGetKeySpec(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val inputKey = invocation.getArg(0) as? Key
        val inputBytes = runCatching { inputKey?.encoded }.getOrNull()
        val specParams = (result as? KeySpec)?.let { SpecExtractor.extract(it).params }
        emit(
            operation = CryptoOperation.GENERATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = viewOf(inputBytes),
            params = specParams?.takeIf { it.isNotEmpty() },
        )
    }

    /** translateKey(Key):翻译后的密钥材料 + 结构参数。 */
    private fun afterTranslateKey(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val key = result as? Key
        val keyBytes = runCatching { key?.encoded }.getOrNull()
        val params = KeyParamExtractor.paramsOf(key)
        emit(
            operation = CryptoOperation.GENERATE,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = viewOf(keyBytes),
            params = params.takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        private const val KF = "java.security.KeyFactory"
        private const val SKF = "javax.crypto.SecretKeyFactory"
    }
}
