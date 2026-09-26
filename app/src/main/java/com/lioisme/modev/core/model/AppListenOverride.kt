package com.lioisme.modev.core.model

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 单应用监听自定义(V24 二态偏差模型,V25 扩展采集微调项):
 * 对默认配置(内置推荐值 [HookSwitch.DEFAULT])的逐项显式偏差,仅存与默认不同的条目。
 *
 * - 字段语义:null/缺条目 = 取默认配置对应位;非 null = 显式生效;
 *   有效配置 = 默认配置 ⊕ 本类偏差(见 [EffectiveSwitch.resolve]),无第三态;
 * - 键 = **限定类别键** [`RecordType.wire` + ":" + kind id]([kindKey],V44 R1-6;
 *   跨类型裸 id 碰撞 OPEN/SIGNATURE 曾在遗留 first-wins 映射下互踩——限定形态为唯一写入格式,
 *   合法性由 [CategoryTaxonomy] 裁决,取代旧枚举并集白名单);
 * - 解析容错:未知 kind 名一律忽略(防存储漂移注入垃圾键),非法值回落「取默认」;
 *   遗留裸 id 键(限定化之前落盘的存储 + 外部直投 JSON)按
 *   [CategoryTaxonomy.typesWithId] 序首归一为限定键(OPEN→DATABASE,SIGNATURE→CRYPTO,
 *   与历史 first-wins 解析逐位一致);
 * - 兼容 V23 三态遗留存储(`"ON"/"OFF"/"INHERIT"` + `kindOverrides` 键名):
 *   解析时无损映射("ON"→true、"OFF"→false、INHERIT/未知→取默认),写入恒为新格式;
 * - 存储 = 每包一键 `override_{pkg}`([ConfigKeys.OVERRIDE_PREFIX]),
 *   惰性创建,无任何偏差([isDefault])即删键,避免逐包键膨胀;
 * - 注入侧不感知本类:resolve 后仍以普通 [HookSwitch] 经 wire 下发(VERSION 恒 4)。
 */
