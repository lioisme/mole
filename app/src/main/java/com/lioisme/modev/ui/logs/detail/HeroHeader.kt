package com.lioisme.modev.ui.logs.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.BehaviorRecord
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.ui.components.behaviorKindLabelRes
import com.lioisme.modev.ui.components.behaviorOperationLabel
import com.lioisme.modev.ui.components.behaviorOperationLabelRes
import com.lioisme.modev.ui.components.directionLabel
import com.lioisme.modev.ui.components.displayAlgorithm
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.operationLabel
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.sqlOperationLabel
import com.lioisme.modev.ui.theme.AppShapes
import com.lioisme.modev.ui.theme.behaviorKindColor
import com.lioisme.modev.ui.theme.kindColor
import com.lioisme.modev.ui.theme.sqlKindColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录身份卡(V24 G3 重设计):首屏一屏内完成「这是什么操作」的判读——
 * 类别徽章(kindColor 语义色,与列表行徽章同源)+ 操作徽章 + 算法名
 * (titleLarge 等宽)+ 方向/验签状态行 + 时间戳。整卡单一 SelectionContainer,
 * 保留 V12 起元数据可长按复制的语义。
 * 视觉全部走 MD3 token:Card(surfaceContainerLow,AppShapes.large)+
 * AssistChip(容器色 = 语义色 @15% alpha,对齐 RecordRow 徽章惯例)。
 */

/** 每次调用按当前 Locale 构造(语言切换重建后不残留旧 locale;V15 契约)。 */
internal fun detailTimeFmt(): SimpleDateFormat =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

@Composable
internal fun CryptoHero(record: CryptoRecord) {
    val context = LocalContext.current
    val timeFmt = remember(context) { detailTimeFmt() }
    val kindAccent = kindColor(record.kind)
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentChip(
                label = stringResource(kindLabelRes(record.kind)),
                accent = kindAccent,
            )
            Spacer(Modifier.width(8.dp))
            HeroChip(label = operationLabel(record.operation))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = record.displayAlgorithm(),
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            record.direction?.let { HeroChip(label = directionLabel(it)) }
            record.verifyResult?.let { verified ->
                StatusChip(
                    label = stringResource(if (verified) R.string.verify_ok else R.string.verify_fail),
                    positive = verified,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = timeFmt.format(Date(record.timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BehaviorHero(record: BehaviorRecord) {
    val context = LocalContext.current
    val timeFmt = remember(context) { detailTimeFmt() }
    val kindAccent = behaviorKindColor(record.kind)
    val operationFallback = stringResource(behaviorOperationLabelRes(record.operation))
    val headline =
        record.path?.trimEnd('/')?.ifEmpty { "/" }?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
            ?: record.commandDisplay()?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: operationFallback
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentChip(
                label = stringResource(behaviorKindLabelRes(record.kind)),
                accent = kindAccent,
            )
            Spacer(Modifier.width(8.dp))
            HeroChip(label = behaviorOperationLabel(record.operation))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = timeFmt.format(Date(record.timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SqlHero(record: SqlRecord) {
    val context = LocalContext.current
    val timeFmt = remember(context) { detailTimeFmt() }
    val kindAccent = sqlKindColor(record.kind)
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentChip(
                label = stringResource(sqlKindLabelRes(record.kind)),
                accent = kindAccent,
            )
            Spacer(Modifier.width(8.dp))
            HeroChip(label = sqlOperationLabel(record.operation))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = record.databaseName,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = timeFmt.format(Date(record.timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroCard(content: @Composable () -> Unit) {
    Card(
        shape = AppShapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().testTag("detail-hero"),
    ) {
        SelectionContainer {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { content() }
        }
    }
}

/** 类别语义徽章:容器 = 语义色 @15%(RecordRow KIND_BADGE_ALPHA 同源),标签全量语义色。 */
@Composable
private fun AccentChip(label: String, accent: Color) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(label, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = accent.copy(alpha = 0.15f),
            disabledLabelColor = accent,
        ),
        border = null,
    )
}

/** 中性信息徽章(surfaceVariant 容器,onSurfaceVariant 标签)。 */
@Composable
private fun HeroChip(label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = null,
    )
}

/** 验签结果徽章:✓ primary / ✗ error,leadingIcon 承载语义(Check/Close)。 */
@Composable
private fun StatusChip(label: String, positive: Boolean) {
    val accent = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    AssistChip(
        onClick = {},
        enabled = false,
        leadingIcon = {
            Icon(
                imageVector = if (positive) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        },
        label = {
            Text(label, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = accent.copy(alpha = 0.15f),
            disabledLabelColor = accent,
        ),
        border = null,
    )
}
