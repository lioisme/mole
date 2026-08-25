package com.lioisme.modev.core.model

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 单应用监听自定义(V24,二态偏差模型):
 * 对默认配置([HookSwitch] 全局键)的逐项显式偏差,仅存与默认不同的条目。
 *
 * - 字段语义:null/缺条目 = 取默认配置对应位;非 null = 显式启用/停用;
 *   有效配置 = 默认配置 ⊕ 本类偏差(见 [EffectiveSwitch.resolve]),无第三态;
 * - 键 = 类别 id([CryptoKind.name]/[SqlKind.name],同 [CategoryTaxonomy]);
 * - 解析容错:未知 kind 名一律忽略(防存储漂移注入垃圾键),非法值回落「取默认」;
 * - 兼容 V23 三态遗留存储(`"ON"/"OFF"/"INHERIT"` + `kindOverrides` 键名):
 *   解析时无损映射("ON"→true、"OFF"→false、INHERIT/未知→取默认),写入恒为新格式,
 *   首次读改写即完成迁移(D2,键名 `override_{pkg}` 不变);
 * - 存储 = 每包一键 `override_{pkg}`([ConfigKeys.OVERRIDE_PREFIX]),
 *   惰性创建,无任何偏差([isDefault])即删键,避免逐包键膨胀;
 * - 注入侧不感知本类:resolve 后仍以普通 [HookSwitch] 经 wire 下发(VERSION 恒 4)。
 */
data class AppListenOverride(
    /** 算法监听总开关偏差(null = 取默认)。 */
    val cryptoListen: Boolean? = null,
    /** 数据库监听总开关偏差(null = 取默认)。 */
    val dbListen: Boolean? = null,
    /** 类别级偏差(kind id → 显式开关;仅存偏差条目,缺条目 = 取默认)。 */
    val kinds: Map<String, Boolean> = emptyMap(),
) {

    /** 是否等价于「完全未定制」(与默认配置零偏差):调用方应删除存储键而非落盘。 */
    val isDefault: Boolean
        get() = cryptoListen == null && dbListen == null && kinds.isEmpty()

    /** Gson 序列化(与 [fromJson] 新格式严格对称;构造路径保证无冗余条目)。 */
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
                    kinds = kindsOf(obj),
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

        /**
         * 类别偏差解析:新键名 [FIELD_KINDS] 优先,V23 遗留键名
         * [LEGACY_FIELD_KINDS] 平滑读取;白名单过滤 + 同 [boolOf] 双格式兼容。
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

        private fun boolValueOf(value: com.google.gson.JsonElement?): Boolean? {
            val primitive = value?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
            return when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.asString == LEGACY_ON -> true
                primitive.asString == LEGACY_OFF -> false
                else -> null
            }
        }

        private const val FIELD_CRYPTO = "cryptoListen"
        private const val FIELD_DB = "dbListen"
        private const val FIELD_KINDS = "kinds"

        /** V23 三态遗留:旧 kinds 键名与枚举序列化形态。 */
        private const val LEGACY_FIELD_KINDS = "kindOverrides"
        private const val LEGACY_ON = "ON"
        private const val LEGACY_OFF = "OFF"

        /** 合法类别 id 白名单(CryptoKind ∪ SqlKind),防存储漂移注入垃圾键。 */
        private val VALID_KINDS: Set<String> =
            CryptoKind.entries.mapTo(mutableSetOf()) { it.name } +
                SqlKind.entries.map { it.name }
    }
}