data class AppListenOverride(
    /** 算法监听总开关偏差(null = 取默认)。 */
    val cryptoListen: Boolean? = null,
    /** 数据库监听总开关偏差(null = 取默认)。 */
    val dbListen: Boolean? = null,
    /** 行为监听总开关偏差(V27;null = 取默认)。 */
    val behaviorListen: Boolean? = null,
    /** 偏好监听总开关偏差(V28;null = 取默认)。 */
    val prefsListen: Boolean? = null,
    /** WebView 监听总开关偏差(V29 一等公民;null = 取默认)。 */
    val webviewListen: Boolean? = null,
    /** 网络环境监听总开关偏差(V30 一等公民;null = 取默认)。 */
    val networkListen: Boolean? = null,
    /** 设备环境监听总开关偏差(V31 一等公民;null = 取默认)。 */
    val deviceEnvListen: Boolean? = null,
    /** UI 交互监听总开关偏差(V33 一等公民;null = 取默认)。 */
    val uiListen: Boolean? = null,
    /** 系统信息监听总开关偏差(V33 一等公民;null = 取默认)。 */
    val systemListen: Boolean? = null,
    /** 进程控制总开关偏差(V33 一等公民;null = 取默认)。 */
    val processControlListen: Boolean? = null,
    /** 类别级偏差(限定类别键 → 显式开关;仅存偏差条目,缺条目 = 取默认)。 */
    val kinds: Map<String, Boolean> = emptyMap(),
    /** 记录调用栈偏差(null = 取默认)。 */
    val logStack: Boolean? = null,
    /** 单字段字节上限偏差(null = 取默认;≤0 语义为不限,由 UI 层渲染 ∞)。 */
    val maxBytesPerField: Int? = null,
    /**
     * 忽略栈前缀清单偏差(null = 取默认;非空整表替换)。
     * 空表与默认等价([isDefault] 归零),不做「显式清空」语义。
     */
    val ignoreStackPrefixes: List<String>? = null,
    /** 结果行数上限偏差(null = 取默认)。 */
    val maxResultRows: Int? = null,
    /** 结果字节上限偏差(null = 取默认)。 */
    val maxResultChars: Int? = null,
    /** 文件内容捕获偏差(V27;null = 取默认)。 */
    val fileContentCapture: Boolean? = null,
    /**
     * 忽略路径前缀清单偏差(V27 行为降噪;null = 取默认,非空整表替换)。
     * 空表与默认等价([isDefault] 归零)。
     */
    val ignorePathPrefixes: List<String>? = null,
    /** 弹窗关键字清单偏差(V33;null = 取默认,非空整表替换)。 */
    val dialogKeywords: List<String>? = null,
    // ---- V37 T1.2 功能域数据偏差 ----
    /** 伪装画像档位偏差(V47 T2 起 = 画像库主键;null = 取默认,"" = 显式不伪装)。 */
    val spoofProfileId: String? = null,
    /**
     * 画像字段覆写偏差(**只读遗留通道**,见 [FeatureConfig.spoofOverrides] 与
     * 决策 D-3 修订;无新写入路径,存量值仍生效。V49 WP-D6 随 `SpoofProfiles` 一并删除)。
     */
    val spoofOverrides: Map<String, String>? = null,
    /**
     * **每应用逐项画像定制**(V49 R-2):所绑画像的字段级稀疏覆写。
     *
     * 键 = [ProfileFieldCatalog.jsonKeyOf](与画像正文同一套键,不是遗留 `build.MODEL` 语法),
     * 值 = 该字段的编辑态字符串;**空串/缺省哨兵值 = 显式清掉画像里这一项**(覆写语义必须能
     * 表达「绑了档但这项不要」,否则用户只能改档或另存一档 —— 那正是 R-2 要消灭的形态)。
     * 只有出现在本表里的键才算覆写,缺键 = 跟随所绑画像。
     *
     * 归属 FEATURE 域(与 [spoofProfileId] 同域同清),计入 [deviationCount] 的条目数
     * (逐字段计数,与 `kinds` 同一口径:一行一项就是用户定制的一项)。
     */
    val profileOverrides: Map<String, String>? = null,
    /**
     * 宿主展开后的画像快照偏差(V47 T2 / 决策 D-12):随 [spoofProfileId] 一起写入,
     * 供注入侧「直读两键 / 磁盘兜底」两条不经宿主的通路消费。
     *
     * **派生数据,不是用户意图** ⇒ 恒不计入 [isDefault] 与 [deviationCount]
     * (否则「绑了画像」会被算成两次偏差,恢复默认行的计数与副标就会分叉);
     * 但随 FEATURE 域一并清除(它与 spoofProfileId 同生同灭,单留会指向不存在的绑定)。
     */
    val resolvedProfile: DeviceProfile? = null,
    /** Root 包名黑名单追加偏差(null = 取默认;非空整表替换)。 */
    val extraRootPackages: List<String>? = null,
    /** Xposed 前缀黑名单追加偏差(null = 取默认;非空整表替换)。 */
    val extraXposedPrefixes: List<String>? = null,
    /** Frida 端口追加偏差(null = 取默认;非空整表替换)。 */
    val extraFridaPorts: List<Int>? = null,
    /** 掩码关键词追加偏差(null = 取默认;非空整表替换)。 */
    val extraMaskKeywords: List<String>? = null,
    /**
     * 关键词库启用条目偏差(V37 T2.1;宿主端解析为 [FeatureConfig.dialogKeywords] 后
     * 不再下发 id 集,注入侧零感知;仅存储/回显用)。
     */
    val keywordIds: List<Long>? = null,
    /**
     * 关键词「跟随全局启用集」偏差(V37 T3.4;宿主端消费,不入 wire):
     * null/true = 跟随全局;false = 显式使用 [keywordIds] 子集(空 = 全部不启用)。
     */
    val keywordFollowGlobal: Boolean? = null,
) {

    /** 是否等价于「完全未定制」(与默认配置零偏差):调用方应删除存储键而非落盘。 */
    val isDefault: Boolean
        get() = cryptoListen == null &&
            dbListen == null &&
            behaviorListen == null &&
            prefsListen == null &&
            webviewListen == null &&
            networkListen == null &&
            deviceEnvListen == null &&
            uiListen == null &&
            systemListen == null &&
            processControlListen == null &&
            kinds.isEmpty() &&
            logStack == null &&
            maxBytesPerField == null &&
            ignoreStackPrefixes.isNullOrEmpty() &&
            maxResultRows == null &&
            maxResultChars == null &&
            fileContentCapture == null &&
            ignorePathPrefixes.isNullOrEmpty() &&
            dialogKeywords.isNullOrEmpty() &&
            spoofProfileId == null &&
            spoofOverrides.isNullOrEmpty() &&
            profileOverrides.isNullOrEmpty() &&
            extraRootPackages.isNullOrEmpty() &&
            extraXposedPrefixes.isNullOrEmpty() &&
            extraFridaPorts.isNullOrEmpty() &&
            extraMaskKeywords.isNullOrEmpty() &&
            keywordIds.isNullOrEmpty() &&
            keywordFollowGlobal == null

    /**
     * 自定义偏差计数(V37 C8 单一事实源:入口页摘要与恢复默认行共用;
     * 清单类偏差整表计 1)。跨域总数 —— 单域计数见 [deviationCountFor]。
     */
    val deviationCount: Int
        get() =
            (if (cryptoListen != null) 1 else 0) +
            (if (dbListen != null) 1 else 0) +
            (if (behaviorListen != null) 1 else 0) +
            (if (prefsListen != null) 1 else 0) +
            (if (webviewListen != null) 1 else 0) +
            (if (networkListen != null) 1 else 0) +
            (if (deviceEnvListen != null) 1 else 0) +
            (if (uiListen != null) 1 else 0) +
            (if (systemListen != null) 1 else 0) +
            (if (processControlListen != null) 1 else 0) +
            kinds.size +
            (if (logStack != null) 1 else 0) +
            (if (maxBytesPerField != null) 1 else 0) +
            (if (!ignoreStackPrefixes.isNullOrEmpty()) 1 else 0) +
            (if (maxResultRows != null) 1 else 0) +
            (if (maxResultChars != null) 1 else 0) +
            (if (fileContentCapture != null) 1 else 0) +
            (if (!ignorePathPrefixes.isNullOrEmpty()) 1 else 0) +
            (if (!dialogKeywords.isNullOrEmpty()) 1 else 0) +
            (if (spoofProfileId != null) 1 else 0) +
            (if (!spoofOverrides.isNullOrEmpty()) 1 else 0) +
            (profileOverrides?.size ?: 0) +
            (if (!extraRootPackages.isNullOrEmpty()) 1 else 0) +
            (if (!extraXposedPrefixes.isNullOrEmpty()) 1 else 0) +
            (if (!extraFridaPorts.isNullOrEmpty()) 1 else 0) +
            (if (!extraMaskKeywords.isNullOrEmpty()) 1 else 0) +
            (if (!keywordIds.isNullOrEmpty()) 1 else 0) +
            (if (keywordFollowGlobal != null) 1 else 0)

    /**
     * 只清除 [domain] 一侧的偏差,另一侧原样保留(V45 T3.5 / DUAL-12)。
     *
     * 为什么必须有:监听页与功能页各有一行「恢复默认配置」,过去两面都调全量
     * [removeOverride] 语义的清除 —— 在监听页点恢复会把用户的功能域定制(画像、
     * 黑名单追加、关键词子集)一起抹掉,反之亦然。双域契约(AGENTS #23)在写侧
     * 有闸、在清侧没有,是同一状态机上的口径分叉。
     */
    fun clearDomain(domain: CapabilityDomain): AppListenOverride {
        val listen = domain == CapabilityDomain.LISTEN
        /**
         * master 的域归属 = 该记录类型是否承载 FEATURE 域类别
         * (与两页总开关的实际分区一致:功能页 ui/network/device_env/process_control,
         * 监听页 crypto/db/behavior/prefs/webview/system)。
         */
        fun master(type: RecordType, value: Boolean?): Boolean? =
            if (masterDomainOf(type) == domain) null else value
        return copy(
            cryptoListen = master(RecordType.CRYPTO, cryptoListen),
            dbListen = master(RecordType.DATABASE, dbListen),
            behaviorListen = master(RecordType.BEHAVIOR, behaviorListen),
            prefsListen = master(RecordType.PREFS, prefsListen),
            webviewListen = master(RecordType.WEBVIEW, webviewListen),
            networkListen = master(RecordType.NETWORK, networkListen),
            deviceEnvListen = master(RecordType.DEVICE_ENV, deviceEnvListen),
            uiListen = master(RecordType.UI_INTERACTION, uiListen),
            systemListen = master(RecordType.SYSTEM, systemListen),
            processControlListen = master(RecordType.PROCESS_CONTROL, processControlListen),
            // 清本域 = 留对侧:监听域清除时保留 FEATURE 类别,反之亦然
            kinds = kinds.filterKeys { CategoryTaxonomy.isFeatureKindKey(it) == listen },
            logStack = if (listen) null else logStack,
            maxBytesPerField = if (listen) null else maxBytesPerField,
            ignoreStackPrefixes = if (listen) null else ignoreStackPrefixes,
            maxResultRows = if (listen) null else maxResultRows,
            maxResultChars = if (listen) null else maxResultChars,
            fileContentCapture = if (listen) null else fileContentCapture,
            ignorePathPrefixes = if (listen) null else ignorePathPrefixes,
            // V33 遗留整表词表 = 弹窗关键词**拦截**定制(V37 后由关键词库 + keywordIds 承接),
            // 归属与 UI_INTERACTION:DIALOG_KEYWORD 类别一致 = 功能域
            dialogKeywords = if (!listen) null else dialogKeywords,
            spoofProfileId = if (!listen) null else spoofProfileId,
            spoofOverrides = if (!listen) null else spoofOverrides,
            profileOverrides = if (!listen) null else profileOverrides,
            resolvedProfile = if (!listen) null else resolvedProfile,
            extraRootPackages = if (!listen) null else extraRootPackages,
            extraXposedPrefixes = if (!listen) null else extraXposedPrefixes,
            extraFridaPorts = if (!listen) null else extraFridaPorts,
            extraMaskKeywords = if (!listen) null else extraMaskKeywords,
            keywordIds = if (!listen) null else keywordIds,
            keywordFollowGlobal = if (!listen) null else keywordFollowGlobal,
        )
    }

    /**
     * 单域偏差计数(每应用两页的「恢复默认配置」行可见性与计数用本值,
     * 恒等式 `countFor(LISTEN) + countFor(FEATURE) == deviationCount` 由测试锁定)。
     */
    fun deviationCountFor(domain: CapabilityDomain): Int =
        clearDomain(domain.opposite).deviationCount

    /** Gson 序列化(null 位由 Gson 默认省略,存储紧凑;构造路径保证无冗余条目)。 */
    fun toJson(gson: Gson): String = gson.toJson(this)

    companion object {

        val DEFAULT = AppListenOverride()

        /**
         * master 的记录类型 → 域归属(V45 T3.5 / DUAL-12):
         * 承载 FEATURE 域类别的类型(ui/network/device_env/process_control)其总开关归功能页,
         * 其余归监听页 —— 与两页实际渲染的总开关集合同源派生,不是第二张手工清单。
         */
        fun masterDomainOf(type: RecordType): CapabilityDomain =
            if (type in FEATURE_MASTER_TYPES) CapabilityDomain.FEATURE else CapabilityDomain.LISTEN

        private val FEATURE_MASTER_TYPES: Set<RecordType> =
            CategoryTaxonomy.forDomain(CapabilityDomain.FEATURE).map { it.type }.toSet()

        /**
         * 限定类别键 = `RecordType.wire + ":" + kind id`(kinds 映射唯一写入格式)。
         * 合法性由 [CategoryTaxonomy] 裁决;UI/MCP 写路径一律经本函数构键。
         */
        fun kindKey(type: RecordType, id: String): String = "${type.wire}:$id"

        /**
         * 解析任意 kinds 键为 (类型, id):
         * - 限定形态 `wire:ID`:必须为 taxonomy 已登记类别,否则 null;
         * - 裸形态 `ID`(V44 前遗留存储 / 外部直投 JSON):按
         *   [CategoryTaxonomy.typesWithId] 序首解析(歧义 id 恒归 DATABASE/CRYPTO,
         *   与旧 first-wins 字符串分支逐位一致);无登记类型返回 null。
         */
        fun parseKindKey(raw: String): Pair<RecordType, String>? {
            val idx = raw.indexOf(':')
            if (idx >= 0) {
                val type = RecordType.fromWire(raw.substring(0, idx)) ?: return null
                val id = raw.substring(idx + 1)
                return if (CategoryTaxonomy.byId(type, id) != null) type to id else null
            }
            val type = CategoryTaxonomy.typesWithId(raw).firstOrNull() ?: return null
            return type to raw
        }

        /** 反序列化 + 全量容错:
         * - null/空白/非法 JSON → [DEFAULT];
         * - 字段缺失 → 取默认(null);V23 遗留字符串形态平滑映射;
         * - kinds 内未知 kind 名、非法值 → 该条目忽略。
         */
        fun fromJson(gson: Gson, json: String?): AppListenOverride {
            if (json.isNullOrBlank()) return DEFAULT
            return runCatching {
                val obj = gson.fromJson(json, JsonObject::class.java) ?: return DEFAULT
                AppListenOverride(
                    cryptoListen = boolOf(obj, FIELD_CRYPTO),
                    dbListen = boolOf(obj, FIELD_DB),
                    behaviorListen = boolOf(obj, FIELD_BEHAVIOR),
                    prefsListen = boolOf(obj, FIELD_PREFS),
                    webviewListen = boolOf(obj, FIELD_WEBVIEW),
                    networkListen = boolOf(obj, FIELD_NETWORK),
                    deviceEnvListen = boolOf(obj, FIELD_DEVICE_ENV),
                    uiListen = boolOf(obj, FIELD_UI),
                    systemListen = boolOf(obj, FIELD_SYSTEM),
                    processControlListen = boolOf(obj, FIELD_PROCESS_CONTROL),
                    kinds = kindsOf(obj),
                    logStack = boolOf(obj, FIELD_LOG_STACK),
                    maxBytesPerField = intOf(obj, FIELD_MAX_BYTES),
                    ignoreStackPrefixes = stringListOf(obj, FIELD_IGNORE_STACK),
                    maxResultRows = intOf(obj, FIELD_MAX_RESULT_ROWS),
                    maxResultChars = intOf(obj, FIELD_MAX_RESULT_CHARS),
                    fileContentCapture = boolOf(obj, FIELD_FILE_CONTENT),
                    ignorePathPrefixes = stringListOf(obj, FIELD_IGNORE_PATH),
                    dialogKeywords = stringListOf(obj, FIELD_DIALOG_KEYWORDS),
                    spoofProfileId = stringOf(obj, FIELD_SPOOF_PROFILE),
                    spoofOverrides = stringMapOf(obj, FIELD_SPOOF_OVERRIDES),
                    // V50 实锤 S1:V49 R-2 加 profileOverrides 属性时只补了 toJson(Gson 反射自动写)、
                    // isDefault/deviationCount/clearDomain,唯独漏了这条手写解析臂 ⇒
                    // 每应用画像微调**落盘后读回即丢**,而全部单测经内存对象直读故恒绿。
                    profileOverrides = stringMapOf(obj, FIELD_PROFILE_OVERRIDES),
                    resolvedProfile = obj.get(FIELD_RESOLVED_PROFILE)
                        ?.takeIf { it.isJsonObject }
                        ?.let {
                            runCatching {
                                gson.fromJson(it, DeviceProfile::class.java)
                            }.getOrNull()
                        },
                    extraRootPackages = stringListOf(obj, FIELD_EXTRA_ROOT_PACKAGES),
                    extraXposedPrefixes = stringListOf(obj, FIELD_EXTRA_XPOSED_PREFIXES),
                    extraFridaPorts = intListOf(obj, FIELD_EXTRA_FRIDA_PORTS),
                    extraMaskKeywords = stringListOf(obj, FIELD_EXTRA_MASK_KEYWORDS),
                    keywordIds = longListOf(obj, FIELD_KEYWORD_IDS),
                    keywordFollowGlobal = boolOf(obj, FIELD_KEYWORD_FOLLOW_GLOBAL),
                )
            }.getOrDefault(DEFAULT)
        }

        /**
         * 布尔偏差解析:V24 新格式为 JSON 布尔;V23 遗留为字符串
         * `"ON"/"OFF"`("INHERIT"/未知串 → null 取默认,宁可漏定制不误改状态)。
         */
        private fun boolOf(obj: JsonObject, field: String): Boolean? {
            val value = obj.get(field)?.takeIf { it.isJsonPrimitive } ?: return null
            return when {
                value.asJsonPrimitive.isBoolean -> value.asBoolean
                value.asString == LEGACY_ON -> true
                value.asString == LEGACY_OFF -> false
                else -> null
            }
        }

        /** 整型偏差解析(V25 采集微调项):仅接受 JSON 数值原样;缺失/非数值 → 取默认。 */
        private fun intOf(obj: JsonObject, field: String): Int? {
            val value = obj.get(field)?.takeIf { it.isJsonPrimitive } ?: return null
            return if (value.asJsonPrimitive.isNumber) value.asInt else null
        }

        /** 字符串清单偏差解析(V25 忽略栈前缀):仅接受字符串数组;缺失/形态不符/空表 → 取默认。 */
        private fun stringListOf(obj: JsonObject, field: String): List<String>? {
            val array = obj.get(field)?.takeIf { it.isJsonArray } ?: return null
            val entries = array.asJsonArray
                .mapNotNull { it?.takeIf { e -> e.isJsonPrimitive }?.asString }
            return entries.ifEmpty { null }
        }

        /** 单值字符串偏差解析(V37 画像档位):仅接受字符串原样;缺失/空串 → 取默认。 */
        private fun stringOf(obj: JsonObject, field: String): String? =
            obj.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.takeIf { it.isNotEmpty() }

        /** 字符串映射偏差解析(V37 画像覆写):仅接受字符串→字符串对象;缺失/空对象 → 取默认。 */
        private fun stringMapOf(obj: JsonObject, field: String): Map<String, String>? {
            val map = obj.get(field)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            if (map.entrySet().isEmpty()) return null
            return map.entrySet()
                .mapNotNull { (k, v) ->
                    val value = v?.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isString }?.asString
                    if (value == null) null else k to value
                }
                .toMap()
                .ifEmpty { null }
        }

        /** 整型清单偏差解析(V37 Frida 端口追加):仅接受数值数组;缺失/空表 → 取默认。 */
        private fun intListOf(obj: JsonObject, field: String): List<Int>? {
            val array = obj.get(field)?.takeIf { it.isJsonArray } ?: return null
            val entries = array.asJsonArray
                .mapNotNull { it?.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isNumber }?.asInt }
            return entries.ifEmpty { null }
        }

        /** 长整型清单偏差解析(V37 关键词库启用条目):仅接受数值数组;缺失/空表 → 取默认。 */
        private fun longListOf(obj: JsonObject, field: String): List<Long>? {
            val array = obj.get(field)?.takeIf { it.isJsonArray } ?: return null
            val entries = array.asJsonArray
                .mapNotNull { it?.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isNumber }?.asLong }
            return entries.ifEmpty { null }
        }

        private fun boolValueOf(value: com.google.gson.JsonElement?): Boolean? {
            val primitive = value?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
            return when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.asString == LEGACY_ON -> true
                primitive.asString == LEGACY_OFF -> false
                else -> null
            }
        }

        /**
         * 类别偏差解析(V44 限定键模型):新键名 [FIELD_KINDS] 优先,V23 遗留键名
         * [LEGACY_FIELD_KINDS] 平滑读取;键经 [parseKindKey] 归一为限定形态,
         * 未知/歧义无法解析的键忽略,双格式(限定/遗留裸)兼容。
         */
        private fun kindsOf(obj: JsonObject): Map<String, Boolean> {
            val kinds = obj.getAsJsonObject(FIELD_KINDS)
                ?: obj.getAsJsonObject(LEGACY_FIELD_KINDS)
                ?: return emptyMap()
            return kinds.entrySet().mapNotNull { (name, value) ->
                val key = parseKindKey(name) ?: return@mapNotNull null
                boolValueOf(value)?.let { kindKey(key.first, key.second) to it }
            }.toMap()
        }

        private const val FIELD_CRYPTO = "cryptoListen"
        private const val FIELD_DB = "dbListen"
        private const val FIELD_BEHAVIOR = "behaviorListen"
        private const val FIELD_PREFS = "prefsListen"
        private const val FIELD_WEBVIEW = "webviewListen"
        private const val FIELD_NETWORK = "networkListen"
        private const val FIELD_DEVICE_ENV = "deviceEnvListen"
        private const val FIELD_UI = "uiListen"
        private const val FIELD_SYSTEM = "systemListen"
        private const val FIELD_PROCESS_CONTROL = "processControlListen"
        private const val FIELD_KINDS = "kinds"
        private const val FIELD_LOG_STACK = "logStack"
        private const val FIELD_MAX_BYTES = "maxBytesPerField"
        private const val FIELD_IGNORE_STACK = "ignoreStackPrefixes"
        private const val FIELD_MAX_RESULT_ROWS = "maxResultRows"
        private const val FIELD_MAX_RESULT_CHARS = "maxResultChars"
        private const val FIELD_FILE_CONTENT = "fileContentCapture"
        private const val FIELD_IGNORE_PATH = "ignorePathPrefixes"
        private const val FIELD_DIALOG_KEYWORDS = "dialogKeywords"
        private const val FIELD_SPOOF_PROFILE = "spoofProfileId"
        private const val FIELD_SPOOF_OVERRIDES = "spoofOverrides"
        private const val FIELD_PROFILE_OVERRIDES = "profileOverrides"
        private const val FIELD_RESOLVED_PROFILE = "resolvedProfile"
        private const val FIELD_EXTRA_ROOT_PACKAGES = "extraRootPackages"
        private const val FIELD_EXTRA_XPOSED_PREFIXES = "extraXposedPrefixes"
        private const val FIELD_EXTRA_FRIDA_PORTS = "extraFridaPorts"
        private const val FIELD_EXTRA_MASK_KEYWORDS = "extraMaskKeywords"
        private const val FIELD_KEYWORD_IDS = "keywordIds"
        private const val FIELD_KEYWORD_FOLLOW_GLOBAL = "keywordFollowGlobal"

        /** V23 三态遗留:旧 kinds 键名与枚举序列化形态。 */
        private const val LEGACY_FIELD_KINDS = "kindOverrides"
        private const val LEGACY_ON = "ON"
        private const val LEGACY_OFF = "OFF"
    }
}
