package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.ProbeContext
import java.security.MessageDigest

/**
 * 签名读取监控 Hooker(V33):hook PackageManager.getPackageInfo(String, int),
 * 检测 GET_SIGNATURES(0x40)/GET_SIGNING_CERTIFICATES(0x8000000) 标记,
 * 记录签名哈希(SHA-256)与 Base64 片段。
 */
class SignatureMonitorHooker(
    context: ProbeContext,
) : SystemBaseHooker(context) {

    private val pmRegistry = BehaviorMethodRegistry.forName("android.app.ApplicationPackageManager")

    override fun install() {
        val registry = pmRegistry
            ?: BehaviorMethodRegistry.forName("android.app.PackageManagerImpl")
            ?: return
        val methods = registry.named("getPackageInfo")
        if (methods.isEmpty()) return
        for (method in methods) {
            engine.hook(method, object : MethodHook() {
                override fun after(invocation: Invocation) {
                    // V43 T2:签名哈希/Base64 为每调用重成本,SIGNATURE kind 关闭必须首行短路
                    if (!config().systemEnabled(SystemKind.SIGNATURE)) return
                    val pkgName = invocation.getArg(0) as? String ?: return
                    val flags = (invocation.getArg(1) as? Number)?.toLong() ?: return
                    val hasSignatures = (flags and FLAG_GET_SIGNATURES != 0L) ||
                        (flags and FLAG_GET_SIGNING_CERTIFICATES != 0L)
                    if (!hasSignatures) return
                    // V50 孤儿整改:两个 flag 早已分别判定,但发射恒用 SIGNATURE_READ
                    // ⇒ SIGNATURE_READ_V2 恒零发射。GET_SIGNING_CERTIFICATES(API 28+ 的
                    // v2 签名块读口)与 GET_SIGNATURES(v1)是靶程序不同的自校验通路,该分开。
                    val readOperation = if (flags and FLAG_GET_SIGNING_CERTIFICATES != 0L) {
                        SystemOperation.SIGNATURE_READ_V2
                    } else {
                        SystemOperation.SIGNATURE_READ
                    }
                    val info = invocation.result ?: return
                    val signatures = runCatching {
                        val sigField = info.javaClass.getField("signatures")
                        sigField.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        sigField.get(info) as? Array<Any>
                    }.getOrNull()
                    if (signatures.isNullOrEmpty()) return
                    for (sig in signatures) {
                        val bytes = runCatching {
                            sig.javaClass.getMethod("toByteArray").invoke(sig) as? ByteArray
                        }.getOrNull() ?: continue
                        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                        val hashHex = sha256.joinToString("") { "%02x".format(it) }
                        val base64 = android.util.Base64.encodeToString(
                            bytes.take(64).toByteArray(),
                            android.util.Base64.NO_WRAP,
                        )
                        emit(
                            kind = SystemKind.SIGNATURE,
                            operation = readOperation,
                            signatureHash = hashHex,
                            signatureBase64 = base64,
                            params = mapOf(
                                "targetPackage" to pkgName,
                                "flags" to flags.toString(),
                                "signatureIndex" to signatures.indexOf(sig).toString(),
                            ),
                        )
                    }
                }
            })
        }
    }

    companion object {
        private const val FLAG_GET_SIGNATURES = 0x40L
        private const val FLAG_GET_SIGNING_CERTIFICATES = 0x8000000L
    }
}
