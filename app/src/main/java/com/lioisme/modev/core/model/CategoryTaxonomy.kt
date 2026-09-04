package com.lioisme.modev.core.model

/**
 * 记录类别分类法(单一事实源,V22):类型([RecordType])↔类别(kind 名)↔默认启用态↔
 * 噪点分级↔展示顺序的唯一来源,取代 UI 中对 [CryptoKind.entries]/[SqlKind.entries]
 * 的硬编码枚举遍历。
 *
 * - 设置页类别开关、记录筛选菜单、未来 MCP 元信息均以此为驱动;
 * - [id] 与枚举名一致([CryptoKind.name]/[SqlKind.name]),对齐 [HookSwitch] 字段、
 *   存储 `kind` 列、UI 标签函数([kindLabelRes]/[sqlKindLabelRes]);
 * - 标签解析仍由 UI 资源函数承担,本类保持 Android 资源无关(可纯 JVM 单测)。
 *
 * 噪点分级([NoiseTier])用于指导默认启用态与(未来)UI 提示,不参与存储/筛选语义。
 */
enum class NoiseTier {
    /** 高逆向价值,默认启用。 */
    SIGNAL,

    /** 价值一般,默认启用但可关注。 */
    NOISE,

    /** 实际意义低、高频噪声,默认停用。 */
    NOISE_HIGH,
}

/**
 * 单一类别元数据。
 *
 * @param id       类别标识,等于对应枚举名([CryptoKind]/[SqlKind])。
 * @param type     所属记录类型([RecordType.CRYPTO]/[RecordType.DATABASE])。
 * @param domain   能力域归属(V37 D-0 基线:[CapabilityDomain.LISTEN] 被动采集 /
 *                 [CapabilityDomain.FEATURE] 主动干预;新增类别必须显式标注,无默认值)。
 * @param defaultEnabled 新安装默认是否启用(噪点类别默认关;功能域类别默认关,
 *                 例外:数据门控类别如 DIALOG_KEYWORD 默认可开——空数据列表即真实停用)。
 * @param noiseTier 噪点分级(仅文档/排序用)。
 * @param order    同类型内展示顺序(升序)。
 */
data class RecordCategory(
    val id: String,
    val type: RecordType,
    val domain: CapabilityDomain,
    val defaultEnabled: Boolean,
    val noiseTier: NoiseTier,
    val order: Int,
)

/** 记录类别分类法(进程级单例,不可变)。 */
object CategoryTaxonomy {

    private val L = CapabilityDomain.LISTEN
    private val F = CapabilityDomain.FEATURE

