package com.lioisme.modev.core.model

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 单应用监听自定义(V24 二态偏差模型,V25 扩展采集微调项):
 * 对默认配置(内置推荐值 [HookSwitch.DEFAULT])的逐项显式偏差,仅存与默认不同的条目。
 *
 * - 字段语义:null/缺条目 = 取默认配置对应位;非 null = 显式生效;
 *   有效配置 = 默认配置 ⊕ 本类偏差(见 [EffectiveSwitch.resolve]),无第三态;
 * - 键 = 类别 id([CryptoKind.name]/[SqlKind.name],同 [CategoryTaxonomy]);
 * - 解析容错:未知 kind 名一律忽略(防存储漂移注入垃圾键),非法值回落「取默认」;
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
    /** 类别级偏差(kind id → 显式开关;仅存偏差条目,缺条目 = 取默认)。 */
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
) {

    /** 是否等价于「完全未定制」(与默认配置零偏差):调用方应删除存储键而非落盘。 */
    val isDefault: Boolean
        get() = cryptoListen == null &&
            dbListen == null &&
            behaviorListen == null &&
            prefsListen == null &&
            kinds.isEmpty() &&
            logStack == null &&
            maxBytesPerField == null &&
            ignoreStackPrefixes.isNullOrEmpty() &&
            maxResultRows == null &&
            maxResultChars == null &&
            fileContentCapture == null &&
            ignorePathPrefixes.isNullOrEmpty()

    /** Gson 序列化(null 位由 Gson 默认省略,存储紧凑;构造路径保证无冗余条目)。 */
    fun toJson(gson: Gson): String = gson.toJson(this)

    companion object {

        val DEFAULT = AppListenOverride()

        /**
         * 反序列化 + 全量容错:
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
                    kinds = kindsOf(obj),
                    logStack = boolOf(obj, FIELD_LOG_STACK),
                    maxBytesPerField = intOf(obj, FIELD_MAX_BYTES),
                    ignoreStackPrefixes = stringListOf(obj, FIELD_IGNORE_STACK),
                    maxResultRows = intOf(obj, FIELD_MAX_RESULT_ROWS),
                    maxResultChars = intOf(obj, FIELD_MAX_RESULT_CHARS),
                    fileContentCapture = boolOf(obj, FIELD_FILE_CONTENT),
                    ignorePathPrefixes = stringListOf(obj, FIELD_IGNORE_PATH),
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
         * 类别偏差解析:新键名 [FIELD_KINDS] 优先,V23 遗留键名
         * [LEGACY_FIELD_KINDS] 平滑读取;白名单过滤 + 双格式兼容。
         */
        private fun kindsOf(obj: JsonObject): Map<String, Boolean> {
            val kinds = obj.getAsJsonObject(FIELD_KINDS)
                ?: obj.getAsJsonObject(LEGACY_FIELD_KINDS)
                ?: return emptyMap()
            return kinds.entrySet().mapNotNull { (name, value) ->
                if (!VALID_KINDS.contains(name)) return@mapNotNull null
                boolValueOf(value)?.let { name to it }
            }.toMap()
        }

        private const val FIELD_CRYPTO = "cryptoListen"
        private const val FIELD_DB = "dbListen"
        private const val FIELD_BEHAVIOR = "behaviorListen"
        private const val FIELD_PREFS = "prefsListen"
        private const val FIELD_KINDS = "kinds"
        private const val FIELD_LOG_STACK = "logStack"
        private const val FIELD_MAX_BYTES = "maxBytesPerField"
        private const val FIELD_IGNORE_STACK = "ignoreStackPrefixes"
        private const val FIELD_MAX_RESULT_ROWS = "maxResultRows"
        private const val FIELD_MAX_RESULT_CHARS = "maxResultChars"
        private const val FIELD_FILE_CONTENT = "fileContentCapture"
        private const val FIELD_IGNORE_PATH = "ignorePathPrefixes"

        /** V23 三态遗留:旧 kinds 键名与枚举序列化形态。 */
        private const val LEGACY_FIELD_KINDS = "kindOverrides"
        private const val LEGACY_ON = "ON"
        private const val LEGACY_OFF = "OFF"

        /** 合法类别 id 白名单(CryptoKind ∪ SqlKind ∪ BehaviorKind ∪ PrefsKind),防存储漂移注入垃圾键。 */
        private val VALID_KINDS: Set<String> =
            CryptoKind.entries.mapTo(mutableSetOf()) { it.name } +
                SqlKind.entries.map { it.name } +
                BehaviorKind.entries.map { it.name } +
                PrefsKind.entries.map { it.name }
    }
}
