package com.lioisme.modev.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.lioisme.modev.R

/**
 * 统一确认对话框(V39 R7):全 App 居中危险确认/信息确认唯一出口。
 *
 * 契约(取代逐屏手写 AlertDialog 复制模板):
 * - **无图标**(V39 验收:对话框不带 icon,标题上方居中大图标形态整体退役);
 * - `containerColor = surfaceContainerHigh`(V21 弹窗契约,宿主页 Scaffold 恒 surface);
 * - 按钮恒为 TextButton 对:确认([confirmTextRes],破坏性动作 [destructive]=true 时
 *   error 色文本)+ 取消([R.string.cancel]);
 * - 仅危险确认/关于类保留居中 AlertDialog(AGENTS 强约束 #4),带键盘输入的弹层
 *   一律走 [ModevSheetScaffold] 系底部 sheet,禁止用本组件承载输入。
 *
 * @param confirmTestTag 测试锚点(如 `records-clear-confirm`/`token-revoke-confirm`)。
 */
@Composable
fun ModevConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes confirmTextRes: Int = R.string.confirm,
    destructive: Boolean = true,
    confirmTestTag: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = confirmTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Text(
                    stringResource(confirmTextRes),
                    color =
                        if (destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
