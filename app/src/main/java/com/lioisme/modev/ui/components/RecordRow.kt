package com.lioisme.modev.ui.components

import com.lioisme.modev.ui.theme.Tokens

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import com.lioisme.modev.domain.record.BehaviorRow
import com.lioisme.modev.domain.record.CryptoRow
import com.lioisme.modev.domain.record.PrefsRow
import com.lioisme.modev.domain.record.RowModel
import com.lioisme.modev.domain.record.SqlRow
import com.lioisme.modev.domain.record.WebViewRow
import com.lioisme.modev.domain.record.NetworkRow
import com.lioisme.modev.domain.record.DeviceEnvRow
import com.lioisme.modev.domain.record.UiInteractionRow
import com.lioisme.modev.domain.record.SystemRow
import com.lioisme.modev.domain.record.ProcessRow
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.NetworkOperation
import com.lioisme.modev.ui.common.formatSecond
import com.lioisme.modev.ui.settings.StorageFormat
import com.lioisme.modev.ui.theme.behaviorKindColor
import com.lioisme.modev.ui.theme.kindColor
import com.lioisme.modev.ui.theme.prefsKindColor
import com.lioisme.modev.ui.theme.sqlKindColor
import com.lioisme.modev.ui.theme.webviewKindColor
import com.lioisme.modev.ui.theme.networkKindColor
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.DeviceEnvOperation
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.UiInteractionOperation
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.SystemOperation
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.core.model.ProcessOperation
import com.lioisme.modev.ui.theme.deviceEnvKindColor
import com.lioisme.modev.ui.theme.uiInteractionKindColor
import com.lioisme.modev.ui.theme.systemKindColor
import com.lioisme.modev.ui.theme.processKindColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录行(V27 M1-T3 重构;V40 T4 行内上下文标签;V40 T5.3 ListItem 化;
 * V43 T4 一行一参数重构,决策 D-1/D-2/D-3;**V46 R1/D1 容器化 + 尾栏退役**):
 *
 * **V46 R1/D1 版式**:容器 = `Box`(`clip(MaterialTheme.shapes.medium)` +
 * `background(surfaceContainerLow)` + `combinedClickable`),`ListItem` 转透明
 * (`containerColor = Color.Transparent`)仅承载内容布局 —— MD3 内边距/字阶/leading
 * 槽契约不破,圆角与可见波纹由容器层单点保证。信息架构槽位:
 * - leading 类别图标(36dp 圆角方 tonal 底,类别色@13% + 类别色图标 20dp);
 * - headline = **身份行**([IdentityLine],testTag `record-row-identity`):
 *   身份文本(weight 1f)+ 右上方秒级时间与方向微徽章;
 * - supporting = **字段行列表**([FieldLines]/[RowField]),每个逻辑参数各占一行,
 *   行内恒 ≤1 参数;路径/命令/URL 行启用 [MiddleEllipsisText] 保头掐腰中缀省略(D-2);
 *   全部标签走资源四语(D-3);
 * - **`trailingContent` 恒不传**:M3 ListItem 的文本列宽 = 行宽 − leading − trailing,
 *   尾栏一旦存在即永久削掉正文可用宽度(尾栏只有两行内容、顶部对齐,其下方是死空间),
 *   这就是「底部参数行右侧明明还有位置却被提前截断」的根因;时间与徽章下钻进身份行,
 *   预留只作用于首行,参数行因此通栏。
 *
 * `record-row` testTag、presentation 单一数据出口、单击/长按/时间契约均不变。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(
    record: RowModel,
    modifier: Modifier = Modifier,
    /** 来源语境标签(应用名;null = 单应用列表不渲染,降噪)。 */
    contextLabel: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val presentation =
        when (record) {
            is CryptoRow -> cryptoPresentation(record)
            is SqlRow -> sqlPresentation(record)
            is BehaviorRow -> behaviorPresentation(record)
            is PrefsRow -> prefsPresentation(record)
            is WebViewRow -> webviewPresentation(record)
            is NetworkRow -> networkPresentation(record)
            is DeviceEnvRow -> deviceEnvPresentation(record)
            is UiInteractionRow -> uiInteractionPresentation(record)
            is SystemRow -> systemPresentation(record)
            is ProcessRow -> processPresentation(record)
        }
    // 容器层只管「形状 + 底色 + 最小高度」;手势、合并语义与 record-row 锚点恒落在
    // ListItem 节点上 —— V46 实测 M3 1.4.0 的 ListItem 自身就是 mergeDescendants 节点
    // (行内全部文本收在它身上)。把锚点放到外层 Box 会让 rowCount 的标准形态
    // `hasTestTag("record-row") and hasText(...)` 整体归零(三个记录列表测试类全红)。
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MIN_ROW_HEIGHT)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { role = Role.Button }
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        },
                    )
                    .testTag(ROW_TEST_TAG),
            leadingContent = {
                LeadingIcon(icon = presentation.icon, tint = presentation.accent)
            },
            headlineContent = {
                IdentityLine(
                    headline = presentation.headline,
                    timestamp = presentation.timestamp,
                    badges = presentation.badges,
                )
            },
            supportingContent = {
                FieldLines(contextLabel = contextLabel, fields = presentation.fields)
            },
        )
    }
}

/**
 * 身份行(V46 R1/D1):headline 与「秒级时间 + 语义徽章」同排。
 *
 * 时间与徽章由本行承载而非 ListItem `trailingContent` —— 尾栏预留宽度只作用于这一行,
 * 参数行因此通栏。垂直对齐取 Top:时间/徽章列自上方起排,徽章落定在 headline 之下,
 * 与时间线组头(按秒分组)在竖向上呼应。
 */
@Composable
private fun IdentityLine(
    headline: String,
    timestamp: Long,
    badges: List<RowBadge>,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("record-row-identity"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = headline,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TimeText(timestamp = timestamp)
            badges.forEach { badge -> MicroBadge(badge) }
        }
    }
}

/** 参数行列表(V43 T4 一行一参数契约不变;V46 起自 RecordRow 内联抽出,恒通栏)。 */
@Composable
private fun FieldLines(contextLabel: String?, fields: List<RowField>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // V43 T4/D-1:应用语境独立成行(全局列表形态;不再与类别词元拼行)
        if (contextLabel != null && contextLabel.isNotBlank()) {
            FieldText(
                RowField(
                    labelRes = R.string.record_field_app,
                    value = contextLabel,
                ),
            )
        }
        fields.forEach { field -> FieldText(field) }
    }
}

/**
 * 行字段(V43 T4/D-1):一行恒承载一个逻辑参数。
 *
 * - [labelRes] null → 纯值行(类别词元/自描述值);非 null → 「标签 值」合并单 Text;
 * - [mono] 值走等宽字体(数据段/路径/SQL);
 * - [valueColor] 非 null = 前景色(类别词元用 accent);
 * - [middleEllipsis] 长值行启用保头掐腰中缀省略(D-2:路径/命令/URL/预览)。
 */
