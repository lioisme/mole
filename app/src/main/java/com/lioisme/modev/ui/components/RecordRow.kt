package com.lioisme.modev.ui.components

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.WebViewOperation
import com.lioisme.modev.data.mapping.BehaviorRow
import com.lioisme.modev.data.mapping.CryptoRow
import com.lioisme.modev.data.mapping.PrefsRow
import com.lioisme.modev.data.mapping.RowModel
import com.lioisme.modev.data.mapping.SqlRow
import com.lioisme.modev.data.mapping.WebViewRow
import com.lioisme.modev.ui.theme.behaviorKindColor
import com.lioisme.modev.ui.theme.kindColor
import com.lioisme.modev.ui.theme.prefsKindColor
import com.lioisme.modev.ui.theme.sqlKindColor
import com.lioisme.modev.ui.theme.webviewKindColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录行(V27 M1-T3 重构,取代 V13 三级文本流):
 * 单一脚手架 [RecordListCard] 承载各族记录(Crypto/Sql,后续 Behavior),信息架构四级:
 * - leading 类别徽章(36dp 圆角方,类别色@13% 底 + 类别色图标 20dp,CIPHER 按方向换锁形);
 * - headline 算法名 / SQL 首行(bodyLarge Medium mono,超长尾省略);
 * - 元行 labelMedium:类别词元(类别色)+ 操作名(+库名/行数后缀);
 * - 预览行 bodySmall mono(最有价值数据段 / SQL 预览);
 * - 尾栏:方向微徽章(labelSmall 紧凑胶囊,替代 V19 AssistChip);
 * - 行容器 surfaceContainerLow 圆角 16dp,最小高 68dp;
 * - 单击进详情、长按复制摘要([CryptoRecord.copySummary],回调由宿主接线)。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(
    record: RowModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val presentation =
            when (record) {
                is CryptoRow -> cryptoPresentation(record)
                is SqlRow -> sqlPresentation(record)
                is BehaviorRow -> behaviorPresentation(record)
                is PrefsRow -> prefsPresentation(record)
                is WebViewRow -> webviewPresentation(record)
            }
        Row(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        },
                    )
                    .defaultMinSize(minHeight = MIN_ROW_HEIGHT)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SPACING.dp),
        ) {
            RecordListCard(presentation = presentation)
        }
    }
}

/** 统一行展示规格(presentation 单一数据出口,三族同构)。 */
private data class RowPresentation(
    val icon: ImageVector,
    val accent: Color,
    val headline: String,
    val meta: AnnotatedString,
    val preview: String?,
    val badges: List<RowBadge>,
    /** 尾栏秒级时间(V27 契约保留:与组头同源口径,由行装配层格式化)。 */
    val timestamp: Long,
)

/** 微徽章规格(text 展示文案;accent 非空 = 语义色呈现,空 = 中性 surfaceVariant)。 */
private data class RowBadge(
    val text: String,
    val accent: Color? = null,
)

/** 统一行卡片骨架(RowScope 内调用,weight 承载主体列)。 */
@Composable
private fun RowScope.RecordListCard(presentation: RowPresentation) {
    LeadingBadge(icon = presentation.icon, tint = presentation.accent)
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = presentation.headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = presentation.meta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        presentation.preview?.let { preview ->
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    // 尾栏恒渲染(时间必在;徽章可选)——V20「时间恒在行内右栏」契约。
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TimeText(timestamp = presentation.timestamp)
        presentation.badges.forEach { badge -> MicroBadge(badge) }
    }
}

/**
 * 尾栏秒级时间(labelSmall onSurfaceVariant;12/24h 时制尊重系统设置,
 * 与时间线组头 [com.lioisme.modev.ui.common.TimelineGroup] 同口径)。
 * V27 回归修复:重构脚手架时误删,V20 契约「时间恒在行内右栏」恢复。
 */
@Composable
private fun TimeText(timestamp: Long) {
    val context = LocalContext.current
    val format =
        remember(context) {
            SimpleDateFormat(
                if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a",
                Locale.getDefault(),
            )
        }
    Text(
        text = remember(timestamp, format) { format.format(Date(timestamp)) },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** leading 类别徽章(36dp 圆角方,accent@13% 容器 + accent 图标;纯装饰语义)。 */
@Composable
private fun LeadingBadge(
    icon: ImageVector,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(BADGE_SIZE.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = BADGE_CONTAINER_ALPHA)),
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

/** 尾栏微徽章(方向/状态;紧凑胶囊)。 */
@Composable
private fun MicroBadge(badge: RowBadge) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color =
            badge.accent?.copy(alpha = BADGE_CONTAINER_ALPHA)
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

// ---------------------------------------------------------------------------
// 族形态:每族产出统一 [RowPresentation](组合期构建,remember 化防滚动重组)。
// ---------------------------------------------------------------------------

/** 算法形态。 */
@Composable
private fun cryptoPresentation(record: CryptoRow): RowPresentation {
    val r = record.record
    val accent = kindColor(r.kind)
    val kindText = stringResource(kindLabelRes(r.kind))
    val operationText = stringResource(operationLabelRes(r.operation))
    val meta =
        remember(r.kind, r.operation, kindText, operationText, accent) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent)) { append(kindText) }
                append(" · ")
                append(operationText)
            }
        }
    return RowPresentation(
        icon = KindIcons.crypto(r.kind, r.direction),
        accent = accent,
        headline = r.displayAlgorithm(),
        meta = meta,
        preview = r.valuableDataSnippet(),
        badges = r.direction?.let { dir -> listOf(RowBadge(directionLabel(dir))) } ?: emptyList(),
        timestamp = r.timestamp,
    )
}

