package com.lioisme.modev.core.model

/**
 * 黑名单追加类别(V44 D4 上移 core:检测黑名单的用户追加项,
 * UI 黑名单页与 MCP `blacklist_edit` 共享唯一枚举——旧形态 MCP 字符串 when
 * 与 UI 枚举 when 双清单漂移)。
 *
 * 语义 = 内置基线 ∪ 追加(只增不删基线,AGENTS #23);[wire] = MCP `kind` 参数值。
 */
enum class BlacklistKind(val wire: String) {
    /** Root 包名追加。 */
    ROOT_PACKAGES("rootPackages"),

    /** Xposed 包名前缀追加。 */
    XPOSED_PREFIXES("xposedPrefixes"),

    /** Frida 端口追加(值为十进制端口号)。 */
    FRIDA_PORTS("fridaPorts"),

    /** 掩码关键词追加(/proc/self/maps 行过滤)。 */
    MASK_KEYWORDS("maskKeywords");

    companion object {
        fun fromWire(value: String?): BlacklistKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * 黑名单列表合并(add = 去重追加;remove = 集合剔除),按 [BlacklistKind] 分派到
 * [FeatureConfig] 对应追加位——UI 与 MCP 唯一写映射(与 [BlacklistKind] 穷尽 when 同步)。
 * 值恒为字符串形态;[BlacklistKind.FRIDA_PORTS] 解析非数值串并丢弃。
 */
fun FeatureConfig.merged(kind: BlacklistKind, values: List<String>, add: Boolean): FeatureConfig {
    fun strings(): List<String> = values.map { it.trim() }.filter { it.isNotEmpty() }
    return when (kind) {
        BlacklistKind.ROOT_PACKAGES ->
            copy(extraRootPackages = merge(extraRootPackages, strings(), add))

        BlacklistKind.XPOSED_PREFIXES ->
            copy(extraXposedPrefixes = merge(extraXposedPrefixes, strings(), add))

        BlacklistKind.FRIDA_PORTS -> {
            val ports = values.mapNotNull { it.trim().toIntOrNull() }
            copy(extraFridaPorts = merge(extraFridaPorts, ports, add))
        }

        BlacklistKind.MASK_KEYWORDS ->
            copy(extraMaskKeywords = merge(extraMaskKeywords, strings(), add))
    }
}

private fun <T> merge(current: List<T>, delta: List<T>, add: Boolean): List<T> =
    if (add) (current + delta).distinct() else current - delta.toSet()

/** 黑名单类别 → 用户追加项(字符串投影;UI 分区行/chips 与空态判定共用)。 */
fun FeatureConfig.blacklistValues(kind: BlacklistKind): List<String> = when (kind) {
    BlacklistKind.ROOT_PACKAGES -> extraRootPackages
    BlacklistKind.XPOSED_PREFIXES -> extraXposedPrefixes
    BlacklistKind.FRIDA_PORTS -> extraFridaPorts.map { it.toString() }
    BlacklistKind.MASK_KEYWORDS -> extraMaskKeywords
}
