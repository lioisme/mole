package com.lioisme.modev.ui.logs

import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.PrefsRecord
import com.lioisme.modev.core.model.SqlBindValue
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.core.model.SqlResultCell
import com.lioisme.modev.core.model.SqlResultRow
import com.lioisme.modev.core.model.SqlValueType
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

/** 绑定参数渲染上限条数(防巨型 bindArgs 产生无界布局)。 */
internal const val BIND_ARGS_MAX_ROWS = 500

/** 结果集渲染上限行数(行超限仅渲染前 N 行,其余按行计数聚合省略标记)。 */
internal const val RESULT_MAX_ROWS = 200

/** 结果集单行渲染上限列数。 */
internal const val RESULT_MAX_COLS = 50

/** 绑定参数 → 有界渲染行(`#i [类型] 值`,截断标记由 UI 追加)。 */
internal fun formatBindArgsText(args: List<SqlBindValue>): List<String> {
    val lines = mutableListOf<String>()
    for ((index, arg) in args.withIndex()) {
        if (index >= BIND_ARGS_MAX_ROWS) break
        lines += "#${arg.i} [${arg.t.name}] ${arg.renderValue()}"
    }
    return lines
}

/** 结果集 → 有界渲染行(`#i col=值, …`)。 */
internal fun formatRowsText(rows: List<SqlResultRow>): List<String> {
    val lines = mutableListOf<String>()
    for ((rowIndex, row) in rows.withIndex()) {
        if (rowIndex >= RESULT_MAX_ROWS) break
        val cells = row.c.take(RESULT_MAX_COLS)
        val truncated = row.c.size > RESULT_MAX_COLS
        lines += "#$rowIndex " + cells.joinToString(", ") { cell ->
            "${cell.n}=${cell.renderValue()}" + if (cell.truncated) "…" else ""
        } + if (truncated) " …" else ""
    }
    return lines
}

private fun SqlBindValue.renderValue(): String = when (t) {
    SqlValueType.L, SqlValueType.D, SqlValueType.S -> v ?: ""
    SqlValueType.B -> hex ?: ""
    SqlValueType.N -> "NULL"
}

private fun SqlResultCell.renderValue(): String = when (t) {
    SqlValueType.L, SqlValueType.D, SqlValueType.S -> v ?: ""
    SqlValueType.B -> hex ?: ""
    SqlValueType.N -> "NULL"
}

// region V28 JSON 高亮发射(D3:params / bindArgs / rows 以真实 JSON 承载,
//  经 json 围栏由 Highlights 引擎着色;序列化走 Gson 保证转义正确性。)

/** 共享 pretty Gson(serializeNulls:SQL NULL 语义必须落为 JSON null;JsonObject 保持插入序)。 */
private val prettyGson by lazy {
    com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()
}

/** params JSON 渲染结果:文档(null = 无参数)+ 超限计数(UI 追加本地化标记)。 */
internal data class ParamsJson(
    val json: String?,
    val omittedEntries: Int,
    val truncatedValues: Int,
)

/** params → 保序 pretty JSON 对象(继承 [PARAMS_MAX_VALUE_CHARS]/[PARAMS_MAX_ENTRIES] 护栏)。 */
internal fun formatParamsJson(params: Map<String, String>): ParamsJson {
    if (params.isEmpty()) return ParamsJson(null, 0, 0)
    var omittedEntries = 0
    var truncatedValues = 0
    val obj = com.google.gson.JsonObject()
    for ((key, value) in params) {
        if (obj.size() >= PARAMS_MAX_ENTRIES) {
            omittedEntries++
            continue
        }
        if (value.length > PARAMS_MAX_VALUE_CHARS) {
            truncatedValues++
            obj.addProperty(key, value.take(PARAMS_MAX_VALUE_CHARS) + "…")
        } else {
            obj.addProperty(key, value)
        }
    }
    return ParamsJson(prettyGson.toJson(obj), omittedEntries, truncatedValues)
}

/** 绑定参数 JSON 渲染结果:pretty 数组文档(null = 无参数)+ 省略行数。 */
internal data class BindArgsJson(val json: String?, val omittedRows: Int)

