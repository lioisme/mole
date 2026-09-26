package com.lioisme.modev.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.domain.server.PortValidator
import kotlinx.coroutines.launch

/**
 * 统一底部弹层脚手架:全 App ModalBottomSheet 唯一出口,
 * 收编此前散落的输入 sheet / 单选 sheet / 端口 sheet / 详情 sheet 手写复制。
 *
 * 契约(AGENTS 强约束 #4,V21 T2 双内边距缺陷防线):
 * - 显式 `containerColor = surfaceContainerHigh`;
 * - 内容 Column **仅** `.imePadding`,禁叠 `navigationBarsPadding`(M3 已内部消费);
 * - **hide 动画完成后才回调关闭**(收口闪黑;此前 Language/Appearance/Color/
 *   DiagnosticsDetail 四处直连偏差经本组件统一修复);
 * - 标题恒 titleLarge SemiBold @horizontal 24;内容区自管水平 padding
 *   (与既有各 sheet 逐像素对齐);
 * - 带键盘输入的弹层一律走本系(居中 AlertDialog 仅危险确认/关于类)。
 */
class ModevSheetScope internal constructor(
    private val hideThen: ((() -> Unit)?) -> Unit,
) {
    /** 播完退场动画后执行 [action](选择/提交/关闭统一收口)。 */
    fun dismissWith(action: () -> Unit = {}) = hideThen(action)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModevSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(ModevSheetScope) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun hideThen(action: (() -> Unit)?) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action?.invoke() }
    }

    ModalBottomSheet(
        onDismissRequest = { hideThen(onDismiss) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            content(ModevSheetScope(::hideThen))
        }
    }
}

/** sheet 内容通用水平内边距(与标题 24dp 对齐)。 */
val SheetContentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)

/**
 * 输入型底部 sheet(标题 + 单行/多行输入 + 校验 + 取消/确认):
 * [validate] 返回 false 时置错误态(supportingText = [errorText]),不关闭;
 * 确认经 hide 动画后回调 [onSubmit](携带原样输入文本,数值解析由调用方负责)。
 *
 * [liveError] 是**输入期即裁决**的形态(V47 WP-5 画像参数编辑器):返回非 null 文案即
 * 实时置错误态并硬闸确认 —— 与 [validate] 的「按确认才判」互补而非替代
 * (既有消费方零改动)。返回的文案由调用方解析(本组件零业务与资源感知),
 * 与提交前的最终裁决同源,故「看着合法却存不下」不可能发生。
 */
@Composable
fun ModevInputSheet(
    title: String,
    initialValue: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    errorText: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    digitOnly: Boolean = false,
    maxLength: Int? = null,
    minLines: Int = 1,
    inputTestTag: String? = null,
    confirmTestTag: String? = null,
    liveError: ((String) -> String?)? = null,
    validate: (String) -> Boolean = { it.isNotBlank() },
) {
    var text by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf(false) }
    val liveErrorText = liveError?.invoke(text)
    ModevSheet(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
    ) { sheet ->
        Column(Modifier.fillMaxWidth().padding(SheetContentPadding)) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val filtered = if (digitOnly) input.filter { it.isDigit() } else input
                    text = if (maxLength != null) filtered.take(maxLength) else filtered
                    error = false
                },
                label = label?.let { { Text(it) } },
                isError = error || liveErrorText != null,
                supportingText =
                    when {
                        error && errorText.isNotEmpty() -> {
                            { Text(errorText) }
                        }

                        liveErrorText != null -> {
                            { Text(liveErrorText, color = MaterialTheme.colorScheme.error) }
                        }

                        hint != null -> {
                            { Text(hint) }
                        }

                        else -> null
                    },
                singleLine = minLines == 1,
                minLines = minLines,
                keyboardOptions = keyboardOptions,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .let { base -> inputTestTag?.let { base.testTag(it) } ?: base },
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (liveErrorText == null && validate(text)) {
                            sheet.dismissWith { onSubmit(text) }
                        } else {
                            error = true
                        }
                    },
                    modifier = confirmTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

/**
 * 端口编辑底部 sheet(wire/MCP 共用,V39 合并;V42 T3 可用性校验升级):
 * 数字输入 + maxLength + 本地范围预拒 + **异步可用性校验**(D-3:确认 → busy →
 * IO 校验(特权/冲突/占用,经 [validatePort])→ 通过 hide 动画后回调 [onConfirm] /
 * 失败分型行内错误并保持 sheet 打开——硬阻止保存,不可带病写入)。
 * [validatePort] 由调用方注入(PortValidator 组装:自身端口豁免/对端冲突/
 * 探测地址随统一外部访问开关);范围校验在本地同步先行(省 IO)。
 */
