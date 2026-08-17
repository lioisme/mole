package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.util.BytesView
import java.security.interfaces.DSAKey
import java.security.interfaces.ECKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * 密钥对象参数提取(纯逻辑,可单测):按标准接口反射提取 RSA/DSA/EC 密钥结构参数,
 * 以 params 键值对承载(模数/指数/CRT 系数/曲线阶等)。
 */
object KeyParamExtractor {

    fun paramsOf(key: Any?): Map<String, String> {
        val params = mutableMapOf<String, String>()
        extract(key, params)
        return params
    }

    private fun extract(key: Any?, out: MutableMap<String, String>) {
        if (key == null) return
        when {
            key is RSAPrivateCrtKey -> {
                out["keyType"] = "RSA"
                out["modulus"] = key.modulus.toString(16)
                out["publicExponent"] = key.publicExponent.toString(16)
                out["privateExponent"] = key.privateExponent.toString(16)
                out["primeP"] = key.primeP.toString(16)
                out["primeQ"] = key.primeQ.toString(16)
                out["primeExponentP"] = key.primeExponentP.toString(16)
                out["primeExponentQ"] = key.primeExponentQ.toString(16)
                out["crtCoefficient"] = key.crtCoefficient.toString(16)
            }
            key is RSAPublicKey -> {
                out["keyType"] = "RSA"
                out["modulus"] = key.modulus.toString(16)
                out["publicExponent"] = key.publicExponent.toString(16)
            }
            key is RSAPrivateKey -> {
                out["keyType"] = "RSA"
                out["modulus"] = key.modulus.toString(16)
                out["privateExponent"] = key.privateExponent.toString(16)
            }
            key is DSAKey -> {
                out["keyType"] = "DSA"
                out["p"] = key.params.p.toString(16)
                out["q"] = key.params.q.toString(16)
                out["g"] = key.params.g.toString(16)
            }
            key is ECKey -> {
                out["keyType"] = "EC"
                out["curve"] = key.params.curve.field.javaClass.simpleName
                out["order"] = key.params.order.toString(16)
                out["cofactor"] = key.params.cofactor.toString()
            }
        }
    }
}