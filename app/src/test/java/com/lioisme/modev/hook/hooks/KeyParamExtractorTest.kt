package com.lioisme.modev.hook.hooks

import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 密钥对象参数提取:真实生成的 RSA/DSA/EC 密钥对(纯 JVM)。 */
class KeyParamExtractorTest {

    @Test
    fun rsa_crt_key_extracts_all_parameters() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(512) }.generateKeyPair()
        val privateKey = kp.private as java.security.interfaces.RSAPrivateCrtKey
        val params = KeyParamExtractor.paramsOf(privateKey)
        assertEquals("RSA", params["keyType"])
        assertEquals(privateKey.modulus.toString(16), params["modulus"])
        assertEquals(privateKey.publicExponent.toString(16), params["publicExponent"])
        assertEquals(privateKey.privateExponent.toString(16), params["privateExponent"])
        assertEquals(privateKey.primeP.toString(16), params["primeP"])
        assertEquals(privateKey.primeQ.toString(16), params["primeQ"])
        assertEquals(privateKey.primeExponentP.toString(16), params["primeExponentP"])
        assertEquals(privateKey.primeExponentQ.toString(16), params["primeExponentQ"])
        assertEquals(privateKey.crtCoefficient.toString(16), params["crtCoefficient"])
    }

    @Test
    fun rsa_public_key_extracts_modulus_and_exponent() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(512) }.generateKeyPair()
        val publicKey = kp.public as java.security.interfaces.RSAPublicKey
        val params = KeyParamExtractor.paramsOf(publicKey)
        assertEquals("RSA", params["keyType"])
        assertEquals(publicKey.modulus.toString(16), params["modulus"])
        assertEquals(publicKey.publicExponent.toString(16), params["publicExponent"])
    }

    @Test
    fun dsa_key_extracts_p_q_g() {
        val kp = KeyPairGenerator.getInstance("DSA").apply { initialize(1024) }.generateKeyPair()
        val params = KeyParamExtractor.paramsOf(kp.private)
        assertEquals("DSA", params["keyType"])
        val dsaKey = kp.private as java.security.interfaces.DSAKey
        assertEquals(dsaKey.params.p.toString(16), params["p"])
        assertEquals(dsaKey.params.q.toString(16), params["q"])
        assertEquals(dsaKey.params.g.toString(16), params["g"])
    }

    @Test
    fun ec_key_extracts_curve_and_order() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val params = KeyParamExtractor.paramsOf(kp.public)
        assertEquals("EC", params["keyType"])
        val ecKey = kp.public as java.security.interfaces.ECKey
        assertEquals(ecKey.params.order.toString(16), params["order"])
        assertEquals(ecKey.params.cofactor.toString(), params["cofactor"])
        assertTrue(params["curve"]!!.isNotBlank())
    }

    @Test
    fun null_or_unknown_key_yields_empty_params() {
        assertTrue(KeyParamExtractor.paramsOf(null).isEmpty())
        assertTrue(KeyParamExtractor.paramsOf("not-a-key").isEmpty())
    }
}
