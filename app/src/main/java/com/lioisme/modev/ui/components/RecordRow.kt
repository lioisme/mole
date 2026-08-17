package com.lioisme.modev.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

/**
 * 记录行(2026-08-16 V3,自 LogsScreen 迁出,算法列表与算法详情共用):
 * 固定列锚点(LogFox 同款):每行 = [类别徽章固定宽列 28dp] + 固定 12dp 间距 + [右侧内容列],
 * 右侧消息/元数据左缘全列表恒定 x 对齐:
 * - 左列:类别徽章——圆角 6dp、类别色 15% alpha 淡底 + 类别色字母
 *   (labelMedium 等宽居中,top 对齐消息首行);
 * - 主体 Column(weight 1f):行1 消息(算法 + 操作,bodyLarge Monospace
 *   maxLines 2,超长省略 + 详情页全文);行2 元数据小字一行
 *   (labelSmall onSurfaceVariant):`包名末段 · 线程 · 方向 · 关键数据`(包内列表)
 *   或 `时间 · 包名末段 · 线程`(全局场景由调用方自定义摘要);
 * - 行高 minHeight 64dp + padding(h16, v10);
 * - 行末细分隔线(outlineVariant 0.4 alpha);
 * - 单击进详情;长按复制记录文本。
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
    val dirLabel = record.direction?.let { directionLabel(it) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                })
                .heightIn(min = RECORD_ROW_MIN_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // 左列:类别徽章(固定宽淡底圆角;右侧内容左缘恒定锚点)。
        KindBadge(kind = record.kind)
        Spacer(Modifier.width(BADGE_TO_CONTENT_GAP))
        // 主体:算法 + 操作独占整行区 + 元数据小字一行,左缘恒定。
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.algorithm,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    operationLabel(record.operation),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = record.summary(directionLabel = dirLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // 行末细分隔线:日志密度大时扫描清晰(LIDE ClassRow/LogRow 同款惯例)。
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** 类别徽章:固定宽淡底圆角(类别色 15% alpha 底 + 类别色字母居中),列锚点恒定。 */
@Composable
private fun KindBadge(kind: CryptoKind) {
    val color = kindColor(kind)
    Box(
        modifier =
            Modifier
                .width(KIND_BADGE_WIDTH)
                .background(color.copy(alpha = KIND_BADGE_ALPHA), RoundedCornerShape(KIND_BADGE_CORNER))
                .padding(vertical = 2.dp),
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

/** 类别徽章固定列宽(右侧内容左缘全列表恒定锚点)。 */
private val KIND_BADGE_WIDTH = 28.dp

/** 徽章与内容间距(固定,与徽章宽共同构成列锚点)。 */
private val BADGE_TO_CONTENT_GAP = 12.dp

/** 徽章淡底透明度(类别色 alpha,淡色底 + 类别色字母)。 */
private const val KIND_BADGE_ALPHA = 0.15f

/** 徽章圆角(与 LIDE LevelBadge 同款 6dp,勿用 shapes.small=8dp)。 */
private val KIND_BADGE_CORNER = 6.dp

/** 行最小高度(消息 1 行时行高一致,列表滚动整齐)。 */
private val RECORD_ROW_MIN_HEIGHT = 64.dp

/** 记录行摘要:包名末段 · 线程 · 方向 · 最有价值数据段。 */
private fun CryptoRecord.summary(directionLabel: String?): String = buildString {
    append(packageName.substringAfterLast('.'))
    append(" · ")
    append(threadName)
    directionLabel?.let { append(" · ").append(it) }
    key?.let { append(" · key:").append(it.hex.take(24)) }
    keyAlias?.let { append(" · alias:").append(it) }
    input?.let { append(" · in:").append(it.hex.take(24)) }
    output?.let { append(" · out:").append(it.hex.take(24)) }
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
