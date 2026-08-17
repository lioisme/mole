package com.lioisme.modev.hook.hooks

import java.math.BigInteger
import java.security.spec.DSAParameterSpec
import java.security.spec.ECGenParameterSpec
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
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 构造器字段提取(纯逻辑,标准 JCE Spec 对象直接构造)。 */
class SpecExtractorTest {

    @Test
    fun secret_key_spec_carries_algorithm_and_encoded() {
        val spec = SecretKeySpec(byteArrayOf(1, 2, 3, 4), "AES")
        val ext = SpecExtractor.extract(spec)
        assertEquals("SecretKeySpec", ext.typeName)
        assertEquals("AES", ext.algorithm)
        assertEquals("01020304", ext.key?.hex)
    }

    @Test
    fun iv_spec_carries_iv() {
        val ext = SpecExtractor.extract(IvParameterSpec(byteArrayOf(1, 2, 3)))
        assertEquals("010203", ext.iv?.hex)
    }

    @Test
    fun gcm_spec_carries_iv_and_tag_len() {
        val ext = SpecExtractor.extract(GCMParameterSpec(128, byteArrayOf(1, 2, 3, 4)))
        assertEquals("01020304", ext.iv?.hex)
        assertEquals("128", ext.params["tagLen"])
    }

    @Test
    fun pbe_key_spec_carries_password_salt_iteration_and_key_length() {
        val ext = SpecExtractor.extract(PBEKeySpec("secret".toCharArray(), byteArrayOf(9, 9), 1000, 256))
        assertEquals("secret", ext.key?.text)
        assertEquals("0909", ext.salt?.hex)
        assertEquals("1000", ext.params["iterationCount"])
        assertEquals("256", ext.params["keyLength"])
    }

    @Test
    fun pbe_parameter_spec_carries_salt_and_iteration() {
        val ext = SpecExtractor.extract(PBEParameterSpec(byteArrayOf(1, 2), 1000))
        assertEquals("0102", ext.salt?.hex)
        assertEquals("1000", ext.params["iterationCount"])
    }

    @Test
    fun dh_spec_carries_p_g_l() {
        val p = BigInteger("23")
        val g = BigInteger("5")
        val ext = SpecExtractor.extract(DHParameterSpec(p, g, 64))
        assertEquals("17", ext.params["p"])
        assertEquals("5", ext.params["g"])
        assertEquals("64", ext.params["l"])
    }

    @Test
    fun dh_gen_spec_carries_sizes() {
        val ext = SpecExtractor.extract(DHGenParameterSpec(2048, 224))
        assertEquals("2048", ext.params["primeSize"])
        assertEquals("224", ext.params["exponentSize"])
    }

    @Test
    fun ec_gen_spec_carries_curve_name() {
        val ext = SpecExtractor.extract(ECGenParameterSpec("secp256r1"))
        assertEquals("secp256r1", ext.params["curve"])
    }

    @Test
    fun dsa_spec_carries_p_q_g() {
        val p = BigInteger("23")
        val q = BigInteger("11")
        val g = BigInteger("5")
        val ext = SpecExtractor.extract(DSAParameterSpec(p, q, g))
        assertEquals("17", ext.params["p"])
        assertEquals("b", ext.params["q"])
        assertEquals("5", ext.params["g"])
    }

    @Test
    fun oaep_spec_carries_digest_mgf_and_mgf_digest() {
        val ext = SpecExtractor.extract(
            OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        )
        assertEquals("SHA-256", ext.params["digest"])
        assertEquals("MGF1", ext.params["mgf"])
        assertEquals("SHA-256", ext.params["mgfDigest"])
    }

    @Test
    fun pss_spec_carries_salt_length_and_trailer() {
        val ext = SpecExtractor.extract(
            PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)
        )
        assertEquals("SHA-256", ext.params["digest"])
        assertEquals("32", ext.params["saltLength"])
        assertEquals("1", ext.params["trailerField"])
        assertEquals("SHA-256", ext.params["mgfDigest"])
    }

    @Test
    fun rsa_crt_spec_carries_all_crt_fields() {
        val ext = SpecExtractor.extract(
            RSAPrivateCrtKeySpec(
                BigInteger("1111"), BigInteger("17"), BigInteger("2222"),
                BigInteger("3333"), BigInteger("4444"),
                BigInteger("5555"), BigInteger("6666"),
                BigInteger("7777"),
            )
        )
        assertEquals("457", ext.params["modulus"])
        assertEquals("11", ext.params["publicExponent"])
        assertEquals("8ae", ext.params["privateExponent"])
        assertEquals("d05", ext.params["primeP"])
        assertEquals("115c", ext.params["primeQ"])
        assertEquals("15b3", ext.params["primeExponentP"])
        assertEquals("1a0a", ext.params["primeExponentQ"])
        assertEquals("1e61", ext.params["crtCoefficient"])
    }

    @Test
    fun rsa_keygen_spec_carries_keysize_and_exponent() {
        val ext = SpecExtractor.extract(RSAKeyGenParameterSpec(2048, BigInteger("65537")))
        assertEquals("2048", ext.params["keysize"])
        assertEquals("10001", ext.params["publicExponent"])
    }

    @Test
    fun unknown_spec_falls_back_to_type_name() {
        val ext = SpecExtractor.extract("not-a-spec")
        assertNotNull(ext.typeName)
        assertNull(ext.algorithm)
        assertNull(ext.key)
    }

    @Test
    fun non_keystore_spec_returns_null_from_keystore_extractor() {
        assertNull(SpecExtractor.extractKeystoreSpec(PBEParameterSpec(byteArrayOf(1), 1)))
    }
}