private data class RowField(
    @StringRes val labelRes: Int? = null,
    val value: String,
    val mono: Boolean = false,
    val valueColor: Color? = null,
    val middleEllipsis: Boolean = false,
)

/** 统一行展示规格(presentation 单一数据出口; supporting = 字段行列表)。 */
private data class RowPresentation(
    val icon: ImageVector,
    val accent: Color,
    val headline: String,
    val fields: List<RowField>,
    val badges: List<RowBadge>,
    /** 尾栏秒级时间(V27 契约保留:与组头同源口径,由行装配层格式化)。 */
    val timestamp: Long,
)

/** 微徽章规格(text 展示文案;accent 非空 = 语义色呈现,空 = 中性 surfaceVariant)。 */
private data class RowBadge(
    val text: String,
    val accent: Color? = null,
)

/** 字段行渲染(V43 T4:单一 Text 节点 = 单参数;标签与值经 SpanStyle 分层)。 */
@Composable
private fun FieldText(field: RowField) {
    val body = MaterialTheme.colorScheme.onSurfaceVariant
    val monoFamily = FontFamily.Monospace
    when {
        field.middleEllipsis -> MiddleEllipsisText(
            text = field.value,
            color = body,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (field.mono) monoFamily else FontFamily.Default,
                ),
        )

        field.labelRes == null -> Text(
            text = field.value,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontFamily = if (field.mono) monoFamily else FontFamily.Default,
                ),
            color = field.valueColor ?: body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        else -> {
            val label = stringResource(field.labelRes)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = body)) { append("$label ") }
                    withStyle(
                        SpanStyle(
                            fontFamily = if (field.mono) monoFamily else FontFamily.Default,
                            color = body,
                        ),
                    ) { append(field.value) }
                },
                style = MaterialTheme.typography.labelMedium,
                color = body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * leading 类别图标(V40 T5.3 内联,原 LeadingBadge 私有件):
 * 36dp 圆角方 tonal 底(accent@13%)+ accent 图标 20dp。
 */
