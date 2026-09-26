package com.lioisme.modev.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R

/**
 * 统一确认对话框:全 App 居中危险确认/信息确认唯一出口。
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

/**
 * 统一只读值对话框(V46 R3/D3):展示完整值 + 复制动作,零输入。
 *
 * 与 [ModevConfirmDialog] 同居本文件 —— `AlertDialog(` 全 App 唯一出口
 * (强约束 #27 / `DialogConventionTest` R1),且同样**不发 icon**(R2)。
 * 强约束 #4 边界:需要键盘输入的弹层仍一律底部 sheet,本组件只做只读展示,
 * 故其内部永不出现 TextField 形态(`ModevValueDialogTest` 源码门禁盯这一点)。
 *
 * 值以等宽字体呈现(令牌/密钥一类秘密值),可完整换行不截断;
 * 复制后的收口(关窗 / Snackbar)由调用方在 [onCopy] 内决定,组件零业务假设。
 *
 * @param valueTestTag 值节点锚点;[copyTestTag] 复制按钮锚点;[closeTestTag] 关闭按钮锚点。
 */
@Composable
fun ModevValueDialog(
    title: String,
    value: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    valueTestTag: String? = null,
    copyTestTag: String? = null,
    closeTestTag: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .let { base -> valueTestTag?.let { base.testTag(it) } ?: base },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onCopy,
                modifier = copyTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diagnostics_detail_copy))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = closeTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
