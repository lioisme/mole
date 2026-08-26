package com.lioisme.modev.core.model

/**
 * 二态偏差解析(V24;V25 扩展采集微调项):默认配置 [HookSwitch] +
 * 单应用 [AppListenOverride] → 该应用的有效配置(普通 [HookSwitch])。
 *
 * 解析是纯函数、无副作用;产物直接经 wire `config` 消息下发,
 * 注入侧与协议零感知偏差的存在(协议 VERSION 恒 4)。
 */
object EffectiveSwitch {

    /**
     * @param default  默认配置(未定制项的取值来源)。
     * @param override 单应用偏差;null 或零偏差时原样返回 [default](零拷贝)。
     * @return 该应用的有效配置:null 位取默认值,显式位强制生效;
     *   偏差中的未知 kind 名被忽略(对齐 [AppListenOverride.fromJson] 容错语义)。
     */
    fun resolve(default: HookSwitch, override: AppListenOverride?): HookSwitch {
        if (override == null || override.isDefault) return default
        var result = default
        override.cryptoListen?.let { result = result.copy(cryptoListen = it) }
        override.dbListen?.let { result = result.copy(dbListen = it) }
        override.behaviorListen?.let { result = result.copy(behaviorListen = it) }
        override.prefsListen?.let { result = result.copy(prefsListen = it) }
        override.webviewListen?.let { result = result.copy(webviewListen = it) }
        if (override.kinds.isNotEmpty()) {
            result = applyKinds(result, override.kinds)
        }
        // V25 采集微调项叠加(与类别偏差同语义:非 null 即生效)
        override.logStack?.let { result = result.copy(logStack = it) }
        override.maxBytesPerField?.let { result = result.copy(maxBytesPerField = it) }
        override.ignoreStackPrefixes?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(ignoreStackPrefixes = it)
        }
        override.maxResultRows?.let { result = result.copy(maxResultRows = it) }
        override.maxResultChars?.let { result = result.copy(maxResultChars = it) }
        // V27 行为微调项
        override.fileContentCapture?.let { result = result.copy(fileContentCapture = it) }
        override.ignorePathPrefixes?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(ignorePathPrefixes = it)
        }
        return result
    }

    /** 类别 id → 有效开关原始位(非 master 门控;供设置页行值回显)。未知 id 返回 null。 */
    fun readKind(switch: HookSwitch, kindId: String): Boolean? = when (kindId) {
        CryptoKind.MESSAGE_DIGEST.name -> switch.messageDigest
        CryptoKind.CIPHER.name -> switch.cipher
        CryptoKind.MAC.name -> switch.mac
        CryptoKind.SIGNATURE.name -> switch.signature
        CryptoKind.KEY_PARAM.name -> switch.keyParam
        CryptoKind.KEY_GENERATION.name -> switch.keyGeneration
        CryptoKind.KEY_FACTORY.name -> switch.keyFactory
        CryptoKind.KEY_STORE.name -> switch.keyStore
        CryptoKind.KEY_AGREEMENT.name -> switch.keyAgreement
        CryptoKind.RANDOM.name -> switch.random
        CryptoKind.BASE64.name -> switch.base64
        CryptoKind.WEB_CRYPTO.name -> switch.webView
        SqlKind.OPEN.name -> switch.dbOpen
        SqlKind.QUERY.name -> switch.dbQuery
        SqlKind.DML.name -> switch.dbDml
        SqlKind.EXEC.name -> switch.dbExec
        SqlKind.TRANSACTION.name -> switch.dbTransaction
        BehaviorKind.FILE_READ.name -> switch.fileRead
        BehaviorKind.FILE_WRITE.name -> switch.fileWrite
        BehaviorKind.FILE_DELETE.name -> switch.fileDelete
        BehaviorKind.FILE_RENAME.name -> switch.fileRename
        BehaviorKind.FILE_LIST.name -> switch.fileList
        BehaviorKind.ASSET_READ.name -> switch.assetRead
        BehaviorKind.SHELL_EXEC.name -> switch.shellExec
        PrefsKind.OPEN.name -> switch.prefsOpen
        PrefsKind.GET.name -> switch.prefsGet
        PrefsKind.PUT.name -> switch.prefsPut
        PrefsKind.REMOVE.name -> switch.prefsRemove
        PrefsKind.CLEAR.name -> switch.prefsClear
        WebViewKind.PAGE_LOAD.name -> switch.wvPageLoad
        WebViewKind.EVALUATE_JS.name -> switch.wvEvaluateJs
        WebViewKind.JS_BRIDGE.name -> switch.wvJsBridge
        WebViewKind.DEBUG.name -> switch.wvDebug
        WebViewKind.JS_CRYPTO.name -> switch.wvJsCrypto
        else -> null
    }

    /** 类别偏差 → [HookSwitch] 平铺字段映射(与 [readKind] 严格互逆,须同步维护)。 */
    private fun applyKinds(
        switch: HookSwitch,
        kinds: Map<String, Boolean>,
    ): HookSwitch {
        var result = switch
        for ((name, value) in kinds) {
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
                BehaviorKind.FILE_READ.name -> result.copy(fileRead = value)
                BehaviorKind.FILE_WRITE.name -> result.copy(fileWrite = value)
                BehaviorKind.FILE_DELETE.name -> result.copy(fileDelete = value)
                BehaviorKind.FILE_RENAME.name -> result.copy(fileRename = value)
                BehaviorKind.FILE_LIST.name -> result.copy(fileList = value)
                BehaviorKind.ASSET_READ.name -> result.copy(assetRead = value)
                BehaviorKind.SHELL_EXEC.name -> result.copy(shellExec = value)
                PrefsKind.OPEN.name -> result.copy(prefsOpen = value)
                PrefsKind.GET.name -> result.copy(prefsGet = value)
                PrefsKind.PUT.name -> result.copy(prefsPut = value)
                PrefsKind.REMOVE.name -> result.copy(prefsRemove = value)
                PrefsKind.CLEAR.name -> result.copy(prefsClear = value)
                WebViewKind.PAGE_LOAD.name -> result.copy(wvPageLoad = value)
                WebViewKind.EVALUATE_JS.name -> result.copy(wvEvaluateJs = value)
                WebViewKind.JS_BRIDGE.name -> result.copy(wvJsBridge = value)
                WebViewKind.DEBUG.name -> result.copy(wvDebug = value)
                WebViewKind.JS_CRYPTO.name -> result.copy(wvJsCrypto = value)
                // 未知 kind 名:忽略(存储漂移兜底)
                else -> result
            }
        }
        return result
    }
}
