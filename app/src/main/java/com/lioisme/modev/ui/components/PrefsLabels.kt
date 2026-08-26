package com.lioisme.modev.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lioisme.modev.R
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.PrefsOperation
import com.lioisme.modev.data.mapping.PrefsRow

/**
 * 偏好监听标签资源(V28):类别/操作显示名单一事实源,
 * 与 [behaviorKindLabelRes]/[sqlKindLabelRes] 同规(筛选菜单 / 列表行 / 详情页共用)。
 */

/** 偏好类别显示名资源。 */
@StringRes
fun prefsKindLabelRes(kind: PrefsKind): Int = when (kind) {
    PrefsKind.OPEN -> R.string.kind_prefs_open
    PrefsKind.GET -> R.string.kind_prefs_get
    PrefsKind.PUT -> R.string.kind_prefs_put
    PrefsKind.REMOVE -> R.string.kind_prefs_remove
    PrefsKind.CLEAR -> R.string.kind_prefs_clear
}

/** 偏好操作标签资源。 */
@StringRes
fun prefsOperationLabelRes(operation: PrefsOperation): Int = when (operation) {
    PrefsOperation.OPEN -> R.string.op_prefs_open
    PrefsOperation.GET_STRING -> R.string.op_prefs_get_string
    PrefsOperation.GET_INT -> R.string.op_prefs_get_int
    PrefsOperation.GET_LONG -> R.string.op_prefs_get_long
    PrefsOperation.GET_FLOAT -> R.string.op_prefs_get_float
    PrefsOperation.GET_BOOLEAN -> R.string.op_prefs_get_boolean
    PrefsOperation.GET_STRING_SET -> R.string.op_prefs_get_string_set
    PrefsOperation.GET_ALL -> R.string.op_prefs_get_all
    PrefsOperation.CONTAINS -> R.string.op_prefs_contains
    PrefsOperation.PUT_STRING -> R.string.op_prefs_put_string
    PrefsOperation.PUT_INT -> R.string.op_prefs_put_int
    PrefsOperation.PUT_LONG -> R.string.op_prefs_put_long
    PrefsOperation.PUT_FLOAT -> R.string.op_prefs_put_float
    PrefsOperation.PUT_BOOLEAN -> R.string.op_prefs_put_boolean
    PrefsOperation.PUT_STRING_SET -> R.string.op_prefs_put_string_set
    PrefsOperation.REMOVE -> R.string.op_prefs_remove
    PrefsOperation.CLEAR -> R.string.op_prefs_clear
}

/** 偏好操作标签(组合包装)。 */
@Composable
fun prefsOperationLabel(operation: PrefsOperation): String =
    stringResource(prefsOperationLabelRes(operation))

/**
 * 偏好列表行复制摘要(V28,与行为摘要同规):
 * headline(键/文件)/ 类别·操作 / 文件路径 / 时间 / 包名,逐行拼接。
 */
fun PrefsRow.prefsSummary(context: Context): String {
    val time = java.text.SimpleDateFormat(
        if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        java.util.Locale.getDefault(),
    ).format(java.util.Date(timestamp))
    return buildString {
        append(headlineText() ?: context.getString(prefsOperationLabelRes(operation)))
        append('\n')
        append(context.getString(prefsKindLabelRes(kind)))
        append(" · ")
        append(context.getString(prefsOperationLabelRes(operation)))
        path?.let { append('\n').append(it) }
        append('\n').append(time)
        append('\n').append(packageName)
    }
}
