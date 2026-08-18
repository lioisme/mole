package com.lioisme.modev.ui.logs

import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView

/**
 * 文本区块护栏与导出预检(V16 R2 空/大数据健壮性):纯函数,单测覆盖。
 *
 * params 护栏:单键值 ≤ [PARAMS_MAX_VALUE_CHARS] 字符、总条目 ≤ [PARAMS_MAX_ENTRIES],
 * 超限计数交由 UI 追加本地化省略标记(不截断原始数据,仅渲染层收敛)。
 * stack 护栏:渲染取前 [STACK_MAX_FRAMES] 帧,超出由 UI 追加本地化标记。
 * 导出预检:[estimateJsonChars] 以 ×2 保守倍率估算 JSON 字符数,超 [EXPORT_MAX_CHARS]
 * 在构建前拒绝(免浪费后台构建)。
 */

/** 参数区块单条键值行的最大字符数(渲染护栏,截断以「…」标记)。 */
internal const val PARAMS_MAX_VALUE_CHARS = 256

/** 参数区块最大渲染条目数(防超大 Map 产生巨型单 Text 布局)。 */
internal const val PARAMS_MAX_ENTRIES = 200

/** 调用堆栈最大渲染帧数(栈长时仅保留最内层近因)。 */
internal const val STACK_MAX_FRAMES = 300

/** params 渲染结果:行 + 超限计数(省略/截断由 UI 本地化渲染)。 */
internal data class ParamsText(
    val lines: List<String>,
    val omittedEntries: Int,
    val truncatedValues: Int,
) {
    val isEmpty: Boolean get() = lines.isEmpty() && omittedEntries == 0 && truncatedValues == 0
}

/** params → 有界渲染行(键值行 `k = v`,超长值截 256 字符 + 省略号,防布局无界)。 */
internal fun formatParamsText(params: Map<String, String>): ParamsText {
    val lines = mutableListOf<String>()
    var omittedEntries = 0
    var truncatedValues = 0
    for ((key, value) in params) {
        if (lines.size >= PARAMS_MAX_ENTRIES) {
            omittedEntries++
            continue
        }
        if (value.length > PARAMS_MAX_VALUE_CHARS) {
            truncatedValues++
            lines += "$key = ${value.take(PARAMS_MAX_VALUE_CHARS)}…"
        } else {
            lines += "$key = $value"
        }
    }
    return ParamsText(lines, omittedEntries, truncatedValues)
}

/** 各字节区块对估算的贡献:hex(2 字符/字节)+ 2×text(UTF-8 转义保守倍率)。 */
private fun BytesView?.estimateChars(): Int =
    if (this == null) 0 else hex.length + 2 * (text?.length ?: 0)

/**
 * 导出 JSON 字符数保守估算(×2 倍率覆盖转义/键名/分隔符):
 * 用于 [EXPORT_MAX_CHARS] 预检——估算超限即拒绝,免构建后才发现超限。
 */
internal fun estimateJsonChars(record: CryptoRecord): Int {
    var total = record.packageName.length + 128
    with(record) {
        total += algorithm.length + threadName.length
        total += provider?.length ?: 0
        total += keyAlias?.length ?: 0
        total += processName?.length ?: 0
        params?.forEach { (k, v) -> total += k.length + v.length }
        stack.forEach { total += it.length }
        total += key.estimateChars()
        total += iv.estimateChars()
        total += input.estimateChars()
        total += output.estimateChars()
        total += salt.estimateChars()
        total += aad.estimateChars()
        total += publicKey.estimateChars()
    }
    return total * 2
}