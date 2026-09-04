package com.lioisme.modev.core.model

import android.util.Log

/**
 * 二态偏差解析(V24;V25 扩展采集微调项):默认配置 [HookSwitch] +
 * 单应用 [AppListenOverride] → 该应用的有效配置(普通 [HookSwitch])。
 *
 * 解析是纯函数、无副作用;产物直接经 wire `config` 消息下发,
 * 注入侧与协议零感知偏差的存在(协议 VERSION 恒 4)。
 *
 * V34 审计增强:干预型开关启用时输出 Log.w 安全警告,便于调试和审计。
 */
object EffectiveSwitch {

    private const val TAG = "EffectiveSwitch"

    /**
     * 干预型 kind 名集合(V37 T0.4:由 [CategoryTaxonomy] 功能域标注派生,
     * 单一事实源,消灭手工清单漂移;供安全审计日志)。
     */
    private val INTERVENTION_KINDS: Set<String> =
        CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE).map { it.id }.toSet()

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
        override.networkListen?.let { result = result.copy(networkListen = it) }
        override.deviceEnvListen?.let { result = result.copy(deviceEnvListen = it) }
        override.uiListen?.let { result = result.copy(uiListen = it) }
        override.systemListen?.let { result = result.copy(systemListen = it) }
        override.processControlListen?.let { result = result.copy(processControlListen = it) }
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
        // V37 T1.2:功能域数据偏差 → feature 段(整表替换语义;keywordIds 由宿主端
        // 解析为 dialogKeywords 后下发,注入侧零感知)
        override.dialogKeywords?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(feature = result.feature.copy(dialogKeywords = it))
        }
        override.spoofProfileId?.let {
            result = result.copy(feature = result.feature.copy(spoofProfileId = it))
        }
        override.spoofOverrides?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(feature = result.feature.copy(spoofOverrides = it))
        }
        override.extraRootPackages?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(feature = result.feature.copy(extraRootPackages = it))
        }
        override.extraXposedPrefixes?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(feature = result.feature.copy(extraXposedPrefixes = it))
        }
        override.extraFridaPorts?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(feature = result.feature.copy(extraFridaPorts = it))
        }
        override.extraMaskKeywords?.takeIf { it.isNotEmpty() }?.let {
            result = result.copy(feature = result.feature.copy(extraMaskKeywords = it))
        }
        // V34 审计增强:干预型开关启用时输出安全警告日志
        logInterventionActivation(result, default)
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
        NetworkKind.SSL_CONTEXT.name -> switch.sslContext
        NetworkKind.SSL_TRUST_MANAGER.name -> switch.sslTrustManager
        NetworkKind.SSL_OKHTTP_PINNER.name -> switch.sslOkhttpPinner
        NetworkKind.SSL_OKHTTP_VERIFIER.name -> switch.sslOkhttpVerifier
        NetworkKind.SSL_WEBVIEW.name -> switch.sslWebview
        NetworkKind.SSL_NETWORK_CONFIG.name -> switch.sslNetworkConfig
        NetworkKind.PROXY_SYSTEM_PROP.name -> switch.proxySystemProp
        NetworkKind.PROXY_CONNECTIVITY.name -> switch.proxyConnectivity
        NetworkKind.PROXY_SETTINGS.name -> switch.proxySettings
        NetworkKind.PROXY_OKHTTP.name -> switch.proxyOkhttp
        NetworkKind.VPN_CAPABILITIES.name -> switch.vpnCapabilities
        NetworkKind.VPN_INTERFACE.name -> switch.vpnInterface
        NetworkKind.VPN_NETWORK_INFO.name -> switch.vpnNetworkInfo
        NetworkKind.VPN_LINK_PROPERTIES.name -> switch.vpnLinkProperties
        NetworkKind.REQUEST.name -> switch.networkRequest
        DeviceEnvKind.ROOT_FILE.name -> switch.rootFile
        DeviceEnvKind.ROOT_EXEC.name -> switch.rootExec
        DeviceEnvKind.ROOT_PACKAGE_MANAGER.name -> switch.rootPackageManager
        DeviceEnvKind.ROOT_PROPERTY.name -> switch.rootProperty
        DeviceEnvKind.XPOSED_CLASS.name -> switch.xposedClass
        DeviceEnvKind.XPOSED_STACK.name -> switch.xposedStack
        DeviceEnvKind.XPOSED_MAPS.name -> switch.xposedMaps
        DeviceEnvKind.EMULATOR_BUILD.name -> switch.emulatorBuild
        DeviceEnvKind.EMULATOR_FILE.name -> switch.emulatorFile
        DeviceEnvKind.EMULATOR_SENSOR.name -> switch.emulatorSensor
        DeviceEnvKind.EMULATOR_NETWORK.name -> switch.emulatorNetwork
        DeviceEnvKind.EMULATOR_TELEPHONY.name -> switch.emulatorTelephony
        DeviceEnvKind.EMULATOR_CPU.name -> switch.emulatorCpu
        DeviceEnvKind.EMULATOR_BATTERY.name -> switch.emulatorBattery
        DeviceEnvKind.SYSTEM_PROPERTIES.name -> switch.systemProperties
        DeviceEnvKind.PROC_STATUS.name -> switch.procStatus
        DeviceEnvKind.PROC_MOUNT_INFO.name -> switch.procMountInfo
        DeviceEnvKind.PROC_CGROUP.name -> switch.procCgroup
        DeviceEnvKind.PROC_NET.name -> switch.procNet
        DeviceEnvKind.DETECT_ROOT.name -> switch.detectRoot
        DeviceEnvKind.DETECT_EMULATOR.name -> switch.detectEmulator
        UiInteractionKind.DIALOG_SHOW.name -> switch.dialogShow
        UiInteractionKind.DIALOG_KEYWORD.name -> switch.dialogKeyword
        UiInteractionKind.WIDGET_TEXT.name -> switch.widgetText
        UiInteractionKind.ACTIVITY_START.name -> switch.activityStart
        UiInteractionKind.ACTIVITY_RESUME.name -> switch.activityResume
        SystemKind.LOGCAT.name -> switch.logcatCapture
        SystemKind.APP_INFO.name -> switch.appInfoCapture
        SystemKind.SIGNATURE.name -> switch.signatureMonitor
        ProcessKind.EXIT_INTERCEPT.name -> switch.exitIntercept
        ProcessKind.CRASH_PREVENT.name -> switch.crashPrevent
        ProcessKind.SCREEN_RECORD.name -> switch.screenRecordBypass
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
                NetworkKind.SSL_CONTEXT.name -> result.copy(sslContext = value)
                NetworkKind.SSL_TRUST_MANAGER.name -> result.copy(sslTrustManager = value)
                NetworkKind.SSL_OKHTTP_PINNER.name -> result.copy(sslOkhttpPinner = value)
                NetworkKind.SSL_OKHTTP_VERIFIER.name -> result.copy(sslOkhttpVerifier = value)
                NetworkKind.SSL_WEBVIEW.name -> result.copy(sslWebview = value)
                NetworkKind.SSL_NETWORK_CONFIG.name -> result.copy(sslNetworkConfig = value)
                NetworkKind.PROXY_SYSTEM_PROP.name -> result.copy(proxySystemProp = value)
                NetworkKind.PROXY_CONNECTIVITY.name -> result.copy(proxyConnectivity = value)
                NetworkKind.PROXY_SETTINGS.name -> result.copy(proxySettings = value)
                NetworkKind.PROXY_OKHTTP.name -> result.copy(proxyOkhttp = value)
                NetworkKind.VPN_CAPABILITIES.name -> result.copy(vpnCapabilities = value)
                NetworkKind.VPN_INTERFACE.name -> result.copy(vpnInterface = value)
                NetworkKind.VPN_NETWORK_INFO.name -> result.copy(vpnNetworkInfo = value)
                NetworkKind.VPN_LINK_PROPERTIES.name -> result.copy(vpnLinkProperties = value)
                NetworkKind.REQUEST.name -> result.copy(networkRequest = value)
                DeviceEnvKind.ROOT_FILE.name -> result.copy(rootFile = value)
                DeviceEnvKind.ROOT_EXEC.name -> result.copy(rootExec = value)
                DeviceEnvKind.ROOT_PACKAGE_MANAGER.name -> result.copy(rootPackageManager = value)
                DeviceEnvKind.ROOT_PROPERTY.name -> result.copy(rootProperty = value)
                DeviceEnvKind.XPOSED_CLASS.name -> result.copy(xposedClass = value)
                DeviceEnvKind.XPOSED_STACK.name -> result.copy(xposedStack = value)
                DeviceEnvKind.XPOSED_MAPS.name -> result.copy(xposedMaps = value)
                DeviceEnvKind.EMULATOR_BUILD.name -> result.copy(emulatorBuild = value)
                DeviceEnvKind.EMULATOR_FILE.name -> result.copy(emulatorFile = value)
                DeviceEnvKind.EMULATOR_SENSOR.name -> result.copy(emulatorSensor = value)
                DeviceEnvKind.EMULATOR_NETWORK.name -> result.copy(emulatorNetwork = value)
                DeviceEnvKind.EMULATOR_TELEPHONY.name -> result.copy(emulatorTelephony = value)
                DeviceEnvKind.EMULATOR_CPU.name -> result.copy(emulatorCpu = value)
                DeviceEnvKind.EMULATOR_BATTERY.name -> result.copy(emulatorBattery = value)
                DeviceEnvKind.SYSTEM_PROPERTIES.name -> result.copy(systemProperties = value)
                DeviceEnvKind.PROC_STATUS.name -> result.copy(procStatus = value)
                DeviceEnvKind.PROC_MOUNT_INFO.name -> result.copy(procMountInfo = value)
                DeviceEnvKind.PROC_CGROUP.name -> result.copy(procCgroup = value)
                DeviceEnvKind.PROC_NET.name -> result.copy(procNet = value)
                DeviceEnvKind.DETECT_ROOT.name -> result.copy(detectRoot = value)
                DeviceEnvKind.DETECT_EMULATOR.name -> result.copy(detectEmulator = value)
                UiInteractionKind.DIALOG_SHOW.name -> result.copy(dialogShow = value)
                UiInteractionKind.DIALOG_KEYWORD.name -> result.copy(dialogKeyword = value)
                UiInteractionKind.WIDGET_TEXT.name -> result.copy(widgetText = value)
                UiInteractionKind.ACTIVITY_START.name -> result.copy(activityStart = value)
                UiInteractionKind.ACTIVITY_RESUME.name -> result.copy(activityResume = value)
                SystemKind.LOGCAT.name -> result.copy(logcatCapture = value)
                SystemKind.APP_INFO.name -> result.copy(appInfoCapture = value)
                SystemKind.SIGNATURE.name -> result.copy(signatureMonitor = value)
                ProcessKind.EXIT_INTERCEPT.name -> result.copy(exitIntercept = value)
                ProcessKind.CRASH_PREVENT.name -> result.copy(crashPrevent = value)
                ProcessKind.SCREEN_RECORD.name -> result.copy(screenRecordBypass = value)
                // 未知 kind 名:忽略(存储漂移兜底)
                else -> result
            }
        }
        return result
    }

    /**
     * V34 审计增强:检测干预型开关从默认关变为开的状态,输出 Log.w 安全警告。
     * 仅在 override 生效时触发,不影响解析结果。
     */
    private fun logInterventionActivation(result: HookSwitch, default: HookSwitch) {
        val newlyEnabled = INTERVENTION_KINDS.filter { kindId ->
            val enabled = readKind(result, kindId) == true
            val wasDefault = readKind(default, kindId) == true
            enabled && !wasDefault
        }
        if (newlyEnabled.isNotEmpty()) {
            Log.w(TAG, "干预型功能已启用: ${newlyEnabled.joinToString()}")
        }
    }
}