    private val entries: List<RecordCategory> = buildList {
        // 算法类别(顺序对齐原设置页,保持用户肌肉记忆)
        add(RecordCategory(CryptoKind.CIPHER.name, RecordType.CRYPTO, L, true, NoiseTier.SIGNAL, 0))
        add(RecordCategory(CryptoKind.MESSAGE_DIGEST.name, RecordType.CRYPTO, L, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(CryptoKind.MAC.name, RecordType.CRYPTO, L, true, NoiseTier.SIGNAL, 2))
        add(RecordCategory(CryptoKind.SIGNATURE.name, RecordType.CRYPTO, L, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(CryptoKind.KEY_PARAM.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 4))
        add(RecordCategory(CryptoKind.KEY_GENERATION.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 5))
        add(RecordCategory(CryptoKind.KEY_FACTORY.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 6))
        add(RecordCategory(CryptoKind.KEY_STORE.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 7))
        add(RecordCategory(CryptoKind.KEY_AGREEMENT.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 8))
        add(RecordCategory(CryptoKind.RANDOM.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 9))
        add(RecordCategory(CryptoKind.BASE64.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 10))
        add(RecordCategory(CryptoKind.WEB_CRYPTO.name, RecordType.CRYPTO, L, false, NoiseTier.NOISE_HIGH, 11))
        // 数据库类别(顺序对齐原设置页)
        add(RecordCategory(SqlKind.OPEN.name, RecordType.DATABASE, L, false, NoiseTier.NOISE_HIGH, 0))
        add(RecordCategory(SqlKind.QUERY.name, RecordType.DATABASE, L, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(SqlKind.DML.name, RecordType.DATABASE, L, true, NoiseTier.SIGNAL, 2))
        add(RecordCategory(SqlKind.EXEC.name, RecordType.DATABASE, L, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(SqlKind.TRANSACTION.name, RecordType.DATABASE, L, false, NoiseTier.NOISE_HIGH, 4))
        // 行为类别(V27;默认态与 HookSwitch.DEFAULT 对齐:写/删/重命名/Shell 高信号默认开,
        // 读/列目录/Assets 高频噪声默认关)
        add(RecordCategory(BehaviorKind.FILE_READ.name, RecordType.BEHAVIOR, L, false, NoiseTier.NOISE_HIGH, 0))
        add(RecordCategory(BehaviorKind.FILE_WRITE.name, RecordType.BEHAVIOR, L, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(BehaviorKind.FILE_DELETE.name, RecordType.BEHAVIOR, L, true, NoiseTier.SIGNAL, 2))
        add(RecordCategory(BehaviorKind.FILE_RENAME.name, RecordType.BEHAVIOR, L, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(BehaviorKind.FILE_LIST.name, RecordType.BEHAVIOR, L, false, NoiseTier.NOISE_HIGH, 4))
        add(RecordCategory(BehaviorKind.ASSET_READ.name, RecordType.BEHAVIOR, L, false, NoiseTier.NOISE_HIGH, 5))
        add(RecordCategory(BehaviorKind.SHELL_EXEC.name, RecordType.BEHAVIOR, L, true, NoiseTier.SIGNAL, 6))
        // 偏好类别(V28;默认态与 HookSwitch.DEFAULT 对齐:OPEN/写/删/清空 高信号默认开,
        // GET 高频噪声默认关——决策 D4)
        add(RecordCategory(PrefsKind.OPEN.name, RecordType.PREFS, L, true, NoiseTier.SIGNAL, 0))
        add(RecordCategory(PrefsKind.GET.name, RecordType.PREFS, L, false, NoiseTier.NOISE_HIGH, 1))
        add(RecordCategory(PrefsKind.PUT.name, RecordType.PREFS, L, true, NoiseTier.SIGNAL, 2))
        add(RecordCategory(PrefsKind.REMOVE.name, RecordType.PREFS, L, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(PrefsKind.CLEAR.name, RecordType.PREFS, L, true, NoiseTier.SIGNAL, 4))
        // WebView 类别(V29 一等公民;默认态与 HookSwitch.DEFAULT 对齐:PAGE_LOAD/EVALUATE_JS/
        // JS_BRIDGE/DEBUG 高信号默认开,JS_CRYPTO 高频噪声默认关)
        add(RecordCategory(WebViewKind.PAGE_LOAD.name, RecordType.WEBVIEW, L, true, NoiseTier.SIGNAL, 0))
        add(RecordCategory(WebViewKind.EVALUATE_JS.name, RecordType.WEBVIEW, L, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(WebViewKind.JS_BRIDGE.name, RecordType.WEBVIEW, L, true, NoiseTier.SIGNAL, 2))
        add(RecordCategory(WebViewKind.DEBUG.name, RecordType.WEBVIEW, L, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(WebViewKind.JS_CRYPTO.name, RecordType.WEBVIEW, L, false, NoiseTier.NOISE_HIGH, 4))
        // 网络环境类别(V30;V37 D-0:SSL/代理/VPN 为功能域干预类,默认关(安全优先);
        // REQUEST 为监听域观测类,默认开——与 HookSwitch.DEFAULT 对齐)
        add(RecordCategory(NetworkKind.SSL_CONTEXT.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 0))
        add(RecordCategory(NetworkKind.SSL_TRUST_MANAGER.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 1))
        add(RecordCategory(NetworkKind.SSL_OKHTTP_PINNER.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 2))
        add(RecordCategory(NetworkKind.SSL_OKHTTP_VERIFIER.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 3))
        add(RecordCategory(NetworkKind.SSL_WEBVIEW.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 4))
        add(RecordCategory(NetworkKind.SSL_NETWORK_CONFIG.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 5))
        add(RecordCategory(NetworkKind.PROXY_SYSTEM_PROP.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 6))
        add(RecordCategory(NetworkKind.PROXY_CONNECTIVITY.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 7))
        add(RecordCategory(NetworkKind.PROXY_SETTINGS.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 8))
        add(RecordCategory(NetworkKind.PROXY_OKHTTP.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 9))
        add(RecordCategory(NetworkKind.VPN_CAPABILITIES.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 10))
        add(RecordCategory(NetworkKind.VPN_INTERFACE.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 11))
        add(RecordCategory(NetworkKind.VPN_NETWORK_INFO.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 12))
        add(RecordCategory(NetworkKind.VPN_LINK_PROPERTIES.name, RecordType.NETWORK, F, false, NoiseTier.SIGNAL, 13))
        add(RecordCategory(NetworkKind.REQUEST.name, RecordType.NETWORK, L, true, NoiseTier.SIGNAL, 14))
        // 设备环境类别(V31;V37 D-0:Root/Xposed/模拟器/proc 19 项为功能域干预类,默认关;
        // DETECT_* 为监听域观测类,默认关(噪声大)——与 HookSwitch.DEFAULT 对齐)
        add(RecordCategory(DeviceEnvKind.ROOT_FILE.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 0))
        add(RecordCategory(DeviceEnvKind.ROOT_EXEC.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 1))
        add(RecordCategory(DeviceEnvKind.ROOT_PACKAGE_MANAGER.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 2))
        add(RecordCategory(DeviceEnvKind.ROOT_PROPERTY.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 3))
        add(RecordCategory(DeviceEnvKind.XPOSED_CLASS.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 4))
        add(RecordCategory(DeviceEnvKind.XPOSED_STACK.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 5))
        add(RecordCategory(DeviceEnvKind.XPOSED_MAPS.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 6))
        add(RecordCategory(DeviceEnvKind.EMULATOR_BUILD.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 7))
        add(RecordCategory(DeviceEnvKind.EMULATOR_FILE.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 8))
        add(RecordCategory(DeviceEnvKind.EMULATOR_SENSOR.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 9))
        add(RecordCategory(DeviceEnvKind.EMULATOR_NETWORK.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 10))
        add(RecordCategory(DeviceEnvKind.EMULATOR_TELEPHONY.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 11))
        add(RecordCategory(DeviceEnvKind.EMULATOR_CPU.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 12))
        add(RecordCategory(DeviceEnvKind.EMULATOR_BATTERY.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 13))
        // V35 momo 增强:系统属性/进程文件检测干预(功能域,默认关)
        add(RecordCategory(DeviceEnvKind.SYSTEM_PROPERTIES.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 14))
        add(RecordCategory(DeviceEnvKind.PROC_STATUS.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 15))
        add(RecordCategory(DeviceEnvKind.PROC_MOUNT_INFO.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 16))
        add(RecordCategory(DeviceEnvKind.PROC_CGROUP.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 17))
        add(RecordCategory(DeviceEnvKind.PROC_NET.name, RecordType.DEVICE_ENV, F, false, NoiseTier.SIGNAL, 18))
        add(RecordCategory(DeviceEnvKind.DETECT_ROOT.name, RecordType.DEVICE_ENV, L, false, NoiseTier.NOISE_HIGH, 19))
        add(RecordCategory(DeviceEnvKind.DETECT_EMULATOR.name, RecordType.DEVICE_ENV, L, false, NoiseTier.NOISE_HIGH, 20))
        // V33 UI 交互类别(V37 D-0:DIALOG_KEYWORD 为功能域拦截,其余为监听域观测;
        // 默认态与 HookSwitch.DEFAULT 对齐)
        add(RecordCategory(UiInteractionKind.DIALOG_SHOW.name, RecordType.UI_INTERACTION, L, true, NoiseTier.SIGNAL, 0))
        add(RecordCategory(UiInteractionKind.DIALOG_KEYWORD.name, RecordType.UI_INTERACTION, F, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(UiInteractionKind.WIDGET_TEXT.name, RecordType.UI_INTERACTION, L, false, NoiseTier.NOISE_HIGH, 2))
        add(RecordCategory(UiInteractionKind.ACTIVITY_START.name, RecordType.UI_INTERACTION, L, true, NoiseTier.SIGNAL, 3))
        add(RecordCategory(UiInteractionKind.ACTIVITY_RESUME.name, RecordType.UI_INTERACTION, L, false, NoiseTier.NOISE, 4))
        // V33 系统信息监听类别;默认态与 HookSwitch.DEFAULT 对齐:
        // APP_INFO/SIGNATURE 高信号默认开,LOGCAT 高频噪声默认关)
        add(RecordCategory(SystemKind.LOGCAT.name, RecordType.SYSTEM, L, false, NoiseTier.NOISE_HIGH, 0))
        add(RecordCategory(SystemKind.APP_INFO.name, RecordType.SYSTEM, L, true, NoiseTier.SIGNAL, 1))
        add(RecordCategory(SystemKind.SIGNATURE.name, RecordType.SYSTEM, L, true, NoiseTier.SIGNAL, 2))
        // V33 进程控制类别(V37 D-0:全部功能域干预,默认关(安全优先))
        add(RecordCategory(ProcessKind.EXIT_INTERCEPT.name, RecordType.PROCESS_CONTROL, F, false, NoiseTier.SIGNAL, 0))
        add(RecordCategory(ProcessKind.CRASH_PREVENT.name, RecordType.PROCESS_CONTROL, F, false, NoiseTier.SIGNAL, 1))
        add(RecordCategory(ProcessKind.SCREEN_RECORD.name, RecordType.PROCESS_CONTROL, F, false, NoiseTier.NOISE, 2))
    }

    /** 全部类别(按 type 分组、组内按 order;稳定输出)。 */
    fun all(): List<RecordCategory> =
        entries.sortedWith(compareBy({ it.type.ordinal }, { it.order }))

    /** 指定能力域的类别(V37 双域拆分:功能页/监听页行集合唯一驱动源)。 */
    fun forDomain(domain: CapabilityDomain): List<RecordCategory> =
        entries.filter { it.domain == domain }

    /** 类别是否功能域(主动干预);未知类别按监听域处理(漂移安全)。 */
    fun isFeature(type: RecordType, id: String): Boolean =
        byId(type, id)?.domain == CapabilityDomain.FEATURE

    /** 指定类型的类别(按 order 升序)。 */
    fun forType(type: RecordType): List<RecordCategory> =
        entries.filter { it.type == type }.sortedBy { it.order }

    /** 按类型 + id 取类别(非法返回 null,协议/存储漂移兜底)。 */
    fun byId(type: RecordType, id: String): RecordCategory? =
        entries.firstOrNull { it.type == type && it.id == id }

    /**
     * 将 [kinds] 收敛到 [recordType] 所属类别集合(精准查询保证,防跨类型 kind 名碰撞):
     * - [kinds] 为 null/空集 → 返回 `null`(不过滤,语义同 [RecordFilter] 全集);
     * - [recordType] 为 null/未知 → 原样返回(无类型约束时不收敛);
     * - 否则返回与类型合法类别相交的集合,**可能为空 `Set`**(调用方据其短路为「无匹配」)。
     */
    fun validateKinds(recordType: String?, kinds: Set<String>?): Set<String>? {
        if (kinds.isNullOrEmpty()) return null
        val type = RecordType.fromWire(recordType) ?: return kinds
        val valid = forType(type).map { it.id }.toSet()
        return kinds.intersect(valid)
    }

    /**
     * 将 [operations] 收敛到 [recordType] 所属操作的合法集合(与 [validateKinds] 同规)。
     * 操作枚举([CryptoOperation]/[SqlOperation])按类型分属,跨类型名碰撞概率低但同样收敛。
     */
    fun validateOperations(recordType: String?, operations: Set<String>?): Set<String>? {
        if (operations.isNullOrEmpty()) return null
        val type = RecordType.fromWire(recordType) ?: return operations
        val valid = when (type) {
            RecordType.CRYPTO -> CryptoOperation.entries.map { it.name }.toSet()
            RecordType.DATABASE -> SqlOperation.entries.map { it.name }.toSet()
            RecordType.BEHAVIOR -> BehaviorOperation.entries.map { it.name }.toSet()
            RecordType.PREFS -> PrefsOperation.entries.map { it.name }.toSet()
            RecordType.WEBVIEW -> WebViewOperation.entries.map { it.name }.toSet()
            RecordType.NETWORK -> NetworkOperation.entries.map { it.name }.toSet()
            RecordType.DEVICE_ENV -> DeviceEnvOperation.entries.map { it.name }.toSet()
            RecordType.UI_INTERACTION -> UiInteractionOperation.entries.map { it.name }.toSet()
            RecordType.SYSTEM -> SystemOperation.entries.map { it.name }.toSet()
            RecordType.PROCESS_CONTROL -> ProcessOperation.entries.map { it.name }.toSet()
        }
        return operations.intersect(valid)
    }

    /** 默认启用的类别 id 集合(供 HookSwitch 默认推导/校验)。 */
    fun defaultEnabledIds(type: RecordType): Set<String> =
        forType(type).filter { it.defaultEnabled }.map { it.id }.toSet()
}
