package com.lioisme.modev.ui.components

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.ui.theme.kindColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录行(2026-08-17 V8 卡片化;2026-08-17 V12 秒级时间 + 参数独立成行;
 * 2026-08-17 V13 去徽章 + 空白治理;2026-08-18 V14 R1 精修 M3 ListItem 层级):
 * 每行 = 圆角卡片(16dp, surfaceContainerLow 浮于 surface),行间留白 8dp:
 * - 三级信息架构(对齐 M3 ListItem:headline + supporting + 数据段)+ 尾部时间:
 *   行1 headline 算法(等宽 bodyLarge SemiBold,超长省略);
 *   行2 supporting 类别·操作组合(labelMedium 类别色,类别语义唯一承载点);
 *   行3(仅当有方向)方向胶囊(类别色 15% alpha 淡底小字);
 *   行4(仅当有数据)唯一最有价值数据段(等宽 labelMedium,优先级 key > alias > out > in);
 *   尾部时间(秒级精度,labelSmall onSurfaceVariant,尊重系统 12/24h 时制);
 * - V13 移除类别徽章(与操作行类别色重复承载)与固定最小高度 68dp——卡片
 *   内容自适应高度、顶对齐,字段空缺不留空白区域;
 * - 单击进详情;长按复制记录摘要([copySummary]),回调由宿主接线。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordRow(
    record: CryptoRecord,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val timeFormat = remember(context) {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    val dataSnippet = record.valuableDataSnippet()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            // 尾部时间与 headline 顶对齐(V14 R1 层级)。
            verticalAlignment = Alignment.Top,
        ) {
            // 主体:每参数独立成行(V13:无徽章,内容顶对齐、高度自适应)。
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.displayAlgorithm(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // 类别·操作组合行(V14 R1:合并单行承载,类别色语义不变)。
                Text(
                    text =
                        stringResource(kindLabelRes(record.kind)) +
                            " · " +
                            operationLabel(record.operation),
                    style = MaterialTheme.typography.labelMedium,
                    color = kindColor(record.kind),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                record.direction?.let { direction ->
                    Spacer(Modifier.height(4.dp))
                    DirectionChip(
                        text = directionLabel(direction),
                        kind = record.kind,
                    )
                }
                if (dataSnippet != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dataSnippet,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // 尾部时间(V14 R1:移出主体,独立于数据区右对齐)。
            Text(
                text = timeFormat.format(Date(record.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 方向胶囊:类别色淡底 + 类别色小字(方向是加密流关键信息)。 */
@Composable
private fun DirectionChip(text: String, kind: CryptoKind) {
    val color = kindColor(kind)
    Surface(
        shape = RoundedCornerShape(KIND_BADGE_CORNER),
        color = color.copy(alpha = KIND_BADGE_ALPHA),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
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

/** 方向胶囊淡底透明度(类别色 alpha,淡色底 + 类别色小字)。 */
private const val KIND_BADGE_ALPHA = 0.15f

/** 方向胶囊圆角(与 LIDE LevelBadge 同款 6dp,勿用 shapes.small=8dp)。 */
private val KIND_BADGE_CORNER = 6.dp

/**
 * 最有价值数据段(单段展示不堆叠,优先级 密钥 > 别名 > 输出 > 输入):
 * 取代旧版六段拼接摘要(包名·线程·方向·key·in·out)——包内列表包名冗余,
 * 线程为噪音,列表行只留「最重要的一段数据」。
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