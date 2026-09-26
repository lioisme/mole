package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.util.BytesView
import java.math.BigInteger
import java.security.spec.DSAParameterSpec
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.EllipticCurve
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAPrivateCrtKeySpec
import javax.crypto.spec.DHGenParameterSpec
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 标准密钥/参数 Spec 对象字段提取(纯逻辑,可单测)。
 * 基于公开 getter 提取,不依赖构造器参数形态,天然覆盖各类构造器;
 * Android KeyGenParameterSpec 走反射提取,避免硬依赖。
 */
object SpecExtractor {

    data class SpecExtraction(
        val typeName: String,
        val algorithm: String? = null,
        val key: BytesView? = null,
        val iv: BytesView? = null,
        val salt: BytesView? = null,
        val params: Map<String, String> = emptyMap(),
    )

    fun extract(instance: Any): SpecExtraction = when (instance) {
        is SecretKeySpec -> SpecExtraction(
            typeName = "SecretKeySpec",
            algorithm = runCatching { instance.algorithm }.getOrNull(),
            key = viewOf(runCatching { instance.encoded }.getOrNull()),
        )
        is IvParameterSpec -> SpecExtraction(typeName = "IvParameterSpec", iv = viewOf(instance.iv))
        is GCMParameterSpec -> SpecExtraction(
            typeName = "GCMParameterSpec",
            iv = viewOf(instance.iv),
            params = mapOf("tagLen" to instance.tLen.toString()),
        )
        is PBEKeySpec -> SpecExtraction(
            typeName = "PBEKeySpec",
            key = viewOf(instance.password?.let { String(it).toByteArray(Charsets.UTF_8) }),
            salt = viewOf(instance.salt),
            params = mapOf(
                "iterationCount" to instance.iterationCount.toString(),
                "keyLength" to instance.keyLength.toString(),
            ),
        )
        is PBEParameterSpec -> SpecExtraction(
            typeName = "PBEParameterSpec",
            salt = viewOf(instance.salt),
            params = mapOf("iterationCount" to instance.iterationCount.toString()),
        )
        is DHParameterSpec -> SpecExtraction(
            typeName = "DHParameterSpec",
            params = mapOf(
                "p" to hexOf(instance.p),
                "g" to hexOf(instance.g),
                "l" to instance.l.toString(),
            ),
        )
        is DHGenParameterSpec -> SpecExtraction(
            typeName = "DHGenParameterSpec",
            params = mapOf(
                "primeSize" to instance.primeSize.toString(),
                "exponentSize" to instance.exponentSize.toString(),
            ),
        )
        is ECGenParameterSpec -> SpecExtraction(
            typeName = "ECGenParameterSpec",
            params = mapOf("curve" to instance.name),
        )
        is ECParameterSpec -> SpecExtraction(
            typeName = "ECParameterSpec",
            params = buildMap {
                put("order", hexOf(instance.order))
                put("cofactor", instance.cofactor.toString())
                curveField(instance.curve)?.let { put("field", it) }
            },
        )
        is DSAParameterSpec -> SpecExtraction(
            typeName = "DSAParameterSpec",
            params = mapOf(
                "p" to hexOf(instance.p),
                "q" to hexOf(instance.q),
                "g" to hexOf(instance.g),
            ),
        )
        is OAEPParameterSpec -> SpecExtraction(
            typeName = "OAEPParameterSpec",
            params = buildMap {
                put("digest", instance.digestAlgorithm)
                put("mgf", instance.mgfAlgorithm)
                (instance.mgfParameters as? MGF1ParameterSpec)?.let { put("mgfDigest", it.digestAlgorithm) }
            },
        )
        is MGF1ParameterSpec -> SpecExtraction(
            typeName = "MGF1ParameterSpec",
            params = mapOf("digest" to instance.digestAlgorithm),
        )
        is PSSParameterSpec -> SpecExtraction(
            typeName = "PSSParameterSpec",
            params = buildMap {
                put("digest", instance.digestAlgorithm)
                put("mgf", instance.mgfAlgorithm)
                put("saltLength", instance.saltLength.toString())
                put("trailerField", instance.trailerField.toString())
                (instance.mgfParameters as? MGF1ParameterSpec)?.let { put("mgfDigest", it.digestAlgorithm) }
            },
        )
        is RSAPrivateCrtKeySpec -> SpecExtraction(
            typeName = "RSAPrivateCrtKeySpec",
            params = mapOf(
                "modulus" to hexOf(instance.modulus),
                "publicExponent" to hexOf(instance.publicExponent),
                "privateExponent" to hexOf(instance.privateExponent),
                "primeP" to hexOf(instance.primeP),
                "primeQ" to hexOf(instance.primeQ),
                "primeExponentP" to hexOf(instance.primeExponentP),
                "primeExponentQ" to hexOf(instance.primeExponentQ),
                "crtCoefficient" to hexOf(instance.crtCoefficient),
            ),
        )
        is RSAKeyGenParameterSpec -> SpecExtraction(
            typeName = "RSAKeyGenParameterSpec",
            params = mapOf(
                "keysize" to instance.keysize.toString(),
                "publicExponent" to hexOf(instance.publicExponent),
            ),
        )
        else -> SpecExtraction(typeName = instance.javaClass.simpleName)
    }

    /** Android KeyGenParameterSpec(keystore 密钥生成参数,反射提取避免硬依赖)。 */
    fun extractKeystoreSpec(instance: Any): SpecExtraction? {
        if (instance.javaClass.name != "android.security.keystore.KeyGenParameterSpec") return null
        fun call(name: String): Any? = runCatching { instance.javaClass.getMethod(name).invoke(instance) }.getOrNull()
        val params = buildMap {
            call("getKeySize")?.let { put("keysize", it.toString()) }
            call("getPurposes")?.let { put("purposes", it.toString()) }
            call("getBlockModes")?.let { put("blockModes", (it as Array<*>).joinToString(",")) }
            call("getEncryptionPaddings")?.let { put("paddings", (it as Array<*>).joinToString(",")) }
            call("getDigests")?.let { put("digests", (it as Array<*>).joinToString(",")) }
            call("isRandomizedEncryptionRequired")?.let { put("randomizedEncryption", it.toString()) }
        }
        return SpecExtraction(typeName = "KeyGenParameterSpec", params = params)
    }

    private fun viewOf(bytes: ByteArray?): BytesView? = BytesView.of(bytes)

    private fun hexOf(bi: BigInteger?): String = bi?.toString(16) ?: ""

    private fun curveField(curve: EllipticCurve?): String? {
        if (curve == null) return null
        return when (val field = curve.field) {
            is java.security.spec.ECFieldFp -> "Fp(${field.p})"
            is java.security.spec.ECFieldF2m -> "F2m(${field.m})"
            else -> field.javaClass.simpleName
        }
    }
}
