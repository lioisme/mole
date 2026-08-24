package com.lioisme.modev.core.model

/**
 * 三态继承解析(V23):全局 [HookSwitch] + 单应用 [AppListenOverride]
 * → 该应用的有效配置(普通 [HookSwitch])。
 *
 * 解析是纯函数、无副作用;产物直接经 wire `config` 消息下发,
 * 注入侧与协议零感知 override 的存在(D1/D3 决策)。
 */
object EffectiveSwitch {

    /**
     * @param global   全局配置(未覆盖项的取值来源)。
     * @param override 单应用覆盖;null 或全 INHERIT 时原样返回 [global](零拷贝)。
     * @return 该应用的有效配置:INHERIT 项取全局值,ON/OFF 强制覆盖;
     *   覆盖中的未知 kind 名被忽略(对齐 [AppListenOverride.fromJson] 容错语义)。
     */
    fun resolve(global: HookSwitch, override: AppListenOverride?): HookSwitch {
        if (override == null || override.isDefault) return global
        var result = global
        if (override.cryptoListen != TriState.INHERIT) {
            result = result.copy(cryptoListen = override.cryptoListen == TriState.ON)
        }
        if (override.dbListen != TriState.INHERIT) {
            result = result.copy(dbListen = override.dbListen == TriState.ON)
        }
        if (override.kindOverrides.isNotEmpty()) {
            result = applyKindOverrides(result, override.kindOverrides)
        }
        return result
    }

    /** 类别覆盖 → [HookSwitch] 平铺字段映射([HookSwitch.enabled]/[HookSwitch.dbEnabled] 的反向)。 */
    private fun applyKindOverrides(
        switch: HookSwitch,
        kinds: Map<String, TriState>,
    ): HookSwitch {
        var result = switch
        for ((name, state) in kinds) {
            if (state == TriState.INHERIT) continue
            val value = state == TriState.ON
            result = when (name) {
                CryptoKind.MESSAGE_DIGEST.name -> result.copy(messageDigest = value)
                CryptoKind.CIPHER.name -> result.copy(cipher = value)
                CryptoKind.MAC.name -> result.copy(mac = value)
                CryptoKind.SIGNATURE.name -> result.copy(signature = value)
                CryptoKind.KEY_PARAM.name -> result.copy(keyParam = value)
                CryptoKind.KEY_GENERATION.name -> result.copy(keyGeneration = value)
                CryptoKind.KEY_FACTORY.name -> result.copy(keyFactory = value)
                CryptoKind.KEY_STORE.name -> result.copy(keyStore = value)
                CryptoKind.KEY_AGREEMENT.name -> result.copy(keyAgreement = value)
                CryptoKind.RANDOM.name -> result.copy(random = value)
                CryptoKind.BASE64.name -> result.copy(base64 = value)
                CryptoKind.WEB_CRYPTO.name -> result.copy(webView = value)
                SqlKind.OPEN.name -> result.copy(dbOpen = value)
                SqlKind.QUERY.name -> result.copy(dbQuery = value)
                SqlKind.DML.name -> result.copy(dbDml = value)
                SqlKind.EXEC.name -> result.copy(dbExec = value)
                SqlKind.TRANSACTION.name -> result.copy(dbTransaction = value)
                // 未知 kind 名:忽略(存储漂移兜底)
                else -> result
            }
        }
        return result
    }
}
