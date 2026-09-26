package com.lioisme.modev.core.model

/**
 * 弹窗拦截关键词匹配模式。
 */
enum class KeywordMatchMode {
    /** 子串包含(大小写不敏感)。 */
    CONTAINS,

    /** 正则匹配(大小写不敏感;非法正则降级为不匹配)。 */
    REGEX,

    /** 全等(大小写不敏感)。 */
    EQUALS,
}

/**
 * 弹窗拦截关键词规则:文本 + 匹配模式,
 * 宿主端由全局关键词库解析产出,经 [FeatureConfig.dialogKeywordRules] 下发;
 * 注入侧 [com.lioisme.modev.hook.hooks.DialogKeywordInterceptHooker] 消费。
 */
data class KeywordRule(
    val text: String,
    val matchMode: KeywordMatchMode = KeywordMatchMode.CONTAINS,
) {
    companion object {
        /** 单规则对单文本的匹配判定(纯函数,null 文本恒不匹配;空规则文本恒不匹配)。 */
        fun matches(rule: KeywordRule, value: String?): Boolean {
            if (value.isNullOrEmpty() || rule.text.isEmpty()) return false
            return when (rule.matchMode) {
                KeywordMatchMode.CONTAINS -> value.contains(rule.text, ignoreCase = true)
                KeywordMatchMode.EQUALS -> value.equals(rule.text, ignoreCase = true)
                KeywordMatchMode.REGEX -> runCatching {
                    Regex(rule.text, RegexOption.IGNORE_CASE).containsMatchIn(value)
                }.getOrDefault(false)
            }
        }
    }
}