@Composable
private fun LeadingIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier =
            Modifier
                .size(BADGE_SIZE.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = Tokens.badgeContainerAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 尾栏秒级时间(labelSmall onSurfaceVariant;12/24h 时制尊重系统设置,
 * 与时间线组头 [com.lioisme.modev.ui.common.TimelineGroup] 同口径同源实现)。
 * V27 回归修复:重构脚手架时误删,V20 契约「时间恒在行内右栏」恢复。
 * V43 T5:经 [com.lioisme.modev.ui.common.formatSecond] 共享格式化器
 * (旧实现每行槽位各持一个 SimpleDateFormat,深滚组合分配高)。
 */
@Composable
private fun TimeText(timestamp: Long) {
    val context = LocalContext.current
    val is24h = remember(context) { DateFormat.is24HourFormat(context) }
    Text(
        text = remember(timestamp, is24h) { formatSecond(timestamp, Locale.getDefault(), is24h) },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 尾栏微徽章(方向/状态;紧凑胶囊)。 */
@Composable
private fun MicroBadge(badge: RowBadge) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color =
            badge.accent?.copy(alpha = Tokens.badgeContainerAlpha)
                ?: MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = badge.text,
            style = MaterialTheme.typography.labelSmall,
            color = badge.accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 干预动作标记本地化(V43 T4/D-3:协议值 BLOCKED/DETECTED 不再以英文原样上屏)。 */
@Composable
private fun interventionActionLabel(action: String?): String? =
    when (action?.uppercase(Locale.ROOT)) {
        "BLOCKED" -> stringResource(R.string.record_action_blocked)
        "DETECTED" -> stringResource(R.string.record_action_detected)
        null -> null
        else -> action
    }

// ---------------------------------------------------------------------------
// 族形态:每族产出统一 [RowPresentation](一行一参数字段列表,V43 T4)。
// ---------------------------------------------------------------------------

/** 算法形态:类别行 / 操作行 / 数据段行(密钥>别名>输出>输入,恒单段)。 */
@Composable
private fun cryptoPresentation(record: CryptoRow): RowPresentation {
    val r = record.record
    val accent = kindColor(r.kind)
    val kindText = stringResource(kindLabelRes(r.kind))
    val operationText = stringResource(operationLabelRes(r.operation))
    val dataPair = r.valuableDataPair()
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        dataPair?.let { (labelRes, value) ->
            add(RowField(labelRes = labelRes, value = value, mono = true))
        }
    }
    return RowPresentation(
        icon = KindIcons.crypto(r.kind, r.direction),
        accent = accent,
        headline = r.displayAlgorithm(),
        fields = fields,
        badges = r.direction?.let { dir -> listOf(RowBadge(directionLabel(dir))) } ?: emptyList(),
        timestamp = r.timestamp,
    )
}

/** 数据库形态:类别 / 操作 / 库名 / 行数 / 影响行各成行,SQL 预览中缀省略。 */
@Composable
private fun sqlPresentation(record: SqlRow): RowPresentation {
    val accent = sqlKindColor(record.kind)
    val kindText = stringResource(sqlKindLabelRes(record.kind))
    val operationText = stringResource(sqlOperationLabelRes(record.operation))
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        record.databaseName.orEmpty().takeIf { it.isNotBlank() }?.let {
            add(RowField(labelRes = R.string.record_data_db, value = it, mono = true, middleEllipsis = true))
        }
        record.rowCount?.let {
            add(RowField(value = pluralStringResource(R.plurals.record_data_rows_short, it, it)))
        }
        record.affectedCount?.let {
            add(RowField(value = pluralStringResource(R.plurals.record_data_affected_short, it, it)))
        }
        record.sqlPreview?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(RowField(value = it, mono = true, middleEllipsis = true))
        }
    }
    return RowPresentation(
        icon = KindIcons.sql(record.kind),
        accent = accent,
        headline = record.sqlHeadlineText(),
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

/** SQL 行 headline:投影无 SQL 首行可用时回退操作名(资源,恒非空)。 */
@Composable
private fun SqlRow.sqlHeadlineText(): String {
    sqlPreview?.let { s ->
        s.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return stringResource(sqlOperationLabelRes(operation))
}

/**
 * 行为形态:类别 / 操作 / 主体(path 或 argv 中缀省略)/
 * 重命名目标 / 文件大小各成行。
 */
@Composable
private fun behaviorPresentation(record: BehaviorRow): RowPresentation {
    val accent = behaviorKindColor(record.kind)
    val kindText = stringResource(behaviorKindLabelRes(record.kind))
    val operationText = stringResource(behaviorOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val subject =
        remember(record.command, record.path) {
            record.command?.joinToString(" ")?.takeIf { it.isNotBlank() }
                ?: record.path?.takeIf { it.isNotBlank() }
        }
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        subject?.let {
            add(RowField(value = it, mono = true, middleEllipsis = true))
        }
        record.destPath?.takeIf { it.isNotBlank() }?.let {
            add(RowField(labelRes = R.string.meta_dest_path, value = it, mono = true, middleEllipsis = true))
        }
        record.fileSize?.let {
            add(RowField(labelRes = R.string.meta_file_size, value = StorageFormat.formatBytes(it)))
        }
    }
    return RowPresentation(
        icon = KindIcons.behavior(record.kind),
        accent = accent,
        headline = headline,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

/**
 * 偏好形态:类别 / 操作 / 配置文件路径各成行。
 */
@Composable
private fun prefsPresentation(record: PrefsRow): RowPresentation {
    val accent = prefsKindColor(record.kind)
    val kindText = stringResource(prefsKindLabelRes(record.kind))
    val operationText = stringResource(prefsOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        record.path?.takeIf { it.isNotBlank() }?.let {
            add(RowField(value = it, mono = true, middleEllipsis = true))
        }
    }
    return RowPresentation(
        icon = KindIcons.prefs(record.kind),
        accent = accent,
        headline = headline,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

/**
 * WebView 形态:类别 / 操作 / 桥名 / URL 各成行,
 * 桥名与 headline(域名/桥名回退)重复时不再渲染(V3.5 去重原则)。
 */
@Composable
private fun webviewPresentation(record: WebViewRow): RowPresentation {
    val accent = webviewKindColor(record.kind)
    val kindText = stringResource(webviewKindLabelRes(record.kind))
    val operationText = stringResource(webviewOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        record.bridgeName?.takeIf { it.isNotBlank() && it != headline }?.let {
            add(RowField(labelRes = R.string.meta_bridge, value = it, mono = true))
        }
        record.url?.takeIf { it.isNotBlank() && it != headline }?.let {
            add(RowField(labelRes = R.string.meta_url, value = it, mono = true, middleEllipsis = true))
        }
    }
    return RowPresentation(
        icon = KindIcons.webview(record.kind),
        accent = accent,
        headline = headline,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

/**
 * 最有价值数据段原始选择(纯数据单一事实源,优先级 密钥 > 别名 > 输出 > 输入):
 * 返回(标签资源, 值)或 null;本地化由 UI 层(组合 / 非组合)各自承担。
 */
internal fun CryptoRecord.valuableDataPair(): Pair<Int, String>? = when {
    keyAlias != null -> R.string.record_data_alias to keyAlias
    key != null -> R.string.record_data_key to key.hex.take(24)
    output != null -> R.string.record_data_out to output.hex.take(24)
    input != null -> R.string.record_data_in to input.hex.take(24)
    else -> null
}

/**
 * 算法展示名单一事实源:algorithm 为空串/空白时回退类别名(恒有),
 * 杜绝列表行 headline、详情页标题、复制摘要首行出现空白。
 */
@Composable
fun CryptoRecord.displayAlgorithm(): String =
    algorithm.ifBlank { stringResource(kindLabelRes(kind)) }

/** 算法展示名(非组合;经 Context 解析,供事件回调 / 摘要等非组合路径使用)。 */
fun CryptoRecord.displayAlgorithm(context: Context): String =
    algorithm.ifBlank { context.getString(kindLabelRes(kind)) }

/**
 * 记录摘要文本(V13 列表行长按复制单一事实源):
 * 算法 / 操作 / 方向 / 最有价值数据段 / 秒级时间 / 包名,逐行拼接,无冗余空行。
 * 纯函数(非组合,时间格式尊重系统 12/24h 时制),宿主可直接在事件回调中调用。
 */
fun CryptoRecord.copySummary(context: Context): String {
    val time = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        Locale.getDefault(),
    ).format(Date(timestamp))
    return buildString {
        append(displayAlgorithm(context))
        append('\n')
        append(context.getString(operationLabelRes(operation)))
        direction?.let { dir ->
            append('\n').append(directionLabel(context, dir))
        }
        valuableDataPair()?.let { (labelRes, value) ->
            append('\n').append(context.getString(labelRes)).append(' ').append(value)
        }
        append('\n').append(time)
        append('\n').append(packageName)
    }
}

/** 类别显示名资源(筛选菜单 / 详情页元数据共用)。 */
@StringRes
fun kindLabelRes(kind: CryptoKind): Int = when (kind) {
    CryptoKind.MESSAGE_DIGEST -> R.string.kind_message_digest
    CryptoKind.CIPHER -> R.string.kind_cipher
    CryptoKind.MAC -> R.string.kind_mac
    CryptoKind.SIGNATURE -> R.string.kind_signature
    CryptoKind.KEY_PARAM -> R.string.kind_key_param
    CryptoKind.KEY_GENERATION -> R.string.kind_key_generation
    CryptoKind.KEY_FACTORY -> R.string.kind_key_factory
    CryptoKind.KEY_STORE -> R.string.kind_key_store
    CryptoKind.KEY_AGREEMENT -> R.string.kind_key_agreement
    CryptoKind.RANDOM -> R.string.kind_random
    CryptoKind.BASE64 -> R.string.kind_base64
}

/** 操作标签资源(2026-08-16 V3 起走资源,消除硬编码英文;i18n 四语)。 */
@StringRes
fun operationLabelRes(operation: CryptoOperation): Int = when (operation) {
    CryptoOperation.INIT -> R.string.op_init
    CryptoOperation.UPDATE -> R.string.op_update
    CryptoOperation.DO_FINAL -> R.string.op_do_final
    CryptoOperation.DIGEST -> R.string.op_digest
    CryptoOperation.SIGN -> R.string.op_sign
    CryptoOperation.VERIFY -> R.string.op_verify
    CryptoOperation.GET_IV -> R.string.op_get_iv
    CryptoOperation.CONSTRUCT -> R.string.op_construct
    CryptoOperation.GENERATE -> R.string.op_generate
    CryptoOperation.GENERATE_PRIVATE -> R.string.op_generate_private
    CryptoOperation.GENERATE_PUBLIC -> R.string.op_generate_public
    CryptoOperation.GENERATE_SECRET -> R.string.op_generate_secret
    CryptoOperation.GET_KEY -> R.string.op_get_key
    CryptoOperation.SET_KEY -> R.string.op_set_key
    CryptoOperation.DELETE_KEY -> R.string.op_delete_key
    CryptoOperation.LOAD -> R.string.op_load
    CryptoOperation.DO_PHASE -> R.string.op_do_phase
    CryptoOperation.GENERATE_SEED -> R.string.op_generate_seed
    CryptoOperation.ENCODE -> R.string.op_encode
    CryptoOperation.DECODE -> R.string.op_decode
    CryptoOperation.ENCRYPT -> R.string.op_encrypt
    CryptoOperation.DECRYPT -> R.string.op_decrypt
}

/** 操作标签(组合包装,视觉承载)。 */
@Composable
fun operationLabel(operation: CryptoOperation): String = stringResource(operationLabelRes(operation))

/** 方向标签资源(未知模式无固定资源,由调用方带模式号)。 */
@StringRes
fun directionLabelRes(direction: Int): Int? = when (direction) {
    1 -> R.string.dir_encrypt
    2 -> R.string.dir_decrypt
    3 -> R.string.dir_wrap
    4 -> R.string.dir_unwrap
    else -> null
}

/** 方向标签(组合包装;未知模式带模式号,同详情页展示)。 */
@Composable
fun directionLabel(direction: Int): String =
    directionLabelRes(direction)?.let { stringResource(it) } ?: stringResource(R.string.dir_mode, direction)

/** 方向标签(非组合;经 Context 解析,供事件回调使用)。 */
fun directionLabel(context: Context, direction: Int): String =
    directionLabelRes(direction)?.let { context.getString(it) } ?: context.getString(R.string.dir_mode, direction)

/** 行最小高度(V27 密度规范;V43 T4 一行一参数后行数增长,容器自适应)。 */
private val MIN_ROW_HEIGHT: Dp = 68.dp

/**
 * 行锚点 testTag —— 由本组件自持(V46:必须挂在 ListItem 合并语义节点上,
 * 宿主只传布局用 modifier,禁再自带同名 tag 造成双节点)。
 */
const val ROW_TEST_TAG = "record-row"

/** leading 徽章边长。 */
private const val BADGE_SIZE = 36

/**
 * 干预动作本地化(非组合,供摘要路径;BLOCKED/DETECTED 协议值映射资源,D-3)。
 */
fun interventionActionLabel(context: Context, action: String?): String? =
    when (action?.uppercase(Locale.ROOT)) {
        "BLOCKED" -> context.getString(R.string.record_action_blocked)
        "DETECTED" -> context.getString(R.string.record_action_detected)
        else -> action
    }

// ---------------------------------------------------------------------------
// WebView 标签资源
// ---------------------------------------------------------------------------

/** WebView 类别显示名资源(筛选菜单 / 详情页元数据共用)。 */
@StringRes
fun webviewKindLabelRes(kind: WebViewKind): Int = when (kind) {
    WebViewKind.PAGE_LOAD -> R.string.kind_wv_page_load
    WebViewKind.EVALUATE_JS -> R.string.kind_wv_evaluate_js
    WebViewKind.JS_BRIDGE -> R.string.kind_wv_js_bridge
    WebViewKind.DEBUG -> R.string.kind_wv_debug
    WebViewKind.JS_CRYPTO -> R.string.kind_wv_js_crypto
}

/** WebView 操作标签资源。 */
@StringRes
fun webviewOperationLabelRes(operation: WebViewOperation): Int = when (operation) {
    WebViewOperation.LOAD_URL -> R.string.op_wv_load_url
    WebViewOperation.LOAD_URL_WITH_HEADERS -> R.string.op_wv_load_url
    WebViewOperation.LOAD_DATA -> R.string.op_wv_load_data
    WebViewOperation.LOAD_DATA_WITH_BASE_URL -> R.string.op_wv_load_data
    WebViewOperation.EVALUATE_JS -> R.string.op_wv_evaluate_js
    WebViewOperation.EVALUATE_JS_LEGACY -> R.string.op_wv_evaluate_js
    WebViewOperation.ADD_JS_INTERFACE -> R.string.op_wv_add_js_interface
    WebViewOperation.SET_DEBUGGING -> R.string.op_wv_set_debugging
    WebViewOperation.PAGE_FINISHED -> R.string.op_wv_page_finished
    WebViewOperation.JS_ENCRYPT -> R.string.op_wv_js_encrypt
    WebViewOperation.JS_DECRYPT -> R.string.op_wv_js_decrypt
    WebViewOperation.JS_DIGEST -> R.string.op_wv_js_digest
    WebViewOperation.JS_SIGN -> R.string.op_wv_js_sign
    WebViewOperation.JS_VERIFY -> R.string.op_wv_js_verify
    WebViewOperation.JS_IMPORT_KEY -> R.string.op_wv_js_import_key
    WebViewOperation.JS_GENERATE_KEY -> R.string.op_wv_js_generate_key
}

/** WebView 记录摘要文本(列表行长按复制;V43 T4:桥名行走 meta_bridge 资源)。 */
fun webviewSummary(context: Context, record: WebViewRow): String {
    val time = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        Locale.getDefault(),
    ).format(Date(record.recordTimestamp))
    return buildString {
        append(context.getString(webviewKindLabelRes(record.kind)))
        append('\n')
        append(context.getString(webviewOperationLabelRes(record.operation)))
        record.url?.let { append('\n').append(it) }
        record.bridgeName?.let {
            append('\n').append(context.getString(R.string.meta_bridge)).append(' ').append(it)
        }
        append('\n').append(time)
        append('\n').append(record.packageName)
    }
}

// ---- V30 网络环境监听 ----

@Composable
private fun networkPresentation(record: NetworkRow): RowPresentation {
    val accent = networkKindColor(record.kind)
    val kindText = stringResource(networkKindLabelRes(record.kind))
    val operationText = stringResource(networkOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val vpnDetected = stringResource(R.string.record_action_detected)
    val vpnNone = stringResource(R.string.record_value_no)
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        record.url?.takeIf { it.isNotBlank() }?.let {
            add(RowField(labelRes = R.string.meta_url, value = it, mono = true, middleEllipsis = true))
        }
        record.method?.takeIf { it.isNotBlank() }?.let {
            add(RowField(labelRes = R.string.meta_method, value = it))
        }
        record.statusCode?.let {
            add(RowField(labelRes = R.string.meta_status_code, value = it.toString()))
        }
        record.proxyHost?.takeIf { it.isNotBlank() }?.let {
            add(RowField(labelRes = R.string.meta_proxy, value = it, mono = true, middleEllipsis = true))
        }
        record.vpnActive?.let { active ->
            add(
                RowField(
                    labelRes = R.string.meta_vpn,
                    value = if (active) vpnDetected else vpnNone,
                ),
            )
        }
    }
    return RowPresentation(
        icon = KindIcons.network(record.kind),
        accent = accent,
        headline = headline,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

@Composable
private fun deviceEnvPresentation(record: DeviceEnvRow): RowPresentation {
    val accent = deviceEnvKindColor(record.kind)
    val kindText = stringResource(deviceEnvKindLabelRes(record.kind))
    val operationText = stringResource(deviceEnvOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        interventionActionLabel(record.interventionAction)?.let {
            add(RowField(value = it, valueColor = accent))
        }
    }
    return RowPresentation(
        icon = KindIcons.deviceEnv(record.kind),
        accent = accent,
        headline = headline,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

@Composable
private fun uiInteractionPresentation(record: UiInteractionRow): RowPresentation {
    val accent = uiInteractionKindColor(record.kind)
    val kindText = stringResource(uiInteractionKindLabelRes(record.kind))
    val operationText = stringResource(uiInteractionOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    // 主体值三选一与 headline 去重(V3.5 原则):headline 已承担的不再上屏
    val subject =
        remember(record.dialogTitle, record.dialogMessage, record.widgetText, record.activityName, headline) {
            sequenceOf(record.dialogTitle, record.dialogMessage, record.widgetText, record.activityName)
                .mapNotNull { it?.takeIf { v -> v.isNotBlank() } }
                .firstOrNull { it != headline }
        }
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        subject?.let {
            add(RowField(value = it.take(80), middleEllipsis = true))
        }
    }
    return RowPresentation(
        icon = KindIcons.uiInteraction(record.kind),
        accent = accent,
        headline = headline,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

@Composable
private fun systemPresentation(record: SystemRow): RowPresentation {
    val accent = systemKindColor(record.kind)
    val kindText = stringResource(systemKindLabelRes(record.kind))
    val operationText = stringResource(systemOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val hashSnippet = record.signatureHash?.takeIf { it.isNotBlank() }?.take(16)?.let { "$it…" }
    // V43 T4(V3.5 去重):headline 已承担的 tag/hash 不再重复上屏;
    // logcat level/message 恢复呈现(旧行模型零展示)
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        record.logcatLevel?.takeIf { it.isNotBlank() }?.let {
            add(RowField(value = it))
        }
        record.logcatMessage?.takeIf { it.isNotBlank() }?.let {
            add(RowField(value = it, middleEllipsis = true))
        }
        hashSnippet?.takeIf { it != headline }?.let {
            add(RowField(value = it, mono = true))
        }
    }
    return RowPresentation(
        icon = KindIcons.system(record.kind),
        accent = accent,
        headline = headline,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

@Composable
private fun processPresentation(record: ProcessRow): RowPresentation {
    val accent = processKindColor(record.kind)
    val kindText = stringResource(processKindLabelRes(record.kind))
    val operationText = stringResource(processOperationLabelRes(record.operation))
    // V43 T4/D-3:headline 身份 = 操作名(资源);退出码/pid 拆独立参数行
    // (旧 `"exit=X pid=Y"` 双参数一行 + 英文前缀,V3.3 违规清除)
    val fields = buildList {
        add(RowField(value = kindText, valueColor = accent))
        add(RowField(labelRes = R.string.meta_operation, value = operationText))
        when (record.kind) {
            ProcessKind.EXIT_INTERCEPT -> {
                record.exitCode?.let { add(RowField(labelRes = R.string.meta_exit_code, value = it.toString())) }
                record.processPid?.let { add(RowField(labelRes = R.string.meta_pid, value = it.toString())) }
            }

            ProcessKind.CRASH_PREVENT -> record.crashType?.takeIf { it.isNotBlank() }
                ?.let { add(RowField(value = it.substringAfterLast('.'), mono = true, middleEllipsis = true)) }

            ProcessKind.SCREEN_RECORD -> Unit
        }
        interventionActionLabel(record.interventionAction)?.let {
            add(RowField(value = it, valueColor = accent))
        }
    }
    return RowPresentation(
        icon = KindIcons.process(record.kind),
        accent = accent,
        headline = operationText,
        fields = fields,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

/** 网络环境监听类别显示名资源。 */
@StringRes
fun networkKindLabelRes(kind: NetworkKind): Int = when (kind) {
    NetworkKind.SSL_CONTEXT -> R.string.kind_net_ssl_context
    NetworkKind.SSL_TRUST_MANAGER -> R.string.kind_net_ssl_trust_manager
    NetworkKind.SSL_OKHTTP_PINNER -> R.string.kind_net_ssl_okhttp_pinner
    NetworkKind.SSL_OKHTTP_VERIFIER -> R.string.kind_net_ssl_okhttp_verifier
    NetworkKind.SSL_WEBVIEW -> R.string.kind_net_ssl_webview
    NetworkKind.SSL_NETWORK_CONFIG -> R.string.kind_net_ssl_network_config
    NetworkKind.PROXY_SYSTEM_PROP -> R.string.kind_net_proxy_system_prop
    NetworkKind.PROXY_CONNECTIVITY -> R.string.kind_net_proxy_connectivity
    NetworkKind.PROXY_SETTINGS -> R.string.kind_net_proxy_settings
    NetworkKind.PROXY_OKHTTP -> R.string.kind_net_proxy_okhttp
    NetworkKind.VPN_CAPABILITIES -> R.string.kind_net_vpn_capabilities
    NetworkKind.VPN_INTERFACE -> R.string.kind_net_vpn_interface
    NetworkKind.VPN_NETWORK_INFO -> R.string.kind_net_vpn_network_info
    NetworkKind.VPN_LINK_PROPERTIES -> R.string.kind_net_vpn_link_properties
    NetworkKind.REQUEST -> R.string.kind_net_request
}

/** 网络环境监听操作标签资源。 */
@StringRes
fun networkOperationLabelRes(operation: NetworkOperation): Int = when (operation) {
    NetworkOperation.SSL_INIT -> R.string.op_net_ssl_init
    NetworkOperation.SSL_CHECK -> R.string.op_net_ssl_check
    NetworkOperation.SSL_PIN -> R.string.op_net_ssl_pin
    NetworkOperation.SSL_VERIFY -> R.string.op_net_ssl_verify
    NetworkOperation.SSL_WEBVIEW_ERROR -> R.string.op_net_ssl_webview_error
    NetworkOperation.SSL_CONFIG_CHECK -> R.string.op_net_ssl_config_check
    NetworkOperation.PROP_GET -> R.string.op_net_prop_get
    NetworkOperation.PROP_DEFAULT -> R.string.op_net_prop_default
    NetworkOperation.PROP_LINK -> R.string.op_net_prop_link
    NetworkOperation.PROP_GLOBAL -> R.string.op_net_prop_global
    NetworkOperation.PROP_OKHTTP -> R.string.op_net_prop_okhttp
    NetworkOperation.VPN_HAS_TRANSPORT -> R.string.op_net_vpn_has_transport
    NetworkOperation.VPN_GET_CAPS -> R.string.op_net_vpn_get_caps
    NetworkOperation.VPN_TRANSPORT_INFO -> R.string.op_net_vpn_transport_info
    NetworkOperation.VPN_IS_VIRTUAL -> R.string.op_net_vpn_is_virtual
    NetworkOperation.VPN_IFACE_NAME -> R.string.op_net_vpn_iface_name
    NetworkOperation.VPN_IFACE_BY_NAME -> R.string.op_net_vpn_iface_by_name
    NetworkOperation.VPN_IFACE_ENUM -> R.string.op_net_vpn_iface_enum
    NetworkOperation.VPN_TYPE -> R.string.op_net_vpn_type
    NetworkOperation.VPN_TYPE_NAME -> R.string.op_net_vpn_type_name
    NetworkOperation.VPN_IFACE_PROP -> R.string.op_net_vpn_iface_prop
    NetworkOperation.VPN_ROUTES -> R.string.op_net_vpn_routes
    NetworkOperation.REQUEST_START -> R.string.op_net_request_start
    NetworkOperation.REQUEST_END -> R.string.op_net_request_end
}

/** 网络记录摘要文本(列表行长按复制;V43 T4:状态/代理/VPN 行走资源)。 */
fun networkSummary(context: Context, record: NetworkRow): String {
    val time = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        Locale.getDefault(),
    ).format(Date(record.recordTimestamp))
    return buildString {
        append(context.getString(networkKindLabelRes(record.kind)))
        append('\n')
        append(context.getString(networkOperationLabelRes(record.operation)))
        record.url?.let { append('\n').append(it) }
        record.method?.let { append('\n').append(it) }
        record.statusCode?.let {
            append('\n').append(context.getString(R.string.meta_status_code)).append(' ').append(it)
        }
        record.proxyHost?.let {
            append('\n').append(context.getString(R.string.meta_proxy)).append(' ').append(it)
        }
        record.vpnActive?.let { active ->
            append('\n').append(context.getString(R.string.meta_vpn)).append(' ')
            append(
                context.getString(
                    if (active) R.string.record_action_detected else R.string.record_value_no,
                ),
            )
        }
        append('\n').append(time)
        append('\n').append(record.packageName)
    }
}

/** 设备环境监听类别显示名资源。 */
@StringRes
fun deviceEnvKindLabelRes(kind: DeviceEnvKind): Int = when (kind) {
    DeviceEnvKind.ROOT_FILE -> R.string.kind_device_env_ROOT_FILE
    DeviceEnvKind.ROOT_EXEC -> R.string.kind_device_env_ROOT_EXEC
    DeviceEnvKind.ROOT_PACKAGE_MANAGER -> R.string.kind_device_env_ROOT_PACKAGE_MANAGER
    DeviceEnvKind.ROOT_PROPERTY -> R.string.kind_device_env_ROOT_PROPERTY
    DeviceEnvKind.XPOSED_CLASS -> R.string.kind_device_env_XPOSED_CLASS
    DeviceEnvKind.XPOSED_STACK -> R.string.kind_device_env_XPOSED_STACK
    DeviceEnvKind.XPOSED_MAPS -> R.string.kind_device_env_XPOSED_MAPS
    DeviceEnvKind.EMULATOR_BUILD -> R.string.kind_device_env_EMULATOR_BUILD
    DeviceEnvKind.EMULATOR_FILE -> R.string.kind_device_env_EMULATOR_FILE
    DeviceEnvKind.EMULATOR_SENSOR -> R.string.kind_device_env_EMULATOR_SENSOR
    DeviceEnvKind.EMULATOR_NETWORK -> R.string.kind_device_env_EMULATOR_NETWORK
    DeviceEnvKind.EMULATOR_TELEPHONY -> R.string.kind_device_env_EMULATOR_TELEPHONY
    DeviceEnvKind.EMULATOR_CPU -> R.string.kind_device_env_EMULATOR_CPU
    DeviceEnvKind.EMULATOR_BATTERY -> R.string.kind_device_env_EMULATOR_BATTERY
    DeviceEnvKind.SYSTEM_PROPERTIES -> R.string.kind_device_env_SYSTEM_PROPERTIES
    DeviceEnvKind.PROC_STATUS -> R.string.kind_device_env_PROC_STATUS
    DeviceEnvKind.PROC_MOUNT_INFO -> R.string.kind_device_env_PROC_MOUNT_INFO
    DeviceEnvKind.PROC_CGROUP -> R.string.kind_device_env_PROC_CGROUP
    DeviceEnvKind.PROC_NET -> R.string.kind_device_env_PROC_NET
    DeviceEnvKind.DETECT_ROOT -> R.string.kind_device_env_DETECT_ROOT
    DeviceEnvKind.DETECT_EMULATOR -> R.string.kind_device_env_DETECT_EMULATOR
    DeviceEnvKind.PROFILE_BUILD_IDENTITY -> R.string.kind_device_env_PROFILE_BUILD_IDENTITY
    DeviceEnvKind.PROFILE_BUILD_VERSION -> R.string.kind_device_env_PROFILE_BUILD_VERSION
    DeviceEnvKind.PROFILE_DENSITY -> R.string.kind_device_env_PROFILE_DENSITY
    DeviceEnvKind.PROFILE_CPU -> R.string.kind_device_env_PROFILE_CPU
    DeviceEnvKind.PROFILE_BATTERY -> R.string.kind_device_env_PROFILE_BATTERY
    DeviceEnvKind.PROFILE_ANDROID_ID -> R.string.kind_device_env_PROFILE_ANDROID_ID
    DeviceEnvKind.PROFILE_TELEPHONY_ID -> R.string.kind_device_env_PROFILE_TELEPHONY_ID
    DeviceEnvKind.PROFILE_SIM -> R.string.kind_device_env_PROFILE_SIM
}

/** 设备环境监听操作标签资源。 */
@StringRes
fun deviceEnvOperationLabelRes(operation: DeviceEnvOperation): Int = when (operation) {
    DeviceEnvOperation.FILE_EXISTS -> R.string.op_device_env_file_exists
    DeviceEnvOperation.FILE_LENGTH -> R.string.op_device_env_file_length
    DeviceEnvOperation.FILE_READABLE -> R.string.op_device_env_file_readable
    DeviceEnvOperation.EXEC_COMMAND -> R.string.op_device_env_exec_command
    DeviceEnvOperation.PROCESS_BUILDER -> R.string.op_device_env_process_builder
    DeviceEnvOperation.GET_INSTALLED_PACKAGES -> R.string.op_device_env_get_installed_packages
    DeviceEnvOperation.GET_INSTALLED_APPLICATIONS -> R.string.op_device_env_get_installed_applications
    DeviceEnvOperation.QUERY_INTENT_ACTIVITIES -> R.string.op_device_env_query_intent_activities
    DeviceEnvOperation.GET_PROPERTY -> R.string.op_device_env_get_property
    DeviceEnvOperation.BUILD_TAGS -> R.string.op_device_env_build_tags
    DeviceEnvOperation.LOAD_CLASS -> R.string.op_device_env_load_class
    DeviceEnvOperation.FIND_CLASS -> R.string.op_device_env_find_class
    DeviceEnvOperation.GET_STACK_TRACE -> R.string.op_device_env_get_stack_trace
    DeviceEnvOperation.THREAD_STACK -> R.string.op_device_env_thread_stack
    DeviceEnvOperation.READ_MAPS -> R.string.op_device_env_read_maps
    DeviceEnvOperation.BUILD_FINGERPRINT -> R.string.op_device_env_build_fingerprint
    DeviceEnvOperation.BUILD_MODEL -> R.string.op_device_env_build_model
    DeviceEnvOperation.BUILD_PRODUCT -> R.string.op_device_env_build_product
    DeviceEnvOperation.BUILD_MANUFACTURER -> R.string.op_device_env_build_manufacturer
    DeviceEnvOperation.DEV_FILE_EXISTS -> R.string.op_device_env_dev_file_exists
    DeviceEnvOperation.SYS_FILE_EXISTS -> R.string.op_device_env_sys_file_exists
    DeviceEnvOperation.LIB_FILE_EXISTS -> R.string.op_device_env_lib_file_exists
    DeviceEnvOperation.GET_DEFAULT_SENSOR -> R.string.op_device_env_get_default_sensor
    DeviceEnvOperation.SENSOR_LIST -> R.string.op_device_env_sensor_list
    DeviceEnvOperation.GET_MAC_ADDRESS -> R.string.op_device_env_get_mac_address
    DeviceEnvOperation.ANDROID_ID -> R.string.op_device_env_android_id
    DeviceEnvOperation.PHONE_DEVICE_ID -> R.string.op_device_env_phone_device_id
    DeviceEnvOperation.PHONE_IMEI -> R.string.op_device_env_phone_imei
    DeviceEnvOperation.PHONE_SUBSCRIBER_ID -> R.string.op_device_env_phone_subscriber_id
    DeviceEnvOperation.PHONE_SIM_SERIAL -> R.string.op_device_env_phone_sim_serial
    DeviceEnvOperation.PHONE_LINE1_NUMBER -> R.string.op_device_env_phone_line1_number
    DeviceEnvOperation.CPU_AVAILABLE_PROCESSORS -> R.string.op_device_env_cpu_available_processors
    DeviceEnvOperation.BATTERY_INT_PROPERTY -> R.string.op_device_env_battery_int_property
    DeviceEnvOperation.BATTERY_IS_CHARGING -> R.string.op_device_env_battery_is_charging
    DeviceEnvOperation.BATTERY_CHANGED -> R.string.op_device_env_battery_changed
    DeviceEnvOperation.BUILD_DEVICE -> R.string.op_device_env_build_device
    DeviceEnvOperation.BUILD_BOARD -> R.string.op_device_env_build_board
    DeviceEnvOperation.BUILD_HARDWARE -> R.string.op_device_env_build_hardware
    DeviceEnvOperation.BUILD_BRAND -> R.string.op_device_env_build_brand
    DeviceEnvOperation.BUILD_HOST -> R.string.op_device_env_build_host
    DeviceEnvOperation.BUILD_USER -> R.string.op_device_env_build_user
    DeviceEnvOperation.BUILD_DISPLAY -> R.string.op_device_env_build_display
    DeviceEnvOperation.BUILD_SERIAL -> R.string.op_device_env_build_serial
    DeviceEnvOperation.BUILD_TYPE -> R.string.op_device_env_build_type
    DeviceEnvOperation.ROOT_CHECK_FILE -> R.string.op_device_env_root_check_file
    DeviceEnvOperation.ROOT_CHECK_EXEC -> R.string.op_device_env_root_check_exec
    DeviceEnvOperation.EMULATOR_CHECK_BUILD -> R.string.op_device_env_emulator_check_build
    DeviceEnvOperation.EMULATOR_CHECK_FILE -> R.string.op_device_env_emulator_check_file
    DeviceEnvOperation.EMULATOR_CHECK_PROP -> R.string.op_device_env_emulator_check_prop
    DeviceEnvOperation.BUILD_IDENTITY_WRITE -> R.string.op_device_env_build_identity_write
    DeviceEnvOperation.BUILD_VERSION_WRITE -> R.string.op_device_env_build_version_write
    DeviceEnvOperation.DENSITY_WRITE -> R.string.op_device_env_density_write
    DeviceEnvOperation.CPU_WRITE -> R.string.op_device_env_cpu_write
    DeviceEnvOperation.BATTERY_WRITE -> R.string.op_device_env_battery_write
    DeviceEnvOperation.ANDROID_ID_WRITE -> R.string.op_device_env_android_id_write
    DeviceEnvOperation.TELEPHONY_ID_WRITE -> R.string.op_device_env_telephony_id_write
    DeviceEnvOperation.SIM_WRITE -> R.string.op_device_env_sim_write
    DeviceEnvOperation.SYS_PROP_GET -> R.string.op_device_env_sys_prop_get
    DeviceEnvOperation.PROC_STATUS_READ -> R.string.op_device_env_proc_status_read
    DeviceEnvOperation.PROC_MOUNT_READ -> R.string.op_device_env_proc_mount_read
    DeviceEnvOperation.PROC_CGROUP_READ -> R.string.op_device_env_proc_cgroup_read
    DeviceEnvOperation.PROC_NET_READ -> R.string.op_device_env_proc_net_read
    DeviceEnvOperation.SETTINGS_GLOBAL_GET -> R.string.op_device_env_settings_global_get
    DeviceEnvOperation.SETTINGS_SECURE_GET -> R.string.op_device_env_settings_secure_get
}

/** 设备环境记录摘要文本(列表行长按复制,V31)。 */
fun deviceEnvSummary(context: Context, record: DeviceEnvRow): String {
    val time = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        Locale.getDefault(),
    ).format(Date(record.recordTimestamp))
    return buildString {
        append(context.getString(deviceEnvKindLabelRes(record.kind)))
        append('\n')
        append(context.getString(deviceEnvOperationLabelRes(record.operation)))
        record.targetPath?.let { v -> append('\n').append(v) }
        record.targetClass?.let { v -> append('\n').append(v) }
        record.targetProperty?.let { v -> append('\n').append(v) }
        append('\n').append(time)
        append('\n').append(record.packageName)
    }
}

/** UI 交互监听类别标签。 */
fun uiInteractionKindLabelRes(kind: UiInteractionKind): Int = when (kind) {
    UiInteractionKind.DIALOG_SHOW -> R.string.kind_ui_DIALOG_SHOW
    UiInteractionKind.DIALOG_KEYWORD -> R.string.kind_ui_DIALOG_KEYWORD
    UiInteractionKind.WIDGET_TEXT -> R.string.kind_ui_WIDGET_TEXT
    UiInteractionKind.ACTIVITY_START -> R.string.kind_ui_ACTIVITY_START
    UiInteractionKind.ACTIVITY_RESUME -> R.string.kind_ui_ACTIVITY_RESUME
}

/** UI 交互监听操作标签。 */
fun uiInteractionOperationLabelRes(op: UiInteractionOperation): Int = when (op) {
    UiInteractionOperation.DIALOG_SHOW -> R.string.op_ui_show
    UiInteractionOperation.DIALOG_DISMISS -> R.string.op_ui_back_dismiss
    UiInteractionOperation.DIALOG_CANCEL -> R.string.op_ui_back_dismiss
    UiInteractionOperation.DIALOG_INTERCEPTED -> R.string.op_ui_intercept
    UiInteractionOperation.DIALOG_PASSED -> R.string.op_ui_show
    UiInteractionOperation.WIDGET_SET_TEXT -> R.string.op_ui_assign
    UiInteractionOperation.WIDGET_SET_TEXT_RES -> R.string.op_ui_assign
    UiInteractionOperation.ACTIVITY_START_INTENT -> R.string.op_ui_start
    UiInteractionOperation.ACTIVITY_START_OPTIONS -> R.string.op_ui_start
    UiInteractionOperation.ACTIVITY_FINISH -> R.string.op_ui_back_dismiss
    UiInteractionOperation.ACTIVITY_ON_CREATE -> R.string.op_ui_create
    UiInteractionOperation.ACTIVITY_ON_RESUME -> R.string.op_ui_create
    UiInteractionOperation.ACTIVITY_ON_PAUSE -> R.string.op_ui_back_dismiss
    UiInteractionOperation.ACTIVITY_ON_STOP -> R.string.op_ui_back_dismiss
    UiInteractionOperation.ACTIVITY_ON_DESTROY -> R.string.op_ui_back_dismiss
}

/** UI 交互记录摘要文本。 */
fun uiInteractionSummary(context: Context, record: UiInteractionRow): String {
    val time = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        Locale.getDefault(),
    ).format(Date(record.recordTimestamp))
    return buildString {
        append(context.getString(uiInteractionKindLabelRes(record.kind)))
        append('\n')
        append(context.getString(uiInteractionOperationLabelRes(record.operation)))
        record.dialogTitle?.let { v -> append('\n').append(v) }
        record.dialogMessage?.let { v -> append('\n').append(v.take(60)) }
        record.widgetText?.let { v -> append('\n').append(v.take(60)) }
        record.activityName?.let { v -> append('\n').append(v.substringAfterLast('.')) }
        append('\n').append(time)
        append('\n').append(record.packageName)
    }
}

/** 系统信息监听类别标签。 */
fun systemKindLabelRes(kind: SystemKind): Int = when (kind) {
    SystemKind.LOGCAT -> R.string.kind_system_LOGCAT
    SystemKind.APP_INFO -> R.string.kind_system_APP_INFO
    SystemKind.SIGNATURE -> R.string.kind_system_SIGNATURE
}

/** 系统信息监听操作标签。 */
fun systemOperationLabelRes(op: SystemOperation): Int = when (op) {
    SystemOperation.LOG_VERBOSE -> R.string.op_system_capture
    SystemOperation.LOG_DEBUG -> R.string.op_system_capture
    SystemOperation.LOG_INFO -> R.string.op_system_capture
    SystemOperation.LOG_WARN -> R.string.op_system_capture
    SystemOperation.LOG_ERROR -> R.string.op_system_capture
    SystemOperation.LOG_FATAL -> R.string.op_system_capture
    SystemOperation.APP_VERSION -> R.string.op_system_get_info
    SystemOperation.APP_PERMISSIONS -> R.string.op_system_get_info
    SystemOperation.SIGNATURE_READ -> R.string.op_system_read
    SystemOperation.SIGNATURE_READ_V2 -> R.string.op_system_read
}

/** 系统信息记录摘要文本。 */
fun systemSummary(context: Context, record: SystemRow): String {
    val time = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        Locale.getDefault(),
    ).format(Date(record.recordTimestamp))
    return buildString {
        append(context.getString(systemKindLabelRes(record.kind)))
        append('\n')
        append(context.getString(systemOperationLabelRes(record.operation)))
        record.logcatTag?.let { v -> append('\n').append("[$v]") }
        record.logcatLevel?.let { v -> append(' ').append(v) }
        record.logcatMessage?.let { v -> append('\n').append(v.take(60)) }
        record.signatureHash?.let { v -> append('\n').append(v.take(16)).append("…") }
        append('\n').append(time)
        append('\n').append(record.packageName)
    }
}

/** 进程控制监听类别标签。 */
fun processKindLabelRes(kind: ProcessKind): Int = when (kind) {
    ProcessKind.EXIT_INTERCEPT -> R.string.kind_process_EXIT_INTERCEPT
    ProcessKind.CRASH_PREVENT -> R.string.kind_process_CRASH_PREVENT
    ProcessKind.SCREEN_RECORD -> R.string.kind_process_SCREEN_RECORD
}

/** 进程控制监听操作标签。 */
fun processOperationLabelRes(op: ProcessOperation): Int = when (op) {
    ProcessOperation.SYSTEM_EXIT -> R.string.op_process_system_exit
    ProcessOperation.KILL_PROCESS -> R.string.op_process_kill_process
    ProcessOperation.RUNTIME_EXIT -> R.string.op_process_system_exit
    ProcessOperation.UNCAUGHT_EXCEPTION -> R.string.op_process_catch
    ProcessOperation.FLAG_SECURE -> R.string.op_process_block
    ProcessOperation.ADD_FLAGS -> R.string.op_process_block
    ProcessOperation.SURFACE_LOCK -> R.string.op_process_block
    ProcessOperation.SCREENSHOT -> R.string.op_process_block
    ProcessOperation.MEDIA_PROJECTION -> R.string.op_process_block
}

/** 进程控制记录摘要文本(V33;V43 T4:exit/pid/状态行全走资源)。 */
fun processControlSummary(context: Context, record: ProcessRow): String {
    val time = SimpleDateFormat(
        if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
        Locale.getDefault(),
    ).format(Date(record.recordTimestamp))
    return buildString {
        append(context.getString(processKindLabelRes(record.kind)))
        append('\n')
        append(context.getString(processOperationLabelRes(record.operation)))
        record.exitCode?.let { v ->
            append('\n').append(context.getString(R.string.meta_exit_code)).append(' ').append(v)
        }
        record.processPid?.let { v ->
            append('\n').append(context.getString(R.string.meta_pid)).append(' ').append(v)
        }
        record.crashType?.let { v -> append('\n').append(v.substringAfterLast('.')) }
        interventionActionLabel(context, record.interventionAction)?.let { v ->
            append('\n').append(v)
        }
        record.crashBlocked?.let { v ->
            append('\n')
                .append(context.getString(if (v == 1) R.string.record_action_blocked else R.string.record_action_detected))
        }
        record.screenRecordBlocked?.let { v ->
            append('\n').append(context.getString(R.string.kind_process_SCREEN_RECORD)).append(' ')
            append(context.getString(if (v == 1) R.string.record_action_blocked else R.string.record_action_detected))
        }
        append('\n').append(record.threadName)
        append('\n').append(time)
        append('\n').append(record.packageName)
    }
}
