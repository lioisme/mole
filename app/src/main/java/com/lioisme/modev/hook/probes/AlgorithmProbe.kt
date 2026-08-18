package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.RecordTypes
import com.lioisme.modev.hook.Probe
import com.lioisme.modev.hook.ProbeContext
import com.lioisme.modev.hook.hooks.Base64Hooker
import com.lioisme.modev.hook.hooks.CipherHooker
import com.lioisme.modev.hook.hooks.KeyAgreementHooker
import com.lioisme.modev.hook.hooks.KeyFactoryHooker
import com.lioisme.modev.hook.hooks.KeyGenerationHooker
import com.lioisme.modev.hook.hooks.KeyParamHooker
import com.lioisme.modev.hook.hooks.KeyStoreHooker
import com.lioisme.modev.hook.hooks.MacHooker
import com.lioisme.modev.hook.hooks.MessageDigestHooker
import com.lioisme.modev.hook.hooks.SecureRandomHooker
import com.lioisme.modev.hook.hooks.SignatureHooker
import com.lioisme.modev.hook.hooks.WebViewHooker

/**
 * 算法捕获探针(ADR-3):收敛全部 12 类 Hooker(四类加密原语 + 密钥链 + 摘要/MAC/签名
 * + 随机源 + Base64 + WebView 桥)。装配清单即能力边界,新增/移除类别在本类一处修改。
 */
class AlgorithmProbe : Probe {

    override val recordType: String = RecordTypes.CRYPTO

    override fun install(context: ProbeContext) {
        val hookers = listOf(
            CipherHooker(context),
            MessageDigestHooker(context),
            MacHooker(context),
            SignatureHooker(context),
            KeyParamHooker(context),
            KeyGenerationHooker(context),
            KeyFactoryHooker(context),
            KeyStoreHooker(context),
            KeyAgreementHooker(context),
            SecureRandomHooker(context),
            Base64Hooker(context),
            WebViewHooker(context),
        )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}