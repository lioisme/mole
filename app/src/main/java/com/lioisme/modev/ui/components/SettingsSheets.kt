package com.lioisme.modev.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lioisme.modev.R

/**
 * 监听设置输入弹层集(V21 T2 契约;V25 自 ui/settings 迁入 components,消费方 = 每应用监听设置页;
 * V39 R7 收编:全部收敛为 [ModevInputSheet] 薄封装,ModalBottomSheet 外壳/hide 动画收口/
 * imePadding/容器色契约由脚手架单点保证,本文件仅表达标题/提示/校验/解析语义)。
 */

/** 单字节字段上限(≤0 = ∞ 由调用方解释;空输入回落 0,与既有行为一致)。 */
@Composable
internal fun MaxBytesSheet(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModevInputSheet(
        title = stringResource(R.string.capture_max_bytes_dialog_title),
        initialValue = if (initial <= 0) "0" else initial.toString(),
        hint = stringResource(R.string.capture_max_bytes_hint),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        digitOnly = true,
        maxLength = 9,
        validate = { true },
        onSubmit = { onConfirm(it.toIntOrNull() ?: 0) },
        onDismiss = onDismiss,
    )
}

/** 忽略栈前缀编辑(底部 sheet,每行一个,去空行去重)。 */
@Composable
internal fun IgnoreStackSheet(
    initial: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) = PrefixListSheet(
    titleRes = R.string.capture_ignore_stack_dialog_title,
    hintRes = R.string.capture_ignore_stack_dialog_hint,
    initial = initial,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

/**
 * 忽略路径前缀编辑(V27 行为降噪;底部 sheet,每行一个,去空行去重):
 * 与 [IgnoreStackSheet] 同构,语义对象为主体路径而非调用栈。
 */
@Composable
internal fun IgnorePathSheet(
    initial: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) = PrefixListSheet(
    titleRes = R.string.capture_ignore_path_dialog_title,
    hintRes = R.string.capture_ignore_path_dialog_hint,
    initial = initial,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

/** 前缀清单编辑共用实现(多行输入,每行一个前缀)。 */
@Composable
private fun PrefixListSheet(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    initial: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModevInputSheet(
        title = stringResource(titleRes),
        initialValue = initial.joinToString("\n"),
        hint = stringResource(hintRes),
        minLines = 4,
        validate = { true },
        onSubmit = { text ->
            onConfirm(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        },
        onDismiss = onDismiss,
    )
}

/** 整数值编辑底部 sheet(数据库监听结果闸门行数/字符上限共用)。 */
@Composable
internal fun IntLimitSheet(
    @StringRes titleRes: Int,
    @StringRes hintRes: Int,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModevInputSheet(
        title = stringResource(titleRes),
        initialValue = initial.toString(),
        hint = stringResource(hintRes),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        digitOnly = true,
        maxLength = 9,
        validate = { true },
        onSubmit = { onConfirm(it.toIntOrNull() ?: 0) },
        onDismiss = onDismiss,
    )
}
