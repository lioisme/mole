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
    /**
     * 调用栈采集(V44 D-V44-2:默认 **false**)。
     *
     * 默认预算最大单项:每条开启态记录都在 hook 线程同步做全栈捕获 + denylist 过滤
     * + 24 帧字符串化([com.lioisme.modev.core.util.StackTraceFilter]),是宿主线程
     * 最大的单点固定开销。需要调用链溯源时逐应用显式开启。
     */
    val logStack: Boolean = false,
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
    /** SSL:SSLContext.init TrustManager 替换(干预型,默认关)。 */
    val sslContext: Boolean = false,
    /** SSL:Conscrypt TrustManagerImpl 拦截(干预型,默认关)。 */
    val sslTrustManager: Boolean = false,
    /** SSL:OkHttp CertificatePinner.check 拦截(含混淆发现,干预型,默认关)。 */
    val sslOkhttpPinner: Boolean = false,
    /** SSL:OkHttp OkHostnameVerifier.verify 拦截(干预型,默认关)。 */
    val sslOkhttpVerifier: Boolean = false,
    /** SSL:WebView onReceivedSslError 自动 proceed(干预型,默认关)。 */
    val sslWebview: Boolean = false,
    /** SSL:NetworkSecurityConfig pin 绕过(干预型,默认关)。 */
    val sslNetworkConfig: Boolean = false,
    /** 代理:System.getProperty 代理属性隐藏(干预型,默认关)。 */
    val proxySystemProp: Boolean = false,
    /** 代理:ConnectivityManager 代理查询隐藏(干预型,默认关)。 */
    val proxyConnectivity: Boolean = false,
    /** 代理:Settings.Global 代理隐藏(干预型,默认关)。 */
    val proxySettings: Boolean = false,
    /** 代理:OkHttp ProxySelector 隐藏(干预型,默认关)。 */
    val proxyOkhttp: Boolean = false,
    /** VPN:NetworkCapabilities TRANSPORT_VPN 隐藏(干预型,默认关)。 */
    val vpnCapabilities: Boolean = false,
    /** VPN:NetworkInterface VPN 接口隐藏(干预型,默认关)。 */
    val vpnInterface: Boolean = false,
    /** VPN:NetworkInfo TYPE_VPN 隐藏(干预型,默认关)。 */
    val vpnNetworkInfo: Boolean = false,
    /** VPN:LinkProperties VPN 路由隐藏(干预型,默认关)。 */
    val vpnLinkProperties: Boolean = false,
    /** 网络请求监控(仅记录,不干预,默认开)。 */
    val networkRequest: Boolean = true,

    // ---- V31 设备环境监听 ----

    /** 设备环境监听全局总开关(V31;false = 全部设备环境类别停用,分开关保留原态)。 */
    val deviceEnvListen: Boolean = true,

    // ---- V33 UI 交互监听 ----

    /** UI 交互监听全局总开关(V33;false = 全部 UI 交互类别停用,分开关保留原态)。 */
    val uiListen: Boolean = true,
    /** UI:Dialog.show() 记录(低频高信号,默认开)。 */
    val dialogShow: Boolean = true,
    /** UI:关键字弹窗拦截记录(低频高信号,默认开)。 */
    val dialogKeyword: Boolean = true,
    /** UI:TextView.setText() 文本赋值记录(极高频噪声,默认关)。 */
    val widgetText: Boolean = false,
    /** UI:Activity.startActivity() 启动记录(低频高信号,默认开)。 */
    val activityStart: Boolean = true,
    /** UI:Activity 生命周期记录(高频,默认关)。 */
    val activityResume: Boolean = false,

    // ---- V33 系统信息监听 ----

    /** 系统信息监听全局总开关(V33;false = 全部系统信息类别停用,分开关保留原态)。 */
    val systemListen: Boolean = true,
    /** 系统:Logcat 输出捕获(极高频噪声,默认关)。 */
    val logcatCapture: Boolean = false,
    /** 系统:Application 元数据采集(进程启动一次,低频高信号,默认开)。 */
    val appInfoCapture: Boolean = true,
    /** 系统:签名读取监听(低频高信号,默认开)。 */
    val signatureMonitor: Boolean = true,

    // ---- V33 进程控制 ----

    /** 进程控制全局总开关(V33;false = 全部进程控制类别停用,分开关保留原态)。 */
    val processControlListen: Boolean = true,
    /** 进程:退出检测与拦截 System.exit/killProcess(干预型,默认关;before() 中 setResult(null) 阻止执行)。 */
    val exitIntercept: Boolean = false,
    /** 进程:崩溃检测与预防未捕获异常(干预型,默认关;注入 wrapper handler + 吞掉异常)。 */
    val crashPrevent: Boolean = false,
    /** 进程:录屏限制检测与解除 FLAG_SECURE(干预型,默认关;before() 中清除 FLAG_SECURE 位)。 */
    val screenRecordBypass: Boolean = false,

    // ---- V33 UI 交互微调项 ----

    /**
     * 弹窗拦截关键字列表(空 = 不拦截;命中任一关键字即拦截)。
     * @deprecated V37 T1.2:迁移至 [FeatureConfig.dialogKeywords];
     * 保留字段仅作旧配置 JSON 读取兼容,消费侧一律走 [activeDialogKeywords]。
     */
    @Deprecated("use feature.dialogKeywords / activeDialogKeywords (V37 T1.2)")
    val dialogKeywords: List<String> = emptyList(),

    // ---- V37 功能域数据段 ----

    /** 功能域干预参数(画像/词表/黑名单追加;见 [FeatureConfig])。 */
    val feature: FeatureConfig = FeatureConfig(),
    /** Root:文件存在性检测干预(干预型,默认关)。 */
    val rootFile: Boolean = false,
    /** Root:命令执行检测干预(干预型,默认关)。 */
    val rootExec: Boolean = false,
    /** Root:包管理器查询干预(干预型,默认关)。 */
    val rootPackageManager: Boolean = false,
    /** Root:系统属性检测干预(干预型,默认关)。 */
    val rootProperty: Boolean = false,
    /** Xposed:类存在性检测干预(干预型,默认关)。 */
    val xposedClass: Boolean = false,
    /** Xposed:堆栈检测干预(干预型,默认关)。 */
    val xposedStack: Boolean = false,
    /** Xposed:/proc/self/maps 扫描干预(干预型,默认关)。 */
    val xposedMaps: Boolean = false,
    /** 模拟器:Build 属性伪造(干预型,默认关)。 */
    val emulatorBuild: Boolean = false,
    /** 模拟器:设备文件检测干预(干预型,默认关)。 */
    val emulatorFile: Boolean = false,
    /** 模拟器:传感器伪造(干预型,默认关)。 */
    val emulatorSensor: Boolean = false,
    /** 模拟器:网络/MAC/IMEI 伪造(干预型,默认关)。 */
    val emulatorNetwork: Boolean = false,
    /** 模拟器:电话信息伪造(干预型,默认关)。 */
    val emulatorTelephony: Boolean = false,
    /** 模拟器:CPU信息伪造(干预型,默认关)。 */
    val emulatorCpu: Boolean = false,
    /** 模拟器:电池信息伪造(干预型,默认关)。 */
    val emulatorBattery: Boolean = false,
    /** 设备环境:android.os.SystemProperties.get() 拦截(干预型,默认关)。 */
    val systemProperties: Boolean = false,
    /** 设备环境:/proc/self/status 行过滤(TracerPid,干预型,默认关)。 */
    val procStatus: Boolean = false,
    /** 设备环境:/proc/self/mountinfo 行过滤(Magisk overlay,干预型,默认关)。 */
    val procMountInfo: Boolean = false,
    /** 设备环境:/proc/self/cgroup 行过滤(Magisk hidden,干预型,默认关)。 */
    val procCgroup: Boolean = false,
    /** 设备环境:/proc/net/tcp 行过滤(Frida 端口,干预型,默认关)。 */
    val procNet: Boolean = false,
    /** 设备环境:Root 检测行为监控(仅记录,不干预;默认关,噪声大)。 */
    val detectRoot: Boolean = false,
    /** 设备环境:模拟器检测行为监控(仅记录,不干预;默认关,噪声大)。 */
    val detectEmulator: Boolean = false,

    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxHexChars: Int = RecordLimits.DEFAULT_MAX_HEX_CHARS,
    /** @deprecated 由 [maxBytesPerField] 表达;保留字段兼容旧配置 JSON。 */
    val maxTextChars: Int = RecordLimits.DEFAULT_MAX_TEXT_CHARS,

    /**
     * 配置版本号(V32+):旧配置缺失此字段时,Gson 给 JVM 默认值 0,
     * [fromJson] 据此判定需迁移干预型 hook 默认值。
     */
    val configVersion: Int = 0,
) {
    /** 是否存在任何需要 Hook 的类别(算法 + 数据库 + 行为 + 偏好 + WebView + 网络 + 设备环境 + UI交互 + 系统 + 进程控制 任一能力启用)。 */
    val anyEnabled: Boolean
        get() = anyCryptoEnabled || anyDbEnabled || anyBehaviorEnabled || anyPrefsEnabled ||
            anyWebviewEnabled || anyNetworkEnabled || anyDeviceEnvEnabled ||
            anyUiEnabled || anySystemEnabled || anyProcessControlEnabled

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
            emulatorTelephony || emulatorCpu || emulatorBattery ||
            systemProperties || procStatus || procMountInfo || procCgroup || procNet ||
            detectRoot || detectEmulator)

    /** UI 交互监听相关类别是否任一启用(含全局总开关投影,供 UI 交互监听能力判定)。 */
    val anyUiEnabled: Boolean
        get() = uiListen && (dialogShow || dialogKeyword || widgetText || activityStart || activityResume)

    /** 系统信息监听相关类别是否任一启用(含全局总开关投影,供系统信息监听能力判定)。 */
    val anySystemEnabled: Boolean
        get() = systemListen && (logcatCapture || appInfoCapture || signatureMonitor)

    /** 进程控制相关类别是否任一启用(含全局总开关投影,供进程控制能力判定)。 */
    val anyProcessControlEnabled: Boolean
        get() = processControlListen && (exitIntercept || crashPrevent || screenRecordBypass)

    /**
     * 弹窗拦截有效关键词表(V37 T1.2):feature 段优先,
     * 旧扁平字段兜底(fromJson 已做迁移,此处双保险覆盖手工构造路径)。
     */
    @Suppress("DEPRECATION")
    val activeDialogKeywords: List<String>
        get() = feature.dialogKeywords.ifEmpty { dialogKeywords }

    /**
     * 弹窗拦截有效规则(V37 T2.1):结构化规则优先,
     * 旧字符串列表按 CONTAINS 语义降级适配。
     */
    val activeKeywordRules: List<KeywordRule>
        get() = feature.dialogKeywordRules.ifEmpty {
            activeDialogKeywords.map { KeywordRule(it, KeywordMatchMode.CONTAINS) }
        }

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
        DeviceEnvKind.EMULATOR_TELEPHONY -> emulatorTelephony
        DeviceEnvKind.EMULATOR_CPU -> emulatorCpu
        DeviceEnvKind.EMULATOR_BATTERY -> emulatorBattery
        DeviceEnvKind.SYSTEM_PROPERTIES -> systemProperties
        DeviceEnvKind.PROC_STATUS -> procStatus
        DeviceEnvKind.PROC_MOUNT_INFO -> procMountInfo
        DeviceEnvKind.PROC_CGROUP -> procCgroup
        DeviceEnvKind.PROC_NET -> procNet
        DeviceEnvKind.DETECT_ROOT -> detectRoot
        DeviceEnvKind.DETECT_EMULATOR -> detectEmulator
    }

    /** UI 交互类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun uiInteractionEnabled(kind: UiInteractionKind): Boolean = uiListen && when (kind) {
        UiInteractionKind.DIALOG_SHOW -> dialogShow
        UiInteractionKind.DIALOG_KEYWORD -> dialogKeyword
        UiInteractionKind.WIDGET_TEXT -> widgetText
        UiInteractionKind.ACTIVITY_START -> activityStart
        UiInteractionKind.ACTIVITY_RESUME -> activityResume
    }

    /** 系统信息类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun systemEnabled(kind: SystemKind): Boolean = systemListen && when (kind) {
        SystemKind.LOGCAT -> logcatCapture
        SystemKind.APP_INFO -> appInfoCapture
        SystemKind.SIGNATURE -> signatureMonitor
    }

    /** 进程控制类别有效开关(master 门控;与 [CategoryTaxonomy] 默认态对齐由测试强制)。 */
    fun processControlEnabled(kind: ProcessKind): Boolean = processControlListen && when (kind) {
        ProcessKind.EXIT_INTERCEPT -> exitIntercept
        ProcessKind.CRASH_PREVENT -> crashPrevent
        ProcessKind.SCREEN_RECORD -> screenRecordBypass
    }

    /**
     * 该记录类型的 master 现值(V48 R-2 唯一通路):跨域提示(监听页要说出「管着这一类的总开关
     * 现在是不是关着」)与任何按类型聚合的观测都经此取,禁止在消费方各拼一份 `when`。
     * 穷尽 `when` ⇒ 新增 [RecordType] 必须同步补 master,编译期即暴露漏配。
     */
    fun masterFor(type: RecordType): Boolean = when (type) {
        RecordType.CRYPTO -> cryptoListen
        RecordType.DATABASE -> dbListen
        RecordType.BEHAVIOR -> behaviorListen
        RecordType.PREFS -> prefsListen
        RecordType.WEBVIEW -> webviewListen
        RecordType.SYSTEM -> systemListen
        RecordType.NETWORK -> networkListen
        RecordType.DEVICE_ENV -> deviceEnvListen
        RecordType.UI_INTERACTION -> uiListen
        RecordType.PROCESS_CONTROL -> processControlListen
    }

    companion object {
        /** 当前配置版本号:V32 干预型 hook 默认关闭迁移;V37 功能域数据段(feature);V47 设备画像库。 */
        private const val CURRENT_CONFIG_VERSION = 3

        val DEFAULT = HookSwitch(configVersion = CURRENT_CONFIG_VERSION)

        /**
         * Gson 反序列化 + 缺字段默认值补齐 + 版本迁移:
         * - 旧字段缺失时按 JsonObject 键存在性补齐默认值;
         * - configVersion < 1 时重置干预型 hook 为安全默认(false),
         *   防止 V31 及更早版本遗留的旧 true 值导致宿主崩溃;
         * - configVersion < 2 时把扁平 [dialogKeywords] 迁入 [FeatureConfig] 数据段
         *   (V37 T1.2 读旧写新;消费侧经 [activeDialogKeywords] 双保险)。
         */
        fun fromJson(gson: Gson, json: String): HookSwitch? = runCatching {
            val base = gson.fromJson(json, HookSwitch::class.java) ?: return null
            val obj = gson.fromJson(json, JsonObject::class.java)
            val version = obj?.get("configVersion")
                ?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            val needsInterventionReset = version < VERSION_INTERVENTION_DEFAULTS_OFF
            val needsFeatureMigration = version < CURRENT_CONFIG_VERSION
            // V47 T2:画像库主键体系自 configVersion 3 起生效,旧档位名需映射一次。
            val needsProfileIdMigration = version < VERSION_DEVICE_PROFILE_LIBRARY
            @Suppress("DEPRECATION")
            val legacyDialogKeywords = base.dialogKeywords
            base.copy(
                cryptoListen = obj?.get("cryptoListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                dbListen = obj?.get("dbListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                behaviorListen = obj?.get("behaviorListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                prefsListen = obj?.get("prefsListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                webviewListen = obj?.get("webviewListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                networkListen = obj?.get("networkListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                deviceEnvListen = obj?.get("deviceEnvListen")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: true,
                configVersion = CURRENT_CONFIG_VERSION,
                // V32 迁移:干预型 hook 强制重置为 false(旧版默认 true 导致宿主崩溃)
                sslContext = if (needsInterventionReset) false else base.sslContext,
                sslTrustManager = if (needsInterventionReset) false else base.sslTrustManager,
                sslOkhttpPinner = if (needsInterventionReset) false else base.sslOkhttpPinner,
                sslOkhttpVerifier = if (needsInterventionReset) false else base.sslOkhttpVerifier,
                sslWebview = if (needsInterventionReset) false else base.sslWebview,
                sslNetworkConfig = if (needsInterventionReset) false else base.sslNetworkConfig,
                proxySystemProp = if (needsInterventionReset) false else base.proxySystemProp,
                proxyConnectivity = if (needsInterventionReset) false else base.proxyConnectivity,
                proxySettings = if (needsInterventionReset) false else base.proxySettings,
                proxyOkhttp = if (needsInterventionReset) false else base.proxyOkhttp,
                vpnCapabilities = if (needsInterventionReset) false else base.vpnCapabilities,
                vpnInterface = if (needsInterventionReset) false else base.vpnInterface,
                vpnNetworkInfo = if (needsInterventionReset) false else base.vpnNetworkInfo,
                vpnLinkProperties = if (needsInterventionReset) false else base.vpnLinkProperties,
                rootFile = if (needsInterventionReset) false else base.rootFile,
                rootExec = if (needsInterventionReset) false else base.rootExec,
                rootPackageManager = if (needsInterventionReset) false else base.rootPackageManager,
                rootProperty = if (needsInterventionReset) false else base.rootProperty,
                xposedClass = if (needsInterventionReset) false else base.xposedClass,
                xposedStack = if (needsInterventionReset) false else base.xposedStack,
                xposedMaps = if (needsInterventionReset) false else base.xposedMaps,
                emulatorBuild = if (needsInterventionReset) false else base.emulatorBuild,
                emulatorFile = if (needsInterventionReset) false else base.emulatorFile,
                emulatorSensor = if (needsInterventionReset) false else base.emulatorSensor,
                emulatorNetwork = if (needsInterventionReset) false else base.emulatorNetwork,
                emulatorTelephony = if (needsInterventionReset) false else base.emulatorTelephony,
                emulatorCpu = if (needsInterventionReset) false else base.emulatorCpu,
                emulatorBattery = if (needsInterventionReset) false else base.emulatorBattery,
                systemProperties = if (needsInterventionReset) false else base.systemProperties,
                procStatus = if (needsInterventionReset) false else base.procStatus,
                procMountInfo = if (needsInterventionReset) false else base.procMountInfo,
                procCgroup = if (needsInterventionReset) false else base.procCgroup,
                procNet = if (needsInterventionReset) false else base.procNet,
                // V37 T1.2 迁移:扁平 dialogKeywords → feature 数据段(读旧写新)
                feature = if (needsFeatureMigration &&
                    base.feature.dialogKeywords.isEmpty() &&
                    legacyDialogKeywords.isNotEmpty()
                ) {
                    base.feature.copy(dialogKeywords = legacyDialogKeywords)
                } else {
                    base.feature
                }.let { feature ->
                    // V47 T2 迁移:旧三档档位名 → 画像库主键。
                    // 不迁就会「升级即静默失效」:旧值 auto/pixel7pro/xiaomi13 在新主键体系
                    // (builtin:*)里根本不存在,而缺失 id 按契约是硬失败(不伪装)——
                    // 用户此前的伪装会无声消失。
                    //  旧 auto      → 解绑画像 + 打开自适应掩蔽(保持**条件档**语义,
                    //                 不把一次升级变成「无条件改写」的静默加压)
                    //  旧 pixel7pro / xiaomi13 → 对应内置档(两者正是不伪造装的档位来源)
                    //  旧未知值     → 原样保留,交给 resolve 报 Missing(UI 可见,不静默)
                    if (!needsProfileIdMigration || feature.spoofProfileId.isEmpty()) {
                        feature
                    } else {
                        when (val legacy = feature.spoofProfileId) {
                            LEGACY_PROFILE_AUTO -> feature.copy(
                                spoofProfileId = "",
                                adaptiveMask = true,
                            )

                            LEGACY_PROFILE_PIXEL -> feature.copy(
                                spoofProfileId = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.PIXEL_7_PRO),
                            )

                            LEGACY_PROFILE_XIAOMI -> feature.copy(
                                spoofProfileId = BuiltinDeviceProfiles.idFor(BuiltinDeviceProfiles.XIAOMI_13),
                            )

                            else -> feature
                        }
                    }
                },
            )
        }.getOrNull()

        private const val VERSION_INTERVENTION_DEFAULTS_OFF = 1

        /** V47 T2 起画像库主键体系生效的最低 configVersion。 */
        private const val VERSION_DEVICE_PROFILE_LIBRARY = 3

        /** V37 T1.2 之前的隐式档位名(见 [SpoofProfiles]),V47 T2 起由画像库主键取代。 */
        private const val LEGACY_PROFILE_AUTO = "auto"
        private const val LEGACY_PROFILE_PIXEL = "pixel7pro"
        private const val LEGACY_PROFILE_XIAOMI = "xiaomi13"
    }
}