/** 绑定参数 → pretty JSON 数组(`[{"index":1,"type":"STRING","value":"…"}]`,继承 [BIND_ARGS_MAX_ROWS])。 */
internal fun formatBindArgsJson(args: List<SqlBindValue>): BindArgsJson {
    if (args.isEmpty()) return BindArgsJson(null, 0)
    val array = com.google.gson.JsonArray()
    for ((index, arg) in args.withIndex()) {
        if (index >= BIND_ARGS_MAX_ROWS) break
        val entry = com.google.gson.JsonObject()
        entry.addProperty("index", arg.i)
        entry.addProperty("type", arg.t.name)
        val rendered = arg.renderValue()
        if (arg.t == SqlValueType.N) entry.add("value", com.google.gson.JsonNull.INSTANCE) else entry.addProperty("value", rendered)
        array.add(entry)
    }
    return BindArgsJson(prettyGson.toJson(array), args.size - array.size())
}

/** 结果集 JSON 渲染结果:NDJSON 行(每行一个独立 JSON 对象)+ 省略行数。 */
internal data class RowsJson(val lines: List<String>, val omittedRows: Int)

/**
 * 结果集 → NDJSON(每行 `{"index":0,"cells":[{"name":"id","value":"1"}]}`,
 * 行数 ≤ [RESULT_MAX_ROWS]、行内列数 ≤ [RESULT_MAX_COLS];逐行独立成文,
 * 高亮器按整块着色且每行可单独复制比对)。
 */
internal fun formatRowsNdjson(rows: List<SqlResultRow>): RowsJson {
    if (rows.isEmpty()) return RowsJson(emptyList(), 0)
    val lines = mutableListOf<String>()
    for ((rowIndex, row) in rows.withIndex()) {
        if (rowIndex >= RESULT_MAX_ROWS) break
        val cells = row.c.take(RESULT_MAX_COLS)
        val cellsArray = com.google.gson.JsonArray()
        cells.forEach { cell ->
            val entry = com.google.gson.JsonObject()
            entry.addProperty("name", cell.n)
            if (cell.t == SqlValueType.N) {
                entry.add("value", com.google.gson.JsonNull.INSTANCE)
            } else {
                entry.addProperty("value", cell.renderValue() + if (cell.truncated) "…" else "")
            }
            cellsArray.add(entry)
        }
        val lineObj = com.google.gson.JsonObject()
        lineObj.addProperty("index", rowIndex)
        lineObj.add("cells", cellsArray)
        lines += prettyGson.toJson(lineObj).replace("\n", " ")
    }
    return RowsJson(lines, rows.size - lines.size)
}
// endregion

/**
 * SQL 记录导出 JSON 字符数保守估算(×2 倍率,与 [estimateJsonChars] 同规):
 * sql + bindArgs + rows + 元数据,超限免构建。
 */
internal fun estimateSqlJsonChars(record: SqlRecord): Int {
    var total = record.packageName.length + record.databaseName.length + 128
    with(record) {
        total += sql?.length ?: 0
        total += dbPath?.length ?: 0
        total += resultText?.length ?: 0
        total += processName?.length ?: 0
        bindArgs?.forEach { total += it.renderValue().length + 16 }
        rows?.forEach { row ->
            row.c.forEach { cell -> total += cell.n.length + cell.renderValue().length }
        }
        params?.forEach { (k, v) -> total += k.length + v.length }
        stack.forEach { total += it.length }
    }
    return total * 2
}

/**
 * 行为记录导出 JSON 字符数保守估算(×2 倍率,与 [estimateSqlJsonChars] 同规):
 * 路径/目标路径/命令/内容三态 + 元数据,超限免构建。
 */
internal fun estimateBehaviorJsonChars(record: BehaviorRecord): Int {
    var total = record.packageName.length + 128
    with(record) {
        total += path?.length ?: 0
        total += destPath?.length ?: 0
        command?.forEach { total += it.length + 8 }
        content?.let {
            total += it.hex.length + (it.base64?.length ?: 0) + (it.text?.length ?: 0)
        }
        params?.forEach { (k, v) -> total += k.length + v.length }
        processName?.let { total += it.length }
        stack.forEach { total += it.length }
    }
    return total * 2
}

/**
 * 偏好记录导出 JSON 字符数保守估算(×2 倍率,V28 与三兄弟同规):
 * 键 / 文件路径 / 值域三态 + 元数据,超限免构建。
 */
internal fun estimatePrefsJsonChars(record: PrefsRecord): Int {
    var total = record.packageName.length + 128
    with(record) {
        total += key?.length ?: 0
        total += path?.length ?: 0
        value?.let {
            total += it.hex.length + (it.base64?.length ?: 0) + (it.text?.length ?: 0)
        }
        params?.forEach { (k, v) -> total += k.length + v.length }
        processName?.let { total += it.length }
        stack.forEach { total += it.length }
    }
    return total * 2
}