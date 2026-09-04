package com.lioisme.modev.hook.hooks

import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.hook.CryptoContextTracker
import com.lioisme.modev.hook.HookEngine
import com.lioisme.modev.hook.Invocation
import com.lioisme.modev.hook.MethodHook
import com.lioisme.modev.hook.MethodFinder
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.RecordSink
import java.math.BigInteger
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECField
import java.security.spec.ECPoint
import java.security.spec.EllipticCurve
import javax.crypto.spec.PSource

/**
 * 密钥/参数对象构造捕获。
 * 注册表驱动:每个 Spec 类 + 构造器签名注册一次,构造完成后经 [SpecExtractor] 提取
 * 密钥/IV/目标量参数(含 PBE 迭代次数、GCM tag 长度、RSA CRT 系数、EC 曲线等)。
 */
class KeyParamHooker(
    context: ProbeContext,
) : BaseHooker(context, CryptoKind.KEY_PARAM) {

    override fun install() {
        val f = MethodFinder
        for (d in CTOR_DESCRIPTORS) {
            hookCtor(f.findCtor(d.className, *d.paramTypes)) { inv, result -> afterConstruct(inv, result) }
        }
    }

    private fun hookCtor(ctor: java.lang.reflect.Constructor<*>?, after: (Invocation, Any?) -> Unit) {
        if (ctor == null) return
        engine.hook(ctor, object : MethodHook() {
            override fun after(invocation: Invocation) {
                if (enabled()) {
                    after(invocation, invocation.result)
                }
            }
        })
    }

    private fun afterConstruct(invocation: Invocation, result: Any?) {
        // D-2 修复:构造器 hook 的 chain.proceed() 返回 void/null,
        // 新实例经 thisObject 获取(after 阶段 LSPlant 已绑定)
        val target = result ?: invocation.thisObject ?: return
        val ext = SpecExtractor.extractKeystoreSpec(target) ?: SpecExtractor.extract(target)
        emit(
            operation = CryptoOperation.CONSTRUCT,
            algorithm = ext.algorithm ?: ext.typeName,
            contextId = CryptoContextTracker.identity(target),
            key = ext.key,
            iv = ext.iv,
            salt = ext.salt,
            params = ext.params.takeIf { it.isNotEmpty() },
        )
    }

    private data class CtorDescriptor(val className: String, val paramTypes: Array<Class<*>>)

    private companion object {
        const val SECRET_KEY_SPEC = "javax.crypto.spec.SecretKeySpec"
        const val IV_SPEC = "javax.crypto.spec.IvParameterSpec"
        const val GCM_SPEC = "javax.crypto.spec.GCMParameterSpec"
        const val PBE_KEY_SPEC = "javax.crypto.spec.PBEKeySpec"
        const val PBE_PARAM_SPEC = "javax.crypto.spec.PBEParameterSpec"
        const val DH_PARAM_SPEC = "javax.crypto.spec.DHParameterSpec"
        const val DH_GEN_SPEC = "javax.crypto.spec.DHGenParameterSpec"
        const val EC_GEN_SPEC = "java.security.spec.ECGenParameterSpec"
        const val EC_PARAM_SPEC = "java.security.spec.ECParameterSpec"
        const val DSA_PARAM_SPEC = "java.security.spec.DSAParameterSpec"
        const val OAEP_SPEC = "java.security.spec.OAEPParameterSpec"
        const val MGF1_SPEC = "java.security.spec.MGF1ParameterSpec"
        const val PSS_SPEC = "java.security.spec.PSSParameterSpec"
        const val RSA_CRT_SPEC = "java.security.spec.RSAPrivateCrtKeySpec"
        const val RSA_KEYGEN_SPEC = "java.security.spec.RSAKeyGenParameterSpec"

        /** int 原始类型(构造器签名用)。 */
        private val INT_TYPE: Class<*> = Int::class.javaPrimitiveType!!

        val CTOR_DESCRIPTORS = listOf(
            CtorDescriptor(SECRET_KEY_SPEC, arrayOf<Class<*>>(ByteArray::class.java, String::class.java)),
            CtorDescriptor(SECRET_KEY_SPEC, arrayOf<Class<*>>(ByteArray::class.java, INT_TYPE, INT_TYPE, String::class.java)),
            CtorDescriptor(IV_SPEC, arrayOf<Class<*>>(ByteArray::class.java)),
            CtorDescriptor(IV_SPEC, arrayOf<Class<*>>(ByteArray::class.java, INT_TYPE, INT_TYPE)),
            CtorDescriptor(GCM_SPEC, arrayOf<Class<*>>(INT_TYPE, ByteArray::class.java)),
            CtorDescriptor(GCM_SPEC, arrayOf<Class<*>>(INT_TYPE, ByteArray::class.java, INT_TYPE, INT_TYPE)),
            CtorDescriptor(PBE_KEY_SPEC, arrayOf<Class<*>>(CharArray::class.java)),
            CtorDescriptor(PBE_KEY_SPEC, arrayOf<Class<*>>(CharArray::class.java, ByteArray::class.java, INT_TYPE)),
            CtorDescriptor(PBE_KEY_SPEC, arrayOf<Class<*>>(CharArray::class.java, ByteArray::class.java, INT_TYPE, INT_TYPE)),
            CtorDescriptor(PBE_KEY_SPEC, arrayOf<Class<*>>(CharArray::class.java, ByteArray::class.java, INT_TYPE, INT_TYPE, INT_TYPE)),
            CtorDescriptor(PBE_PARAM_SPEC, arrayOf<Class<*>>(ByteArray::class.java, INT_TYPE)),
            CtorDescriptor(DH_PARAM_SPEC, arrayOf<Class<*>>(BigInteger::class.java, BigInteger::class.java, INT_TYPE)),
            CtorDescriptor(DH_GEN_SPEC, arrayOf<Class<*>>(INT_TYPE, INT_TYPE)),
            CtorDescriptor(EC_GEN_SPEC, arrayOf<Class<*>>(String::class.java)),
            CtorDescriptor(EC_PARAM_SPEC, arrayOf<Class<*>>(ECField::class.java, EllipticCurve::class.java, ECPoint::class.java, BigInteger::class.java, INT_TYPE)),
            CtorDescriptor(DSA_PARAM_SPEC, arrayOf<Class<*>>(BigInteger::class.java, BigInteger::class.java, BigInteger::class.java)),
            CtorDescriptor(OAEP_SPEC, arrayOf<Class<*>>(String::class.java, String::class.java, AlgorithmParameterSpec::class.java, PSource::class.java)),
            CtorDescriptor(MGF1_SPEC, arrayOf<Class<*>>(String::class.java)),
            CtorDescriptor(PSS_SPEC, arrayOf<Class<*>>(String::class.java, String::class.java, AlgorithmParameterSpec::class.java, INT_TYPE, INT_TYPE)),
            CtorDescriptor(PSS_SPEC, arrayOf<Class<*>>(INT_TYPE)),
            CtorDescriptor(RSA_CRT_SPEC, arrayOf<Class<*>>(BigInteger::class.java, BigInteger::class.java, BigInteger::class.java, BigInteger::class.java, BigInteger::class.java, BigInteger::class.java, BigInteger::class.java, BigInteger::class.java, BigInteger::class.java)),
            CtorDescriptor(RSA_KEYGEN_SPEC, arrayOf<Class<*>>(INT_TYPE, BigInteger::class.java)),
        )
    }
}
