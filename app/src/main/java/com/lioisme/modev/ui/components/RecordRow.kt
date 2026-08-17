package com.lioisme.modev.ui.components

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * 记录行(2026-08-17 V8 卡片化;2026-08-17 V12 秒级时间 + 参数独立成行):
 * 每行 = 圆角卡片(16dp, surfaceContainerLow 浮于 surface),行间留白 8dp,
 * 取代旧版「细分隔线 + 六段拼接摘要」的拥挤版式:
 * - 左:类别徽章 28dp 圆角方块(类别色 15% alpha 淡底 + 类别色字母,竖直居中,
 *   列锚点恒定);徽章与内容间距 14dp;
 * - 主体 Column(weight 1f),除徽章外每参数独立成行(V12 语义):
 *   行1 算法(等宽 bodyLarge,超长省略);
 *   行2 操作阶段(labelMedium 类别色,与徽章同色系);
 *   行3(仅当有方向)方向胶囊(类别色 15% alpha 淡底小字);
 *   行4(仅当有数据)唯一最有价值数据段(等宽 labelMedium,优先级 key > alias > out > in);
 *   行5 时间(秒级精度,labelSmall onSurfaceVariant,尊重系统 12/24h 时制);
 * - 行高 minHeight 68dp(内容自适应加高,空段行高一致);
 * - 单击进详情;长按复制记录文本(涟漪随卡片圆角裁剪)。
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
                    .heightIn(min = RECORD_ROW_MIN_HEIGHT)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左列:类别徽章(固定尺寸;右侧内容左缘全列表恒定锚点)。
            KindBadge(kind = record.kind)
            Spacer(Modifier.width(BADGE_TO_CONTENT_GAP))
            // 主体:每参数独立成行(V12;徽章为唯一同行豁免元素)。
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.algorithm,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = operationLabel(record.operation),
                    style = MaterialTheme.typography.labelMedium,
                    color = kindColor(record.kind),
                    maxLines = 1,
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = timeFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 类别徽章:28dp 圆角方块(类别色 15% alpha 淡底 + 类别色字母),列锚点恒定。 */
@Composable
private fun KindBadge(kind: CryptoKind) {
    val color = kindColor(kind)
    Box(
        modifier =
            Modifier
                .size(KIND_BADGE_SIZE)
                .background(color.copy(alpha = KIND_BADGE_ALPHA), RoundedCornerShape(KIND_BADGE_CORNER)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kindSymbol(kind).toString(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = color,
            maxLines = 1,
        )
    }
}

/** 方向胶囊:类别色淡底 + 类别色小字(与徽章同色系;方向是加密流关键信息)。 */
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

/** 类别徽章固定尺寸(右侧内容左缘全列表恒定锚点)。 */
private val KIND_BADGE_SIZE = 28.dp

/** 徽章与内容间距(固定,与徽章宽共同构成列锚点)。 */
private val BADGE_TO_CONTENT_GAP = 14.dp

/** 徽章淡底透明度(类别色 alpha,淡色底 + 类别色字母)。 */
private const val KIND_BADGE_ALPHA = 0.15f

/** 徽章圆角(与 LIDE LevelBadge 同款 6dp,勿用 shapes.small=8dp)。 */
private val KIND_BADGE_CORNER = 6.dp

/** 行最小高度(数据段缺失时行高一致,列表滚动整齐)。 */
private val RECORD_ROW_MIN_HEIGHT = 68.dp

/**
 * 最有价值数据段(单段展示不堆叠,优先级 密钥 > 别名 > 输出 > 输入):
 * 取代旧版六段拼接摘要(包名·线程·方向·key·in·out)——包内列表包名冗余,
 * 线程为噪音,列表行只留「最重要的一段数据」。
 */
@Composable
private fun CryptoRecord.valuableDataSnippet(): String? = when {
    keyAlias != null -> stringResource(R.string.record_data_alias) + " " + keyAlias
    key != null -> stringResource(R.string.record_data_key) + " " + key.hex.take(24)
    output != null -> stringResource(R.string.record_data_out) + " " + output.hex.take(24)
    input != null -> stringResource(R.string.record_data_in) + " " + input.hex.take(24)
    else -> null
}

/** 类别徽章字母(单字符扫描)。 */
private fun kindSymbol(kind: CryptoKind): Char = when (kind) {
    CryptoKind.MESSAGE_DIGEST -> 'D'
    CryptoKind.CIPHER -> 'C'
    CryptoKind.MAC -> 'M'
    CryptoKind.SIGNATURE -> 'S'
    CryptoKind.KEY_PARAM -> 'P'
    CryptoKind.KEY_GENERATION -> 'G'
    CryptoKind.KEY_FACTORY -> 'F'
    CryptoKind.KEY_STORE -> 'K'
    CryptoKind.KEY_AGREEMENT -> 'A'
    CryptoKind.RANDOM -> 'R'
    CryptoKind.BASE64 -> 'B'
    CryptoKind.WEB_CRYPTO -> 'W'
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
    CryptoKind.WEB_CRYPTO -> R.string.kind_webview
}

/** 操作标签(2026-08-16 V3 起走资源,消除硬编码英文;i18n 双语言)。 */
@Composable
fun operationLabel(operation: CryptoOperation): String = stringResource(
    when (operation) {
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
    },
)

/** 方向标签(资源化;未知模式带模式号,同详情页展示)。 */
@Composable
fun directionLabel(direction: Int): String = when (direction) {
    1 -> stringResource(R.string.dir_encrypt)
    2 -> stringResource(R.string.dir_decrypt)
    3 -> stringResource(R.string.dir_wrap)
    4 -> stringResource(R.string.dir_unwrap)
    else -> stringResource(R.string.dir_mode, direction)
}