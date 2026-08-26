package com.lioisme.modev.ui.components

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.BehaviorOperation
import com.lioisme.modev.data.mapping.BehaviorRow

/**
 * 行为监听标签资源(V27):类别/操作显示名单一事实源,
 * 与 [sqlKindLabelRes]/[kindLabelRes] 同规(筛选菜单 / 列表行 / 详情页共用)。
 */

/** 行为类别显示名资源。 */
@StringRes
fun behaviorKindLabelRes(kind: BehaviorKind): Int = when (kind) {
    BehaviorKind.FILE_READ -> R.string.kind_behavior_file_read
    BehaviorKind.FILE_WRITE -> R.string.kind_behavior_file_write
    BehaviorKind.FILE_DELETE -> R.string.kind_behavior_file_delete
    BehaviorKind.FILE_RENAME -> R.string.kind_behavior_file_rename
    BehaviorKind.FILE_LIST -> R.string.kind_behavior_file_list
    BehaviorKind.ASSET_READ -> R.string.kind_behavior_asset_read
    BehaviorKind.SHELL_EXEC -> R.string.kind_behavior_shell_exec
}

/** 行为操作标签资源。 */
@StringRes
fun behaviorOperationLabelRes(operation: BehaviorOperation): Int = when (operation) {
    BehaviorOperation.OPEN_READ -> R.string.op_behavior_open_read
    BehaviorOperation.READ_ALL -> R.string.op_behavior_read_all
    BehaviorOperation.OPEN_WRITE -> R.string.op_behavior_open_write
    BehaviorOperation.WRITE_ALL -> R.string.op_behavior_write_all
    BehaviorOperation.MKDIR -> R.string.op_behavior_mkdir
    BehaviorOperation.MKDIRS -> R.string.op_behavior_mkdirs
    BehaviorOperation.DELETE -> R.string.op_behavior_delete
    BehaviorOperation.RENAME -> R.string.op_behavior_rename
    BehaviorOperation.LIST -> R.string.op_behavior_list
    BehaviorOperation.ASSET_OPEN -> R.string.op_behavior_asset_open
    BehaviorOperation.ASSET_OPEN_FD -> R.string.op_behavior_asset_open_fd
    BehaviorOperation.ASSET_LIST -> R.string.op_behavior_asset_list
    BehaviorOperation.EXEC -> R.string.op_behavior_exec
}

/** 行为操作标签(组合包装)。 */
@Composable
fun behaviorOperationLabel(operation: BehaviorOperation): String =
    stringResource(behaviorOperationLabelRes(operation))

/**
 * 行为列表行复制摘要(V27,与 SQL 摘要同规):
 * headline / 类别·操作 / 主体(路径或命令)/ 时间 / 包名,逐行拼接。
 */
@SuppressLint("DefaultLocale")
fun BehaviorRow.behaviorSummary(context: Context): String {
    val time = java.text.SimpleDateFormat(
        if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        java.util.Locale.getDefault(),
    ).format(java.util.Date(timestamp))
    val subject = commandDisplay() ?: path.orEmpty()
    return buildString {
        append(headlineText() ?: context.getString(behaviorOperationLabelRes(operation)))
        append('\n')
        append(context.getString(behaviorKindLabelRes(kind)))
        append(" · ")
        append(context.getString(behaviorOperationLabelRes(operation)))
        if (subject.isNotBlank()) {
            append('\n').append(subject)
        }
        destPath?.let { append('\n').append(it) }
        append('\n').append(time)
        append('\n').append(packageName)
    }
}

private fun BehaviorRow.commandDisplay(): String? = command?.joinToString(" ")?.takeIf { it.isNotBlank() }
