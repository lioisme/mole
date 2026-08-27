package com.lioisme.modev.core.model

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 全局捕获配置(V10:全应用共享一份,与包名解耦;V17 数据库监听扩展;
 * V18 算法/数据库监听全局总开关)。
 * 宿主 App 持久化于 `switch_global` 并经通道推送,注入进程实时读取更新。
 */
data class HookSwitch(
    /** 算法监听全局总开关:false = 全部算法类别停用(分开关保留原态,重开即恢复)。 */
    val cryptoListen: Boolean = true,
    /** 数据库监听全局总开关:false = 全部数据库类别停用(分开关保留原态,重开即恢复)。 */
    val dbListen: Boolean = true,
    val messageDigest: Boolean = true,
    val cipher: Boolean = true,
    val mac: Boolean = true,
    val signature: Boolean = true,
    /** 密钥参数构造:高频、逆向价值低,默认关(噪点类别,见 [CategoryTaxonomy])。 */
    val keyParam: Boolean = false,
    /** 密钥生成:高频、逆向价值低,默认关(噪点类别)。 */
    val keyGeneration: Boolean = false,
    /** 密钥还原:高频、逆向价值低,默认关(噪点类别)。 */
    val keyFactory: Boolean = false,
    /** 密钥库:高频、逆向价值低,默认关(噪点类别)。 */
    val keyStore: Boolean = false,
    /** 密钥协商:高频、逆向价值低,默认关(噪点类别)。 */
    val keyAgreement: Boolean = false,
    /** 随机数:极高频、逆向价值低,默认关(噪点类别)。 */
    val random: Boolean = false,
    /** Base64 编码:纯编码噪声,默认关(噪点类别,与 webView 同先例)。 */
    val base64: Boolean = false,
    /** WebView JS 加解密监听(WebCrypto / CryptoJS / JSEncrypt,V12 起;默认关,同 base64 先例)。 */
    val webView: Boolean = false,
    /**
     * 数据库监听:打开/关闭(openDatabase/openOrCreateDatabase/SQLiteOpenHelper)。
     * 高频、信号弱(仅生命周期),默认关(噪点类别,见 [CategoryTaxonomy])。
     */
    val dbOpen: Boolean = false,
    /** 数据库监听:查询(query/rawQuery,含结果集被动采集)。高信号,默认启用。 */
    val dbQuery: Boolean = true,
    /** 数据库监听:增删改便捷方法(insert/replace/update/delete)。高信号,默认启用。 */
    val dbDml: Boolean = true,
    /** 数据库监听:原生 SQL 执行(execSQL / SQLiteStatement.execute* / attach)。高信号,默认启用。 */
    val dbExec: Boolean = true,
    /**
     * 数据库监听:事务(begin/end/setTransactionSuccessful)。
     * 高频、信号弱,默认关(噪点类别)。
     */
    val dbTransaction: Boolean = false,
    /** 行为监听全局总开关(V27;false = 全部行为类别停用,分开关保留原态)。 */
    val behaviorListen: Boolean = true,
    /**
     * 行为监听:文件读取(FileInputStream/FileReader/Files.newInputStream/readAllBytes)。
     * 极高频(框架启动即数百次),默认关(噪点类别)。
     */
    val fileRead: Boolean = false,
    /** 行为监听:文件写出/创建(FileOutputStream/Files.write/mkdir 族)。高信号,默认启用。 */
    val fileWrite: Boolean = true,
    /** 行为监听:文件删除(File.delete/Files.delete)。高信号,默认启用。 */
    val fileDelete: Boolean = true,
    /** 行为监听:重命名/移动(File.renameTo/Files.move)。高信号低频,默认启用。 */
    val fileRename: Boolean = true,
    /** 行为监听:目录列举(File.list/listFiles/Files.list)。高频低值,默认关。 */
    val fileList: Boolean = false,
    /** 行为监听:Assets 资源读取(AssetManager.open/openFd/list)。启动期高频,默认关。 */
    val assetRead: Boolean = false,
    /** 行为监听:Shell 命令执行(Runtime.exec/ProcessBuilder.start)。最高信号,默认启用。 */
    val shellExec: Boolean = true,
    /** 偏好监听全局总开关(V28;false = 全部偏好类别停用,分开关保留原态)。 */
    val prefsListen: Boolean = true,
    /** WebView 监听全局总开关(V29 一等公民;false = 全部 WebView 类别停用)。 */
    val webviewListen: Boolean = true,
    /** WebView:页面加载(loadUrl/loadData/loadDataWithBaseURL)。低频高信号,默认启用。 */
    val wvPageLoad: Boolean = true,
    /** WebView:JS 执行(evaluateJavascript)。高信号,默认启用。 */
    val wvEvaluateJs: Boolean = true,
    /** WebView:JS 桥注册(addJavascriptInterface)。高信号,默认启用。 */
    val wvJsBridge: Boolean = true,
    /** WebView:调试开关(setWebContentsDebuggingEnabled)。高信号,默认启用。 */
    val wvDebug: Boolean = true,
    /** WebView:JS 密码学包装(WebCrypto/CryptoJS/JSEncrypt)。高频噪声,默认关。 */
    val wvJsCrypto: Boolean = false,
    /**
     * 偏好监听:读入配置文件(SharedPreferencesImpl 构造)。
     * 进程内每文件仅一次,低频高信号,默认启用(决策 D4)。
     */
    val prefsOpen: Boolean = true,
    /** 偏好监听:配置读取(get* 全家桶/contains/getAll)。极高频,默认关(噪点类别)。 */
    val prefsGet: Boolean = false,
    /** 偏好监听:配置写入(editor.put* 全家桶)。高信号,默认启用。 */
    val prefsPut: Boolean = true,
    /** 偏好监听:单键删除(editor.remove)。低频,默认启用。 */
    val prefsRemove: Boolean = true,
    /** 偏好监听:全量清空(editor.clear)。低频,默认启用。 */
    val prefsClear: Boolean = true,
    /** 数据库监听:单条查询结果行数上限;0 = 不限制(不推荐,结果闸门双保险)。 */
    val maxResultRows: Int = RecordLimits.DEFAULT_MAX_RESULT_ROWS,
    /** 数据库监听:单条查询结果序列化字符上限;0 = 不限制(不推荐)。 */
    val maxResultChars: Int = RecordLimits.DEFAULT_MAX_RESULT_CHARS,
    val logStack: Boolean = true,
    val maxStackDepth: Int = 24,
    /** 单字段字节上限(全量捕获保险丝);0 表示无限。 */
    val maxBytesPerField: Int = RecordLimits.DEFAULT_MAX_BYTES_PER_FIELD,
    /** 调用栈前缀 denylist:任一帧命中即跳过该记录;默认空 = 全捕获。 */
    val ignoreStackPrefixes: List<String> = emptyList(),
    /**
     * 文件内容捕获(V27):开启后仅对「随调用即可得」的整读 API
     * (Files.readAllBytes/readAllLines/write/copy 与 AssetManager.open)
     * 按 [maxBytesPerField] 截断捕获正文预览;流构造器路径绝不触碰句柄(被动红线)。
     * 默认关。
     */
    val fileContentCapture: Boolean = false,
    /**
     * 路径前缀 denylist(V27 行为降噪):主体路径命中任一前缀即跳过该记录;
     * 与内置系统路径清单叠加生效,默认空。
     */
    val ignorePathPrefixes: List<String> = emptyList(),

    // ---- V30 网络环境监听 ----

    /** 网络环境监听全局总开关(V30;false = 全部网络类别停用,分开关保留原态)。 */
    val networkListen: Boolean = true,
    /** SSL:SSLContext.init TrustManager 替换。 */
    val sslContext: Boolean = true,
    /** SSL:Conscrypt TrustManagerImpl 拦截。 */
    val sslTrustManager: Boolean = true,
    /** SSL:OkHttp CertificatePinner.check 拦截(含混淆发现)。 */
    val sslOkhttpPinner: Boolean = true,
    /** SSL:OkHttp OkHostnameVerifier.verify 拦截。 */
    val sslOkhttpVerifier: Boolean = true,
    /** SSL:WebView onReceivedSslError 自动 proceed。 */
    val sslWebview: Boolean = true,
    /** SSL:NetworkSecurityConfig pin 绕过。 */
    val sslNetworkConfig: Boolean = true,
    /** 代理:System.getProperty 代理属性隐藏。 */
    val proxySystemProp: Boolean = true,
    /** 代理:ConnectivityManager 代理查询隐藏。 */
    val proxyConnectivity: Boolean = true,
    /** 代理:Settings.Global 代理隐藏。 */
    val proxySettings: Boolean = true,
    /** 代理:OkHttp ProxySelector 隐藏。 */
    val proxyOkhttp: Boolean = true,
    /** VPN:NetworkCapabilities TRANSPORT_VPN 隐藏。 */
    val vpnCapabilities: Boolean = true,
    /** VPN:NetworkInterface VPN 接口隐藏。 */
    val vpnInterface: Boolean = true,
    /** VPN:NetworkInfo TYPE_VPN 隐藏。 */
    val vpnNetworkInfo: Boolean = true,
    /** VPN:LinkProperties VPN 路由隐藏。 */
    val vpnLinkProperties: Boolean = true,
    /** 网络请求监控(含 Reqable 堆栈)。 */
    val networkRequest: Boolean = true,

    // ---- V31 设备环境监听 ----

    /** 设备环境监听全局总开关(V31;false = 全部设备环境类别停用,分开关保留原态)。 */
    val deviceEnvListen: Boolean = true,
    /** Root:文件存在性检测干预(su/busybox/magisk 路径)。 */
    val rootFile: Boolean = true,
    /** Root:命令执行检测干预(Runtime.exec su)。 */
    val rootExec: Boolean = true,
    /** Root:包管理器查询干预(root 管理 app 过滤)。 */
    val rootPackageManager: Boolean = true,
    /** Root:系统属性检测干预(Build.TAGS/ro.debuggable 等)。 */
    val rootProperty: Boolean = true,
    /** Xposed:类存在性检测干预(Class.forName XposedBridge)。 */
    val xposedClass: Boolean = true,
    /** Xposed:堆栈检测干预(调用栈含 xposed 帧)。 */
    val xposedStack: Boolean = true,
    /** Xposed:/proc/self/maps 扫描干预。 */
    val xposedMaps: Boolean = true,
    /** 模拟器:Build 属性伪造(fingerprint/model/product)。 */
    val emulatorBuild: Boolean = true,
    /** 模拟器:设备文件检测干预(qemu_pipe/goldfish_pipe)。 */
    val emulatorFile: Boolean = true,
    /** 模拟器:传感器伪造(假加速度计/陀螺仪)。 */
    val emulatorSensor: Boolean = true,
    /** 模拟器:网络/MAC/IMEI 伪造。 */
    val emulatorNetwork: Boolean = true,
    /** 设备环境:Root 检测行为监控(仅记录,不干预;默认关,噪声大)。 */
    val detectRoot: Boolean = false,
    /** 设备环境:模拟器检测行为监控(仅记录,不干预;默认关,噪声大)。 */
    val detectEmulator: Boolean = false,

    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxHexChars: Int = RecordLimits.DEFAULT_MAX_HEX_CHARS,
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxTextChars: Int = RecordLimits.DEFAULT_MAX_TEXT_CHARS,
) {
    /** 是否存在任何需要 Hook 的类别(算法 + 数据库 + 行为 + 偏好 + WebView + 网络 + 设备环境 任一能力启用)。 */
    val anyEnabled: Boolean
        get() = anyCryptoEnabled || anyDbEnabled || anyBehaviorEnabled || anyPrefsEnabled ||
            anyWebviewEnabled || anyNetworkEnabled || anyDeviceEnvEnabled

    /** 算法捕获相关类别是否任一启用(含全局总开关投影,供算法监听能力判定)。 */
    val anyCryptoEnabled: Boolean
        get() = cryptoListen && (messageDigest || cipher || mac || signature || keyParam || keyGeneration ||
            keyFactory || keyStore || keyAgreement || random || base64 || webView)

    /** 数据库监听相关类别是否任一启用(含全局总开关投影,供数据库监听能力判定)。 */
    val anyDbEnabled: Boolean
        get() = dbListen && (dbOpen || dbQuery || dbDml || dbExec || dbTransaction)

    /** 行为监听相关类别是否任一启用(含全局总开关投影,供行为监听能力判定)。 */
    val anyBehaviorEnabled: Boolean
        get() = behaviorListen && (fileRead || fileWrite || fileDelete || fileRename ||
            fileList || assetRead || shellExec)

    /** 偏好监听相关类别是否任一启用(含全局总开关投影,供偏好监听能力判定)。 */
    val anyPrefsEnabled: Boolean
        get() = prefsListen && (prefsOpen || prefsGet || prefsPut || prefsRemove || prefsClear)

    /** WebView 监听相关类别是否任一启用(含全局总开关投影,供 WebView 监听能力判定)。 */
    val anyWebviewEnabled: Boolean
        get() = webviewListen && (wvPageLoad || wvEvaluateJs || wvJsBridge || wvDebug || wvJsCrypto)

    /** 网络环境监听相关类别是否任一启用(含全局总开关投影,供网络监听能力判定)。 */
    val anyNetworkEnabled: Boolean
        get() = networkListen && (sslContext || sslTrustManager || sslOkhttpPinner ||
            sslOkhttpVerifier || sslWebview || sslNetworkConfig ||
            proxySystemProp || proxyConnectivity || proxySettings || proxyOkhttp ||
            vpnCapabilities || vpnInterface || vpnNetworkInfo || vpnLinkProperties ||
            networkRequest)

    /** 设备环境监听相关类别是否任一启用(含全局总开关投影,供设备环境监听能力判定)。 */
    val anyDeviceEnvEnabled: Boolean
        get() = deviceEnvListen && (rootFile || rootExec || rootPackageManager || rootProperty ||
            xposedClass || xposedStack || xposedMaps ||
            emulatorBuild || emulatorFile || emulatorSensor || emulatorNetwork ||
            detectRoot || detectEmulator)

    fun enabled(kind: CryptoKind): Boolean = cryptoListen && when (kind) {
        CryptoKind.MESSAGE_DIGEST -> messageDigest
        CryptoKind.CIPHER -> cipher
        CryptoKind.MAC -> mac
        CryptoKind.SIGNATURE -> signature
        CryptoKind.KEY_PARAM -> keyParam
        CryptoKind.KEY_GENERATION -> keyGeneration
        CryptoKind.KEY_FACTORY -> keyFactory
        CryptoKind.KEY_STORE -> keyStore
        CryptoKind.KEY_AGREEMENT -> keyAgreement
        CryptoKind.RANDOM -> random
        CryptoKind.BASE64 -> base64
        CryptoKind.WEB_CRYPTO -> webView
    }

    fun dbEnabled(kind: SqlKind): Boolean = dbListen && when (kind) {
        SqlKind.OPEN -> dbOpen
        SqlKind.QUERY -> dbQuery
        SqlKind.DML -> dbDml
        SqlKind.EXEC -> dbExec
        SqlKind.TRANSACTION -> dbTransaction
    }

    /** 行为类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun behaviorEnabled(kind: BehaviorKind): Boolean = behaviorListen && when (kind) {
        BehaviorKind.FILE_READ -> fileRead
        BehaviorKind.FILE_WRITE -> fileWrite
        BehaviorKind.FILE_DELETE -> fileDelete
        BehaviorKind.FILE_RENAME -> fileRename
        BehaviorKind.FILE_LIST -> fileList
        BehaviorKind.ASSET_READ -> assetRead
        BehaviorKind.SHELL_EXEC -> shellExec
    }

    /** 偏好类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun prefsEnabled(kind: PrefsKind): Boolean = prefsListen && when (kind) {
        PrefsKind.OPEN -> prefsOpen
        PrefsKind.GET -> prefsGet
        PrefsKind.PUT -> prefsPut
        PrefsKind.REMOVE -> prefsRemove
        PrefsKind.CLEAR -> prefsClear
    }

    /** WebView 类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun webviewEnabled(kind: WebViewKind): Boolean = webviewListen && when (kind) {
        WebViewKind.PAGE_LOAD -> wvPageLoad
        WebViewKind.EVALUATE_JS -> wvEvaluateJs
        WebViewKind.JS_BRIDGE -> wvJsBridge
        WebViewKind.DEBUG -> wvDebug
        WebViewKind.JS_CRYPTO -> wvJsCrypto
    }

    /** 网络类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun networkEnabled(kind: NetworkKind): Boolean = networkListen && when (kind) {
        NetworkKind.SSL_CONTEXT -> sslContext
        NetworkKind.SSL_TRUST_MANAGER -> sslTrustManager
        NetworkKind.SSL_OKHTTP_PINNER -> sslOkhttpPinner
        NetworkKind.SSL_OKHTTP_VERIFIER -> sslOkhttpVerifier
        NetworkKind.SSL_WEBVIEW -> sslWebview
        NetworkKind.SSL_NETWORK_CONFIG -> sslNetworkConfig
        NetworkKind.PROXY_SYSTEM_PROP -> proxySystemProp
        NetworkKind.PROXY_CONNECTIVITY -> proxyConnectivity
        NetworkKind.PROXY_SETTINGS -> proxySettings
        NetworkKind.PROXY_OKHTTP -> proxyOkhttp
        NetworkKind.VPN_CAPABILITIES -> vpnCapabilities
        NetworkKind.VPN_INTERFACE -> vpnInterface
        NetworkKind.VPN_NETWORK_INFO -> vpnNetworkInfo
        NetworkKind.VPN_LINK_PROPERTIES -> vpnLinkProperties
        NetworkKind.REQUEST -> networkRequest
    }

    /** 设备环境类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun deviceEnvEnabled(kind: DeviceEnvKind): Boolean = deviceEnvListen && when (kind) {
        DeviceEnvKind.ROOT_FILE -> rootFile
        DeviceEnvKind.ROOT_EXEC -> rootExec
        DeviceEnvKind.ROOT_PACKAGE_MANAGER -> rootPackageManager
        DeviceEnvKind.ROOT_PROPERTY -> rootProperty
        DeviceEnvKind.XPOSED_CLASS -> xposedClass
        DeviceEnvKind.XPOSED_STACK -> xposedStack
        DeviceEnvKind.XPOSED_MAPS -> xposedMaps
        DeviceEnvKind.EMULATOR_BUILD -> emulatorBuild
        DeviceEnvKind.EMULATOR_FILE -> emulatorFile
        DeviceEnvKind.EMULATOR_SENSOR -> emulatorSensor
        DeviceEnvKind.EMULATOR_NETWORK -> emulatorNetwork
        DeviceEnvKind.DETECT_ROOT -> detectRoot
        DeviceEnvKind.DETECT_EMULATOR -> detectEmulator
    }

    companion object {
        val DEFAULT = HookSwitch()

        /**
         * Gson 反序列化 + 缺字段默认值补齐:新增字段(cryptoListen/dbListen 等)
         * 在旧配置 JSON 中缺失时,Gson 的 Unsafe 实例化会给 JVM 默认值(false),
         * 会误把历史配置判为「全部停用」。此处按 JsonObject 键存在性补齐默认值。
         */
        fun fromJson(gson: Gson, json: String): HookSwitch? = runCatching {
            val base = gson.fromJson(json, HookSwitch::class.java) ?: return null
            val obj = gson.fromJson(json, JsonObject::class.java)
            base.copy(
                cryptoListen = obj?.get("cryptoListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                dbListen = obj?.get("dbListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                behaviorListen = obj?.get("behaviorListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                prefsListen = obj?.get("prefsListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                webviewListen = obj?.get("webviewListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                networkListen = obj?.get("networkListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                deviceEnvListen = obj?.get("deviceEnvListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
            )
        }.getOrNull()
    }
}