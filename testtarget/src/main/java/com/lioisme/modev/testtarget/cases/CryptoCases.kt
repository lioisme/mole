package com.lioisme.modev.testtarget.cases

import android.content.Context
import com.lioisme.modev.testtarget.Tt
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** L-CRY-*:算法监听触发器(JCA 全谱)。 */
object CryptoCases {

    @Suppress("BlockingMethodInNonBlockingContext")
    fun run(ctx: Context, case: String) {
        when (case) {
            "L-CRY-01" -> {
                val md5 = java.security.MessageDigest.getInstance("MD5")
                    .digest("modev-test".toByteArray())
                val sha = java.security.MessageDigest.getInstance("SHA-256")
                    .digest("modev-test".toByteArray())
                Tt.result(case, "md5len" to md5.size.toString(), "sha256len" to sha.size.toString())
            }
            "L-CRY-02" -> {
                val key = SecretKeySpec("0123456789abcdef".toByteArray(), "AES")
                val iv = IvParameterSpec("fedcba9876543210".toByteArray())
                val enc = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.ENCRYPT_MODE, key, iv)
                }
                val ct = enc.doFinal("hello-modev-cbc".toByteArray())
                val dec = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.DECRYPT_MODE, key, iv)
                }
                val pt = dec.doFinal(ct)
                Tt.result(case, "roundtrip" to (String(pt) == "hello-modev-cbc").toString())
            }
            "L-CRY-03" -> {
                val key = SecretKeySpec("0123456789abcdef".toByteArray(), "AES")
                val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val enc = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                }
                val ct = enc.update("hello-modev-gcm".toByteArray()) + enc.doFinal()
                val dec = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                }
                val pt = dec.doFinal(ct)
                Tt.result(case, "roundtrip" to (String(pt) == "hello-modev-gcm").toString())
            }
            "L-CRY-04" -> {
                val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val enc = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply {
                    init(Cipher.ENCRYPT_MODE, kp.public)
                }
                val ct = enc.doFinal("rsa-modev".toByteArray())
                val dec = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply {
                    init(Cipher.DECRYPT_MODE, kp.private)
                }
                Tt.result(case, "roundtrip" to (String(dec.doFinal(ct)) == "rsa-modev").toString())
            }
            "L-CRY-05" -> {
                val mac = Mac.getInstance("HmacSHA256").apply {
                    init(SecretKeySpec("secret-key".toByteArray(), "HmacSHA256"))
                }
                mac.update("part1".toByteArray())
                Tt.result(case, "maclen" to mac.doFinal("part2".toByteArray()).size.toString())
            }
            "L-CRY-06" -> {
                val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
                val sig = java.security.Signature.getInstance("SHA256withRSA").apply {
                    initSign(kp.private)
                    update("sign-me".toByteArray())
                }
                val signed = sig.sign()
                val ver = java.security.Signature.getInstance("SHA256withRSA").apply {
                    initVerify(kp.public)
                    update("sign-me".toByteArray())
                }
                Tt.result(case, "verified" to ver.verify(signed).toString())
            }
            "L-CRY-07" -> {
                SecretKeySpec("0123456789abcdef".toByteArray(), "AES")
                IvParameterSpec("fedcba9876543210".toByteArray())
                GCMParameterSpec(128, ByteArray(12))
                PBEKeySpec("pw".toCharArray(), "salt".toByteArray(), 1000, 128)
                Tt.result(case, "constructed" to "4")
            }
            "L-CRY-08" -> {
                val k = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
                Tt.result(case, "algo" to k.algorithm)
            }
            "L-CRY-09" -> {
                // 迭代数取 10:PBKDF2 每轮 2 update + 1 digest,1000 轮会制造数千条洪泛记录
                val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                val spec = PBEKeySpec("pw".toCharArray(), "salt".toByteArray(), 10, 128)
                Tt.result(case, "derived" to (skf.generateSecret(spec).encoded.size.toString()))
            }
            "L-CRY-10" -> {
                // BKS 口令保护走 PKCS12 KDF(65536 轮摘要)会制造无界洪泛;
                // 改用 load/size/aliases/getKey 验证 KEY_STORE 捕获面(LOAD+GET_KEY)。
                val ks = java.security.KeyStore.getInstance("BKS")
                ks.load(null, null)
                ks.size()
                ks.aliases().toList()
                ks.getKey("absent", null)
                Tt.result(case, "type" to ks.type)
            }
            "L-CRY-11" -> {
                // DH 2048 密钥对在 x86_64 模拟器上需数十秒;改用 EC/ECDH(毫秒级)
                val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
                val ka = KeyAgreement.getInstance("ECDH").apply { init(kp.private) }
                val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(kp.public.encoded))
                ka.doPhase(pub, true)
                val secret = ka.generateSecret()
                Tt.result(case, "secretLen" to secret.size.toString())
            }
            "L-CRY-12" -> {
                val out = ByteArray(16)
                SecureRandom().nextBytes(out)
                Tt.result(case, "random" to out.joinToString("") { "%02x".format(it) }.take(8))
            }
            "L-CRY-13" -> {
                val data = "modev-base64".toByteArray()
                val a = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
                android.util.Base64.decode(a, android.util.Base64.NO_WRAP)
                val j = java.util.Base64.getEncoder().encodeToString(data)
                java.util.Base64.getDecoder().decode(j)
                java.util.Base64.getUrlEncoder().encodeToString(data)
                java.util.Base64.getMimeEncoder().encodeToString(data)
                Tt.result(case, "android" to a, "java" to j)
            }
            "L-CRY-14" -> {
                val key = SecretKeySpec("0123456789abcdef".toByteArray(), "AES")
                val iv = IvParameterSpec("fedcba9876543210".toByteArray())
                val c = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, key, iv) }
                Tt.result(case, "ivlen" to (c.iv?.size?.toString() ?: "0"))
            }
            else -> Tt.result(case, "error" to "unknown-case")
        }
    }
}
