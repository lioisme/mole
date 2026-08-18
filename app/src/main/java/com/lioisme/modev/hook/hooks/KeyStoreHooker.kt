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
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.io.InputStream
import java.security.Key
import java.security.KeyStore
import java.security.Provider
import java.security.cert.Certificate

/**
 * 瀵嗛挜搴撴崟鑾?KeyStore):绫诲瀷/provider銆乴oad 鍙ｄ护銆佸埆鍚嶆煡璇€佸彇閿?鍐欓敭/鍒犻敭銆佽瘉涔﹂摼銆? * 鍙ｄ护(char[])涓庤繑鍥炲瘑閽ョ収甯告崟鑾?璋冭瘯宸ュ叿瀹氫綅,涓庡弬鑰冭蒋浠朵竴鑷?;涓嶅彲瀵煎嚭瀵嗛挜璁?alias + 鏍囪銆? */
class KeyStoreHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.KEY_STORE) {

    override fun install() {
        val f = MethodFinder
        hook(f.find(KS, "getInstance", String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KS, "getInstance", String::class.java, String::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KS, "getInstance", String::class.java, Provider::class.java)) { inv, result -> afterGetInstance(inv, result) }
        hook(f.find(KS, "load", InputStream::class.java, CharArray::class.java)) { inv -> afterLoad(inv) }
        hook(f.find(KS, "load", KeyStore.LoadStoreParameter::class.java)) { inv -> afterLoadParameter(inv) }
        hook(f.find(KS, "aliases")) { inv, result -> afterAliases(inv, result) }
        hook(f.find(KS, "containsAlias", String::class.java)) { inv, result -> afterContainsAlias(inv, result) }
        hook(f.find(KS, "getKey", String::class.java, CharArray::class.java)) { inv, result -> afterGetKey(inv, result) }
        hook(f.find(KS, "setKeyEntry", String::class.java, Key::class.java, CharArray::class.java, Array<Certificate>::class.java)) { inv -> afterSetKey(inv) }
        hook(f.find(KS, "setCertificateEntry", String::class.java, Certificate::class.java)) { inv -> afterSetCertificate(inv) }
        hook(f.find(KS, "deleteEntry", String::class.java)) { inv -> afterDelete(inv) }
        hook(f.find(KS, "getCertificateChain", String::class.java)) { inv, result -> afterGetChain(inv, result) }
        hook(f.find(KS, "getEntry", String::class.java, KeyStore.ProtectionParameter::class.java)) { inv, result -> afterGetEntry(inv, result) }
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

    private fun afterLoad(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        emit(
            operation = CryptoOperation.LOAD,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = chars(invocation.getArg(1)),
        )
    }

    private fun afterLoadParameter(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        emit(
            operation = CryptoOperation.LOAD,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
        )
    }

    private fun afterAliases(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val aliases = (result as? java.util.Enumeration<*>)?.toList()?.joinToString(",")
        if (aliases.isNullOrEmpty()) return
        emit(
            operation = CryptoOperation.GET_KEY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            params = mapOf("aliases" to aliases),
        )
    }

    private fun afterContainsAlias(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val alias = invocation.getArg(0) as? String ?: return
        emit(
            operation = CryptoOperation.GET_KEY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            params = mapOf(
                "alias" to alias,
                "contains" to (result == true).toString(),
            ),
        )
    }

    private fun afterGetKey(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val alias = invocation.getArg(0) as? String ?: return
        val password = chars(invocation.getArg(1))
        emitKeyResult(invocation, ctx, alias, password, result as? Key)
    }

    private fun afterGetEntry(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val alias = invocation.getArg(0) as? String ?: return
        val password = (invocation.getArg(1) as? KeyStore.PasswordProtection)
            ?.let { runCatching { it.password }.getOrNull() }
            ?.let { charsOf(it) }
        val key = when (result) {
            is KeyStore.SecretKeyEntry -> result.secretKey
            is KeyStore.PrivateKeyEntry -> result.privateKey
            else -> runCatching {
                result?.javaClass?.getMethod("getCertificate")?.invoke(result) as? Certificate
            }.getOrNull()?.publicKey
        }
        emitKeyResult(instance, ctx, alias, password, key)
    }

    /** 鍙栭敭缁撴灉缁熶竴缁勮:瀵煎嚭瀵嗛挜鍙?encoded;涓嶅彲瀵煎嚭璁?alias + 鏍囪銆?*/
    private fun emitKeyResult(
        instance: Any,
        ctx: CryptoContext,
        alias: String,
        password: BytesView?,
        key: Key?,
    ) {
        val encoded = runCatching { key?.encoded }.getOrNull()
        emit(
            operation = CryptoOperation.GET_KEY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = viewOf(encoded),
            keyAlias = if (encoded != null) null else alias,
            keyExportable = encoded != null,
            params = mapOf("alias" to alias),
        )
        // 鍙ｄ护鍗曠嫭鎴愭潯(涓庡瘑閽ユ潗鏂欏垎绂?渚夸簬瀵圭収)
        if (password != null) {
            emit(
                operation = CryptoOperation.GET_KEY,
                algorithm = ctx.algorithm,
                contextId = CryptoContextTracker.identity(instance),
                context = ctx,
                key = password,
                params = mapOf("alias" to alias, "field" to "password"),
            )
        }
    }

    private fun afterSetKey(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val alias = invocation.getArg(0) as? String ?: return
        val key = invocation.getArg(1) as? Key
        val encoded = runCatching { key?.encoded }.getOrNull()
        emit(
            operation = CryptoOperation.SET_KEY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            key = viewOf(encoded),
            keyAlias = if (encoded != null) null else alias,
            keyExportable = encoded != null,
            params = mapOf("alias" to alias),
        )
    }

    private fun afterSetCertificate(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val alias = invocation.getArg(0) as? String ?: return
        emit(
            operation = CryptoOperation.SET_KEY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            publicKey = (invocation.getArg(1) as? Certificate)?.let { viewOf(runCatching { it.encoded }.getOrNull()) },
            params = mapOf("alias" to alias, "entryType" to "certificate"),
        )
    }

    private fun afterDelete(invocation: Invocation) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val alias = invocation.getArg(0) as? String ?: return
        emit(
            operation = CryptoOperation.DELETE_KEY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            params = mapOf("alias" to alias),
        )
    }

    private fun afterGetChain(invocation: Invocation, result: Any?) {
        val instance = invocation.thisObject ?: return
        val ctx = tracker.contextFor(instance)
        val alias = invocation.getArg(0) as? String ?: return
        val chain = result as? Array<Certificate>
        if (chain.isNullOrEmpty()) return
        emit(
            operation = CryptoOperation.GET_KEY,
            algorithm = ctx.algorithm,
            contextId = CryptoContextTracker.identity(instance),
            context = ctx,
            publicKey = viewOf(runCatching { chain.first().encoded }.getOrNull()),
            params = mapOf("alias" to alias, "chainLength" to chain.size.toString()),
        )
    }

    private fun chars(arg: Any?): BytesView? {
        val chars = arg as? CharArray ?: return null
        return charsOf(chars)
    }

    private fun charsOf(chars: CharArray): BytesView =
        viewOf(String(chars).toByteArray(Charsets.UTF_8))!!

    companion object {
        private const val KS = "java.security.KeyStore"
    }
}