@Composable
fun ModevPortSheet(
    label: String,
    currentPort: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    validatePort: (suspend (Int) -> PortValidator.PortCheck)? = null,
) {
    var text by remember { mutableStateOf(currentPort.toString()) }
    // 错误态持 PortCheck 分型(组合侧映射文案——stringResource 仅可组合上下文)。
    var lastError by remember { mutableStateOf<PortValidator.PortCheck?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    @Composable
    fun errorLabel(check: PortValidator.PortCheck): String = when (check) {
        PortValidator.PortCheck.OutOfRange -> stringResource(R.string.settings_port_invalid)
        PortValidator.PortCheck.Privileged -> stringResource(R.string.settings_port_privileged)
        is PortValidator.PortCheck.Conflict -> stringResource(R.string.settings_port_conflict, check.otherLabel)
        PortValidator.PortCheck.InUse -> stringResource(R.string.settings_port_in_use)
        PortValidator.PortCheck.Available -> ""
    }

    ModevSheet(
        title = stringResource(R.string.settings_port_edit),
        onDismiss = onDismiss,
        modifier = modifier,
    ) { sheet ->
        Column(Modifier.fillMaxWidth().padding(SheetContentPadding)) {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { it.isDigit() }.take(PORT_INPUT_MAX_LENGTH)
                    lastError = null
                },
                label = label.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                isError = lastError != null,
                supportingText = lastError?.let { check -> { Text(errorLabel(check)) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("port-input"),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) {
                    Text(
                        stringResource(R.string.settings_port_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp).testTag("port-checking"),
                    )
                }
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val candidate = text.toIntOrNull()
                        if (candidate == null || candidate !in 1..65535) {
                            // 范围本地同步预拒(零 IO)。
                            lastError = PortValidator.PortCheck.OutOfRange
                            return@TextButton
                        }
                        busy = true
                        scope.launch {
                            val check = validatePort?.invoke(candidate)
                                ?: PortValidator.PortCheck.Available
                            if (check == PortValidator.PortCheck.Available) {
                                sheet.dismissWith { onConfirm(candidate) }
                            } else {
                                lastError = check
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("port-confirm"),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

/** 端口输入长度上限(65535 五位;ModevPortSheet 输入滤器消费)。 */
private const val PORT_INPUT_MAX_LENGTH = 5

/** 单选 sheet 选项:值 + 已解析标签(UI 侧经 stringResource 产出,组件零资源感知)。 */
data class ChoiceOption<T>(val value: T, val label: String, val testTag: String? = null)

/**
 * 单选列表底部 sheet(画像等选择型弹层共用;V19 T6 矩阵 M10 同款
 * ListItem + leading RadioButton 形态):选中即经 hide 动画回调 [onSelect]。
 */
@Composable
fun <T> ModevChoiceSheet(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModevSheet(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
    ) { sheet ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            options.forEach { option ->
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    headlineContent = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingContent = {
                        // U3〔P2〕只读 RadioButton:不再独立可聚焦(onClick=null),
                        // 避免与行 clickable 构成 TalkBack 双节点重复;选中态仅作视觉指示。
                        RadioButton(
                            selected = option.value == selected,
                            onClick = null,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .let { base ->
                                option.testTag?.let { base.testTag(it) } ?: base
                            }
                            // 单一语义节点:行整体 selectable(role=RadioButton),
                            // 替代「RadioButton 可聚焦 + 行 clickable」双节点重复。
                            .selectable(
                                selected = option.value == selected,
                                role = Role.RadioButton,
                                onClick = { sheet.dismissWith { onSelect(option.value) } },
                            ),
                )
            }
        }
    }
}

/**
 * 可搜索单选底部 sheet(V47 WP-5b:机型库品牌 / 型号选择器专用)。
 *
 * 与 [ModevChoiceSheet] 同一出口(都建在 [ModevSheet] 上,契约 #27 的
 * `ModalBottomSheet` 单点不破),差别只在**候选量级**:内置机型库单品牌型号数可达数百、
 * 全库数千行,整表 `verticalScroll` 会一次组合全部行 ⇒ 列表走 LazyColumn,
 * 并按 [SHEET_MAX_VISIBLE_ROWS] 截断渲染 + 明说「请缩小搜索范围」(截断必须可见,
 * 否则用户读到的是「库里只有这些」)。
 *
 * 过滤留在组件内(默认按 label 子串、忽略大小写):唯一的调用形态就是「按名字找」,
 * 让每个调用方各写一遍 filter 才是漂移源头。需要别的判据时再传 [match]。
 */
@Composable
fun <T> ModevSearchableChoiceSheet(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T?,
    placeholder: String,
    emptyText: String,
    truncatedText: (Int) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    match: (ChoiceOption<T>, String) -> Boolean = { option, query ->
        query.isBlank() || option.label.contains(query, ignoreCase = true)
    },
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) { options.filter { match(it, query) } }
    val visible = filtered.take(SHEET_MAX_VISIBLE_ROWS)

    ModevSheet(
        title = title,
        onDismiss = onDismiss,
        modifier = modifier,
    ) { sheet ->
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet-search-input"),
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("sheet-search-empty"),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    if (filtered.size > visible.size) {
                        item {
                            Text(
                                truncatedText(filtered.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("sheet-search-truncated"),
                            )
                        }
                    }
                    // 不设 key:同品牌内 label 可能重复(上游改数据即出现),
                    // 而重复 key 是硬崩;这里也不需要跨重组保位。
                    items(visible) { option ->
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            headlineContent = {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .let { base ->
                                    option.testTag?.let { base.testTag(it) } ?: base
                                }
                                .clickable {
                                    sheet.dismissWith { onSelect(option.value) }
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

const val SHEET_MAX_VISIBLE_ROWS = 120

/**
 * sheet 底部取消/确认按钮行(自定义内容 sheet 复用:键值编辑 / 关键词编辑等
 * 多控件形态无法收敛为 [ModevInputSheet] 单输入,内容经 [ModevSheet] 承载,
 * 动作行统一走本组件;[onConfirm] 由调用方经 ModevSheetScope.dismissWith 收口)。
 */
@Composable
fun ModevSheetButtonRow(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    confirmTestTag: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.cancel))
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = confirmTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}
