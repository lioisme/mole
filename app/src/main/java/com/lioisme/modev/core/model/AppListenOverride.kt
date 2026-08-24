package com.lioisme.modev.core.model

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 单应用监听覆盖(V23,三态继承模型,见 [TriState]):
 * 对 [HookSwitch] 的两个总开关与全部 17 个类别开关的逐项覆盖。
 *
 * - 键 = 类别 id([CryptoKind.name]/[SqlKind.name],同 [CategoryTaxonomy]);
 * - 解析容错:未知 kind 名 / 非法枚举名一律忽略(回落 INHERIT),
 *   缺字段补 [TriState.INHERIT](对齐 [HookSwitch.fromJson] 的缺字段防御);
 * - 存储 = 每包一键 `override_{pkg}`([ConfigKeys.OVERRIDE_PREFIX]),
 *   惰性创建,全 INHERIT([isDefault])即删键,避免逐包键膨胀;
 * - 注入侧不感知本类:宿主 resolve 后仍以普通 [HookSwitch] 经 wire 下发。
 */
data class AppListenOverride(
    /** 算法监听总开关覆盖。 */
    val cryptoListen: TriState = TriState.INHERIT,
    /** 数据库监听总开关覆盖。 */
    val dbListen: TriState = TriState.INHERIT,
    /** 类别级覆盖(kind id → 三态;INHERIT 条目在 [normalized] 中被剔除)。 */
    val kindOverrides: Map<String, TriState> = emptyMap(),
) {

    /** 是否等价于「无覆盖」(全 INHERIT):调用方应删除存储键而非落盘。 */
    val isDefault: Boolean
        get() = cryptoListen == TriState.INHERIT &&
            dbListen == TriState.INHERIT &&
            kindOverrides.isEmpty()

    /**
     * 规范化:剔除 INHERIT 的类别条目(存储紧凑,isDefault 判定可靠)。
     * 总开关 INHERIT 即默认值,无需处理。
     */
    fun normalized(): AppListenOverride = copy(
        kindOverrides = kindOverrides.filterValues { it != TriState.INHERIT },
    )

    /** Gson 序列化(与 [fromJson] 严格对称,双端一致)。 */
    fun toJson(gson: Gson): String = gson.toJson(normalized())

    companion object {

        val DEFAULT = AppListenOverride()

        /**
         * 反序列化 + 全量容错:
         * - null/空白/非法 JSON → [DEFAULT];
         * - 字段缺失 → [TriState.INHERIT];
         * - kindOverrides 内未知 kind 名、非法三态名 → 该条目忽略;
         * - 结果经 [normalized](空 map 收敛)。
         */
        fun fromJson(gson: Gson, json: String?): AppListenOverride {
            if (json.isNullOrBlank()) return DEFAULT
            return runCatching {
                val obj = gson.fromJson(json, JsonObject::class.java) ?: return DEFAULT
                val kinds = obj.getAsJsonObject(FIELD_KINDS) ?: null
                AppListenOverride(
                    cryptoListen = triStateOf(obj, FIELD_CRYPTO),
                    dbListen = triStateOf(obj, FIELD_DB),
                    kindOverrides = kinds?.entrySet()
                        ?.filter { VALID_KINDS.contains(it.key) }
                        ?.mapNotNull { (name, value) ->
                            val state = TriState.ofName(value?.takeIf { it.isJsonPrimitive }?.asString)
                            if (state == TriState.INHERIT) null else name to state
                        }
                        ?.toMap()
                        .orEmpty(),
                ).normalized()
            }.getOrDefault(DEFAULT)
        }

        private fun triStateOf(obj: JsonObject, field: String): TriState {
            val value = obj.get(field)?.takeIf { it.isJsonPrimitive }?.asString
            return TriState.ofName(value)
        }

        private const val FIELD_CRYPTO = "cryptoListen"
        private const val FIELD_DB = "dbListen"
        private const val FIELD_KINDS = "kindOverrides"

        /** 合法类别 id 白名单(CryptoKind ∪ SqlKind),防存储漂移注入垃圾键。 */
        private val VALID_KINDS: Set<String> =
            CryptoKind.entries.mapTo(mutableSetOf()) { it.name } +
                SqlKind.entries.map { it.name }
    }
}
