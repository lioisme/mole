package com.lioisme.modev.hook.probes

import com.lioisme.modev.core.model.HookSwitch
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

/**
 * 算法捕获探针(ADR-3):收敛全部 11 类 Hooker(四类加密原语 + 密钥链 + 摘要/MAC/签名
 * + 随机源 + Base64)。WebViewHooker 独立为 [WebViewProbe]。
 */
class AlgorithmProbe : Probe {

    override val recordType: String = RecordTypes.CRYPTO

    /** V44 T2.4 冷启动裁剪:算法域全部 kind 经 `cryptoListen` master ∧ kind 双门控。 */
    override fun enabledIn(switch: HookSwitch): Boolean = switch.cryptoListen

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
        )
        for (hooker in hookers) {
            hooker.install()
        }
    }
}