/** 数据库形态。 */
@Composable
private fun sqlPresentation(record: SqlRow): RowPresentation {
    val accent = sqlKindColor(record.kind)
    val rowCountText =
        record.rowCount?.let { pluralStringResource(R.plurals.record_data_rows_short, it, it) }
    val affectedText =
        record.affectedCount?.let { pluralStringResource(R.plurals.record_data_affected_short, it, it) }
    val suffix =
        when {
            rowCountText != null && affectedText != null -> " ($rowCountText, $affectedText)"
            rowCountText != null -> " ($rowCountText)"
            affectedText != null -> " ($affectedText)"
            else -> ""
        }
    val dbName = record.databaseName.orEmpty().takeIf { it.isNotBlank() }
    val kindText = stringResource(sqlKindLabelRes(record.kind))
    val operationText = stringResource(sqlOperationLabelRes(record.operation))
    val meta =
        remember(record.kind, record.operation, dbName, kindText, operationText, suffix, accent) {
            buildAnnotatedString {
                dbName?.let {
                    append(it)
                    append(" · ")
                }
                withStyle(SpanStyle(color = accent)) { append(kindText) }
                append(" · ")
                append(operationText)
                append(suffix)
            }
        }
    return RowPresentation(
        icon = KindIcons.sql(record.kind),
        accent = accent,
        headline = record.sqlHeadlineText(),
        meta = meta,
        preview = record.sqlPreview?.trim()?.takeIf { it.isNotEmpty() },
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
 * 行为形态(V27):headline = 文件 basename / 命令首段(空回退操作名,恒非空);
 * 元行 = 类别词元 · 操作 · 目标路径中缀省略;预览行 = 完整路径/命令。
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
    val meta =
        remember(record.kind, record.operation, kindText, operationText, accent) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent)) { append(kindText) }
                append(" · ")
                append(operationText)
            }
        }
    return RowPresentation(
        icon = KindIcons.behavior(record.kind),
        accent = accent,
        headline = headline,
        meta = meta,
        preview = subject,
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

/**
 * 偏好形态(V28):headline = 配置键 / 文件 basename(空回退操作名,恒非空);
 * 元行 = 类别词元 · 操作;预览行 = 文件路径。
 */
@Composable
private fun prefsPresentation(record: PrefsRow): RowPresentation {
    val accent = prefsKindColor(record.kind)
    val kindText = stringResource(prefsKindLabelRes(record.kind))
    val operationText = stringResource(prefsOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val meta =
        remember(record.kind, record.operation, kindText, operationText, accent) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent)) { append(kindText) }
                append(" · ")
                append(operationText)
            }
        }
    return RowPresentation(
        icon = KindIcons.prefs(record.kind),
        accent = accent,
        headline = headline,
        meta = meta,
        preview = record.path?.takeIf { it.isNotBlank() },
        badges = emptyList(),
        timestamp = record.recordTimestamp,
    )
}

/**
 * WebView 形态(V29 一等公民):headline = 域名 / 操作名(空回退操作名,恒非空);
 * 元行 = 类别词元 · 操作;预览行 = URL/桥名。
 */
@Composable
private fun webviewPresentation(record: WebViewRow): RowPresentation {
    val accent = webviewKindColor(record.kind)
    val kindText = stringResource(webviewKindLabelRes(record.kind))
    val operationText = stringResource(webviewOperationLabelRes(record.operation))
    val headline = record.headlineText() ?: operationText
    val meta =
        remember(record.kind, record.operation, kindText, operationText, accent) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent)) { append(kindText) }
                append(" · ")
                append(operationText)
            }
        }
    val preview = remember(record.url, record.bridgeName) {
        record.bridgeName?.takeIf { it.isNotBlank() }
            ?: record.url?.takeIf { it.isNotBlank() }
    }
    return RowPresentation(
        icon = KindIcons.webview(record.kind),
        accent = accent,
        headline = headline,
        meta = meta,
        preview = preview,
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
 * 算法展示名单一事实源(V16 R1 空白治理):algorithm 为空串/空白时回退类别名(恒有),
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

/**
 * 最有价值数据段(单段展示不堆叠,优先级 密钥 > 别名 > 输出 > 输入):
 * 列表行只留「最重要的一段数据」(包名/线程为噪音,详情页承担全量)。
 */
@Composable
private fun CryptoRecord.valuableDataSnippet(): String? =
    valuableDataPair()?.let { (labelRes, value) -> stringResource(labelRes) + " " + value }

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
    CryptoKind.WEB_CRYPTO -> R.string.kind_webview
}

/** 操作标签资源(2026-08-16 V3 起走资源,消除硬编码英文;i18n 双语言)。 */
@StringRes
fun operationLabelRes(operation: CryptoOperation): Int = when (operation) {
    CryptoOperation.GET_INSTANCE -> R.string.op_get_instance
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

/** 行最小高度(V27 密度规范)。 */
private val MIN_ROW_HEIGHT: Dp = 68.dp

/** leading 徽章边长。 */
private const val BADGE_SIZE = 36

/** 徽章容器/微徽章淡底透明度。 */
private const val BADGE_CONTAINER_ALPHA = 0.13f

/** 行内元素间距。 */
private const val SPACING = 12

// ---------------------------------------------------------------------------
// WebView 标签资源(V29 一等公民)
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

/** WebView 记录摘要文本(列表行长按复制)。 */
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
        record.bridgeName?.let { append('\n').append("bridge: ").append(it) }
        append('\n').append(time)
        append('\n').append(record.packageName)
    }
}
