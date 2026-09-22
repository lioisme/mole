package com.lioisme.modev.core.model


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

    /**
     * 干预启用安全警告出口(V43 T7/V5.1:core 去 Android 依赖——日志经宿主/注入侧
     * 装配时注入,默认无操作;core 保持纯 JVM 可测)。
     */
    @Volatile
    var interventionWarn: (String) -> Unit = {}


    /**
     * 干预型 (类型,id) 对集合(V44 R1-6:FEATURE 域标注派生,消灭手工清单与裸 id 歧义;
     * 供安全审计日志)。
     */
    private val INTERVENTION_KINDS: List<Pair<RecordType, String>> =
        CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE).map { it.type to it.id }

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
        // V47 T2 / 决策 D-12:宿主展开的画像快照覆盖默认快照。
        // 注入侧只读 feature.deviceProfile,**永不按 spoofProfileId 反查画像库**
        // (正文在 Room,而直读两键与磁盘兜底两条通路都不经宿主进程)。
        override.resolvedProfile?.let {
            result = result.copy(feature = result.feature.copy(deviceProfile = it))
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

    /**
     * (类型,id) → 有效开关原始位(V44 限定键路由:非 master 门控;供设置页行值回显)。
     * 跨类型裸 id 碰撞(OPEN:DATABASE/PREFS;SIGNATURE:CRYPTO/SYSTEM)由类型参数消歧,
     * 取代旧字符串分支 first-wins 互踩;未知 (type,id) 返回 null。
     * 与各枚举 → [HookSwitch] 字段的 when 表为读写互逆唯一映射,新增类别必须同步
     * [applyKind] 与本表(编译期穷尽保证两表同枚举项)。
     */
    fun readKind(switch: HookSwitch, type: RecordType, kindId: String): Boolean? = when (type) {
        RecordType.CRYPTO -> CryptoKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                CryptoKind.MESSAGE_DIGEST -> switch.messageDigest
                CryptoKind.CIPHER -> switch.cipher
                CryptoKind.MAC -> switch.mac
                CryptoKind.SIGNATURE -> switch.signature
                CryptoKind.KEY_PARAM -> switch.keyParam
                CryptoKind.KEY_GENERATION -> switch.keyGeneration
                CryptoKind.KEY_FACTORY -> switch.keyFactory
                CryptoKind.KEY_STORE -> switch.keyStore
                CryptoKind.KEY_AGREEMENT -> switch.keyAgreement
                CryptoKind.RANDOM -> switch.random
                CryptoKind.BASE64 -> switch.base64
                CryptoKind.WEB_CRYPTO -> switch.webView
            }
        }

        RecordType.DATABASE -> SqlKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                SqlKind.OPEN -> switch.dbOpen
                SqlKind.QUERY -> switch.dbQuery
                SqlKind.DML -> switch.dbDml
                SqlKind.EXEC -> switch.dbExec
                SqlKind.TRANSACTION -> switch.dbTransaction
            }
        }

        RecordType.BEHAVIOR -> BehaviorKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                BehaviorKind.FILE_READ -> switch.fileRead
                BehaviorKind.FILE_WRITE -> switch.fileWrite
                BehaviorKind.FILE_DELETE -> switch.fileDelete
                BehaviorKind.FILE_RENAME -> switch.fileRename
                BehaviorKind.FILE_LIST -> switch.fileList
                BehaviorKind.ASSET_READ -> switch.assetRead
                BehaviorKind.SHELL_EXEC -> switch.shellExec
            }
        }

        RecordType.PREFS -> PrefsKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                PrefsKind.OPEN -> switch.prefsOpen
                PrefsKind.GET -> switch.prefsGet
                PrefsKind.PUT -> switch.prefsPut
                PrefsKind.REMOVE -> switch.prefsRemove
                PrefsKind.CLEAR -> switch.prefsClear
            }
        }

        RecordType.WEBVIEW -> WebViewKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                WebViewKind.PAGE_LOAD -> switch.wvPageLoad
                WebViewKind.EVALUATE_JS -> switch.wvEvaluateJs
                WebViewKind.JS_BRIDGE -> switch.wvJsBridge
                WebViewKind.DEBUG -> switch.wvDebug
                WebViewKind.JS_CRYPTO -> switch.wvJsCrypto
            }
        }

        RecordType.NETWORK -> NetworkKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                NetworkKind.SSL_CONTEXT -> switch.sslContext
                NetworkKind.SSL_TRUST_MANAGER -> switch.sslTrustManager
                NetworkKind.SSL_OKHTTP_PINNER -> switch.sslOkhttpPinner
                NetworkKind.SSL_OKHTTP_VERIFIER -> switch.sslOkhttpVerifier
                NetworkKind.SSL_WEBVIEW -> switch.sslWebview
                NetworkKind.SSL_NETWORK_CONFIG -> switch.sslNetworkConfig
                NetworkKind.PROXY_SYSTEM_PROP -> switch.proxySystemProp
                NetworkKind.PROXY_CONNECTIVITY -> switch.proxyConnectivity
                NetworkKind.PROXY_SETTINGS -> switch.proxySettings
                NetworkKind.PROXY_OKHTTP -> switch.proxyOkhttp
                NetworkKind.VPN_CAPABILITIES -> switch.vpnCapabilities
                NetworkKind.VPN_INTERFACE -> switch.vpnInterface
                NetworkKind.VPN_NETWORK_INFO -> switch.vpnNetworkInfo
                NetworkKind.VPN_LINK_PROPERTIES -> switch.vpnLinkProperties
                NetworkKind.REQUEST -> switch.networkRequest
            }
        }

        RecordType.DEVICE_ENV -> DeviceEnvKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                DeviceEnvKind.ROOT_FILE -> switch.rootFile
                DeviceEnvKind.ROOT_EXEC -> switch.rootExec
                DeviceEnvKind.ROOT_PACKAGE_MANAGER -> switch.rootPackageManager
                DeviceEnvKind.ROOT_PROPERTY -> switch.rootProperty
                DeviceEnvKind.XPOSED_CLASS -> switch.xposedClass
                DeviceEnvKind.XPOSED_STACK -> switch.xposedStack
                DeviceEnvKind.XPOSED_MAPS -> switch.xposedMaps
                DeviceEnvKind.EMULATOR_BUILD -> switch.emulatorBuild
                DeviceEnvKind.EMULATOR_FILE -> switch.emulatorFile
                DeviceEnvKind.EMULATOR_SENSOR -> switch.emulatorSensor
                DeviceEnvKind.EMULATOR_NETWORK -> switch.emulatorNetwork
                DeviceEnvKind.EMULATOR_TELEPHONY -> switch.emulatorTelephony
                DeviceEnvKind.EMULATOR_CPU -> switch.emulatorCpu
                DeviceEnvKind.EMULATOR_BATTERY -> switch.emulatorBattery
                DeviceEnvKind.SYSTEM_PROPERTIES -> switch.systemProperties
                DeviceEnvKind.PROC_STATUS -> switch.procStatus
                DeviceEnvKind.PROC_MOUNT_INFO -> switch.procMountInfo
                DeviceEnvKind.PROC_CGROUP -> switch.procCgroup
                DeviceEnvKind.PROC_NET -> switch.procNet
                DeviceEnvKind.DETECT_ROOT -> switch.detectRoot
                DeviceEnvKind.DETECT_EMULATOR -> switch.detectEmulator
            }
        }

        RecordType.UI_INTERACTION -> UiInteractionKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                UiInteractionKind.DIALOG_SHOW -> switch.dialogShow
                UiInteractionKind.DIALOG_KEYWORD -> switch.dialogKeyword
                UiInteractionKind.WIDGET_TEXT -> switch.widgetText
                UiInteractionKind.ACTIVITY_START -> switch.activityStart
                UiInteractionKind.ACTIVITY_RESUME -> switch.activityResume
            }
        }

        RecordType.SYSTEM -> SystemKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                SystemKind.LOGCAT -> switch.logcatCapture
                SystemKind.APP_INFO -> switch.appInfoCapture
                SystemKind.SIGNATURE -> switch.signatureMonitor
            }
        }

        RecordType.PROCESS_CONTROL -> ProcessKind.entries.firstOrNull { it.name == kindId }?.let { k ->
            when (k) {
                ProcessKind.EXIT_INTERCEPT -> switch.exitIntercept
                ProcessKind.CRASH_PREVENT -> switch.crashPrevent
                ProcessKind.SCREEN_RECORD -> switch.screenRecordBypass
            }
        }
    }

    /**
     * 类别偏差 → [HookSwitch] 平铺字段映射(与 [readKind] 严格互逆,须同步维护)。
     * 键经 [AppListenOverride.parseKindKey] 归一(限定形态直解;遗留裸 id 按序首类型解析),
     * 无法解析的键忽略(存储漂移兜底)。
     */
    private fun applyKinds(
        switch: HookSwitch,
        kinds: Map<String, Boolean>,
    ): HookSwitch {
        var result = switch
        for ((key, value) in kinds) {
            val parsed = AppListenOverride.parseKindKey(key) ?: continue
            result = applyKind(result, parsed.first, parsed.second, value)
        }
        return result
    }

    /** (类型,id,值) → 单字段覆写(未知 (type,id) 原样返回,漂移安全)。 */
    private fun applyKind(switch: HookSwitch, type: RecordType, kindId: String, value: Boolean): HookSwitch =
        when (type) {
            RecordType.CRYPTO -> CryptoKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    CryptoKind.MESSAGE_DIGEST -> switch.copy(messageDigest = value)
                    CryptoKind.CIPHER -> switch.copy(cipher = value)
                    CryptoKind.MAC -> switch.copy(mac = value)
                    CryptoKind.SIGNATURE -> switch.copy(signature = value)
                    CryptoKind.KEY_PARAM -> switch.copy(keyParam = value)
                    CryptoKind.KEY_GENERATION -> switch.copy(keyGeneration = value)
                    CryptoKind.KEY_FACTORY -> switch.copy(keyFactory = value)
                    CryptoKind.KEY_STORE -> switch.copy(keyStore = value)
                    CryptoKind.KEY_AGREEMENT -> switch.copy(keyAgreement = value)
                    CryptoKind.RANDOM -> switch.copy(random = value)
                    CryptoKind.BASE64 -> switch.copy(base64 = value)
                    CryptoKind.WEB_CRYPTO -> switch.copy(webView = value)
                }
            } ?: switch

            RecordType.DATABASE -> SqlKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    SqlKind.OPEN -> switch.copy(dbOpen = value)
                    SqlKind.QUERY -> switch.copy(dbQuery = value)
                    SqlKind.DML -> switch.copy(dbDml = value)
                    SqlKind.EXEC -> switch.copy(dbExec = value)
                    SqlKind.TRANSACTION -> switch.copy(dbTransaction = value)
                }
            } ?: switch

            RecordType.BEHAVIOR -> BehaviorKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    BehaviorKind.FILE_READ -> switch.copy(fileRead = value)
                    BehaviorKind.FILE_WRITE -> switch.copy(fileWrite = value)
                    BehaviorKind.FILE_DELETE -> switch.copy(fileDelete = value)
                    BehaviorKind.FILE_RENAME -> switch.copy(fileRename = value)
                    BehaviorKind.FILE_LIST -> switch.copy(fileList = value)
                    BehaviorKind.ASSET_READ -> switch.copy(assetRead = value)
                    BehaviorKind.SHELL_EXEC -> switch.copy(shellExec = value)
                }
            } ?: switch

            RecordType.PREFS -> PrefsKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    PrefsKind.OPEN -> switch.copy(prefsOpen = value)
                    PrefsKind.GET -> switch.copy(prefsGet = value)
                    PrefsKind.PUT -> switch.copy(prefsPut = value)
                    PrefsKind.REMOVE -> switch.copy(prefsRemove = value)
                    PrefsKind.CLEAR -> switch.copy(prefsClear = value)
                }
            } ?: switch

            RecordType.WEBVIEW -> WebViewKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    WebViewKind.PAGE_LOAD -> switch.copy(wvPageLoad = value)
                    WebViewKind.EVALUATE_JS -> switch.copy(wvEvaluateJs = value)
                    WebViewKind.JS_BRIDGE -> switch.copy(wvJsBridge = value)
                    WebViewKind.DEBUG -> switch.copy(wvDebug = value)
                    WebViewKind.JS_CRYPTO -> switch.copy(wvJsCrypto = value)
                }
            } ?: switch

            RecordType.NETWORK -> NetworkKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    NetworkKind.SSL_CONTEXT -> switch.copy(sslContext = value)
                    NetworkKind.SSL_TRUST_MANAGER -> switch.copy(sslTrustManager = value)
                    NetworkKind.SSL_OKHTTP_PINNER -> switch.copy(sslOkhttpPinner = value)
                    NetworkKind.SSL_OKHTTP_VERIFIER -> switch.copy(sslOkhttpVerifier = value)
                    NetworkKind.SSL_WEBVIEW -> switch.copy(sslWebview = value)
                    NetworkKind.SSL_NETWORK_CONFIG -> switch.copy(sslNetworkConfig = value)
                    NetworkKind.PROXY_SYSTEM_PROP -> switch.copy(proxySystemProp = value)
                    NetworkKind.PROXY_CONNECTIVITY -> switch.copy(proxyConnectivity = value)
                    NetworkKind.PROXY_SETTINGS -> switch.copy(proxySettings = value)
                    NetworkKind.PROXY_OKHTTP -> switch.copy(proxyOkhttp = value)
                    NetworkKind.VPN_CAPABILITIES -> switch.copy(vpnCapabilities = value)
                    NetworkKind.VPN_INTERFACE -> switch.copy(vpnInterface = value)
                    NetworkKind.VPN_NETWORK_INFO -> switch.copy(vpnNetworkInfo = value)
                    NetworkKind.VPN_LINK_PROPERTIES -> switch.copy(vpnLinkProperties = value)
                    NetworkKind.REQUEST -> switch.copy(networkRequest = value)
                }
            } ?: switch

            RecordType.DEVICE_ENV -> DeviceEnvKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    DeviceEnvKind.ROOT_FILE -> switch.copy(rootFile = value)
                    DeviceEnvKind.ROOT_EXEC -> switch.copy(rootExec = value)
                    DeviceEnvKind.ROOT_PACKAGE_MANAGER -> switch.copy(rootPackageManager = value)
                    DeviceEnvKind.ROOT_PROPERTY -> switch.copy(rootProperty = value)
                    DeviceEnvKind.XPOSED_CLASS -> switch.copy(xposedClass = value)
                    DeviceEnvKind.XPOSED_STACK -> switch.copy(xposedStack = value)
                    DeviceEnvKind.XPOSED_MAPS -> switch.copy(xposedMaps = value)
                    DeviceEnvKind.EMULATOR_BUILD -> switch.copy(emulatorBuild = value)
                    DeviceEnvKind.EMULATOR_FILE -> switch.copy(emulatorFile = value)
                    DeviceEnvKind.EMULATOR_SENSOR -> switch.copy(emulatorSensor = value)
                    DeviceEnvKind.EMULATOR_NETWORK -> switch.copy(emulatorNetwork = value)
                    DeviceEnvKind.EMULATOR_TELEPHONY -> switch.copy(emulatorTelephony = value)
                    DeviceEnvKind.EMULATOR_CPU -> switch.copy(emulatorCpu = value)
                    DeviceEnvKind.EMULATOR_BATTERY -> switch.copy(emulatorBattery = value)
                    DeviceEnvKind.SYSTEM_PROPERTIES -> switch.copy(systemProperties = value)
                    DeviceEnvKind.PROC_STATUS -> switch.copy(procStatus = value)
                    DeviceEnvKind.PROC_MOUNT_INFO -> switch.copy(procMountInfo = value)
                    DeviceEnvKind.PROC_CGROUP -> switch.copy(procCgroup = value)
                    DeviceEnvKind.PROC_NET -> switch.copy(procNet = value)
                    DeviceEnvKind.DETECT_ROOT -> switch.copy(detectRoot = value)
                    DeviceEnvKind.DETECT_EMULATOR -> switch.copy(detectEmulator = value)
                }
            } ?: switch

            RecordType.UI_INTERACTION -> UiInteractionKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    UiInteractionKind.DIALOG_SHOW -> switch.copy(dialogShow = value)
                    UiInteractionKind.DIALOG_KEYWORD -> switch.copy(dialogKeyword = value)
                    UiInteractionKind.WIDGET_TEXT -> switch.copy(widgetText = value)
                    UiInteractionKind.ACTIVITY_START -> switch.copy(activityStart = value)
                    UiInteractionKind.ACTIVITY_RESUME -> switch.copy(activityResume = value)
                }
            } ?: switch

            RecordType.SYSTEM -> SystemKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    SystemKind.LOGCAT -> switch.copy(logcatCapture = value)
                    SystemKind.APP_INFO -> switch.copy(appInfoCapture = value)
                    SystemKind.SIGNATURE -> switch.copy(signatureMonitor = value)
                }
            } ?: switch

            RecordType.PROCESS_CONTROL -> ProcessKind.entries.firstOrNull { it.name == kindId }?.let { k ->
                when (k) {
                    ProcessKind.EXIT_INTERCEPT -> switch.copy(exitIntercept = value)
                    ProcessKind.CRASH_PREVENT -> switch.copy(crashPrevent = value)
                    ProcessKind.SCREEN_RECORD -> switch.copy(screenRecordBypass = value)
                }
            } ?: switch
        }

    /**
     * V34 审计增强:检测干预型开关从默认关变为开的状态,输出 Log.w 安全警告。
     * 仅在 override 生效时触发,不影响解析结果。
     */
    private fun logInterventionActivation(result: HookSwitch, default: HookSwitch) {
        val newlyEnabled = INTERVENTION_KINDS.filter { (type, kindId) ->
            val enabled = readKind(result, type, kindId) == true
            val wasDefault = readKind(default, type, kindId) == true
            enabled && !wasDefault
        }
        if (newlyEnabled.isNotEmpty()) {
            interventionWarn(
                "干预型功能已启用: ${newlyEnabled.joinToString { (t, id) -> "${t.wire}:$id" }}",
            )
        }
    }
}
