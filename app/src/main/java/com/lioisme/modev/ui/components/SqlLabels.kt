package com.lioisme.modev.ui.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lioisme.modev.R
import com.lioisme.modev.core.model.SqlKind
import com.lioisme.modev.core.model.SqlOperation
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.data.mapping.SqlRow

/** SQL 类别显示名资源(筛选菜单 / 详情页元数据 / 列表行共用)。 */
@StringRes
fun sqlKindLabelRes(kind: SqlKind): Int = when (kind) {
    SqlKind.OPEN -> R.string.kind_db_open
    SqlKind.QUERY -> R.string.kind_db_query
    SqlKind.DML -> R.string.kind_db_dml
    SqlKind.EXEC -> R.string.kind_db_exec
    SqlKind.TRANSACTION -> R.string.kind_db_transaction
}

/** SQL 操作标签资源(与算法操作同规,i18n 双语言)。 */
@StringRes
fun sqlOperationLabelRes(operation: SqlOperation): Int = when (operation) {
    SqlOperation.OPEN_DATABASE -> R.string.op_db_open_database
    SqlOperation.CLOSE_DATABASE -> R.string.op_db_close_database
    SqlOperation.OPEN_HELPER -> R.string.op_db_open_helper
    SqlOperation.CLOSE_HELPER -> R.string.op_db_close_helper
    SqlOperation.QUERY -> R.string.op_db_query
    SqlOperation.RAW_QUERY -> R.string.op_db_raw_query
    SqlOperation.QUERY_RESULT -> R.string.op_db_query_result
    SqlOperation.STATEMENT_QUERY_LONG -> R.string.op_db_statement_query_long
    SqlOperation.STATEMENT_QUERY_STRING -> R.string.op_db_statement_query_string
    SqlOperation.INSERT -> R.string.op_db_insert
    SqlOperation.REPLACE -> R.string.op_db_replace
    SqlOperation.UPDATE -> R.string.op_db_update
    SqlOperation.DELETE -> R.string.op_db_delete
    SqlOperation.EXEC_SQL -> R.string.op_db_exec_sql
    SqlOperation.STATEMENT_EXECUTE -> R.string.op_db_statement_execute
    SqlOperation.STATEMENT_EXECUTE_INSERT -> R.string.op_db_statement_execute_insert
    SqlOperation.STATEMENT_EXECUTE_UPDATE_DELETE -> R.string.op_db_statement_execute_update_delete
    SqlOperation.ATTACH -> R.string.op_db_attach
    SqlOperation.BEGIN_TRANSACTION -> R.string.op_db_begin_transaction
    SqlOperation.END_TRANSACTION -> R.string.op_db_end_transaction
    SqlOperation.SET_TRANSACTION_SUCCESSFUL -> R.string.op_db_set_transaction_successful
}

/** SQL 操作标签(组合包装,视觉承载)。 */
@Composable
fun sqlOperationLabel(operation: SqlOperation): String = stringResource(sqlOperationLabelRes(operation))

/** SQL 操作标签(非组合;经 Context 解析,供事件回调 / 摘要等非组合路径使用)。 */
fun sqlOperationLabel(context: Context, operation: SqlOperation): String =
    context.getString(sqlOperationLabelRes(operation))

/**
 * SQL 记录 headline 单一事实源(V17 空白治理,与算法 `displayAlgorithm` 同规):
 * 优先取 SQL 首行(trim),SQL 缺失/空白时回退操作名资源(恒非空,双语言)。
 * 列表行 / 详情页标题共用(组合版)。
 */
@Composable
fun SqlRecord.sqlHeadline(): String {
    sql?.let { s ->
        s.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return stringResource(sqlOperationLabelRes(operation))
}

/** SQL 记录 headline(非组合;经 Context 解析,供复制摘要等非组合路径使用)。 */
fun SqlRecord.sqlHeadline(context: Context): String {
    sql?.let { s ->
        s.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return context.getString(sqlOperationLabelRes(operation))
}

/**
 * SQL 记录复制摘要(V17,列表行长按复制单一事实源,与算法摘要同规):
 * headline / 操作 / 数据库 / SQL / 行数元数据 / 秒级时间 / 包名,逐行拼接。
 */
@SuppressLint("DefaultLocale")
fun SqlRecord.sqlCopySummary(context: Context): String {
    val time = java.text.SimpleDateFormat(
        if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        java.util.Locale.getDefault(),
    ).format(java.util.Date(timestamp))
    return buildString {
        append(sqlHeadline(context))
        append('\n')
        append(sqlOperationLabel(context, operation))
        append('\n').append(context.getString(R.string.record_data_db)).append(' ').append(databaseName)
        sql?.let { append('\n').append(context.getString(R.string.record_data_sql)).append(' ').append(it) }
        rowCount?.let { append('\n').append(context.getString(R.string.record_data_rows)).append(' ').append(it) }
        affectedCount?.let {
            append('\n').append(context.getString(R.string.record_data_affected)).append(' ').append(it)
        }
        append('\n').append(time)
        append('\n').append(packageName)
    }
}

/**
 * SQL 列表行(投影)复制摘要(V17,与 [SqlRecord.sqlCopySummary] 同规,但基于
 * 投影列 SQL 预览——列表页零大字段物化,长按复制不出整段 SQL 原文)。
 */
@SuppressLint("DefaultLocale")
fun SqlRow.sqlRowSummary(context: Context): String {
    val time = java.text.SimpleDateFormat(
        if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        java.util.Locale.getDefault(),
    ).format(java.util.Date(timestamp))
    return buildString {
        append(sqlHeadline(context))
        append('\n')
        append(sqlOperationLabel(context, operation))
        append('\n').append(context.getString(R.string.record_data_db)).append(' ').append(databaseName)
        sqlPreview?.let { append('\n').append(context.getString(R.string.record_data_sql)).append(' ').append(it) }
        rowCount?.let { append('\n').append(context.getString(R.string.record_data_rows)).append(' ').append(it) }
        affectedCount?.let {
            append('\n').append(context.getString(R.string.record_data_affected)).append(' ').append(it)
        }
        append('\n').append(time)
        append('\n').append(packageName)
    }
}

private fun SqlRow.sqlHeadline(context: Context): String {
    sqlPreview?.let { s ->
        s.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return context.getString(sqlOperationLabelRes(operation))
}