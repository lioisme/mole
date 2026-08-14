package com.lioisme.modev.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.util.BytesView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录详情:完整元数据 + 密钥/IV/输入/输出的 hex 与文本 + 调用堆栈。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailDialog(record: CryptoRecord, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                record.algorithm,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${record.packageName} · ${record.threadName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetaValue("类别", kindLabel(record.kind))
                MetaValue("操作", operationLabel(record.operation))
                MetaValue("时间", detailTimeFmt.format(Date(record.timestamp)))
                MetaValue("上下文", "#${record.contextId}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                record.direction?.let { MetaValue("方向", directionLabel(it)) }
                record.provider?.let { MetaValue("提供者", it) }
                record.keyExportable.takeIf { it }?.let { MetaValue("密钥", "可导出") }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()

            record.key?.let { Section("密钥", it, exportableNote = record.keyExportable) }
            record.keyAlias?.let {
                SectionText("密钥别名(硬件密钥,不可导出)", it, monospace = true)
            }
            record.iv?.let { Section("IV", it) }
            record.input?.let { Section("输入数据", it) }
            record.output?.let { Section("输出数据", it) }

            if (record.stack.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "调用堆栈",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    record.stack.forEach { frame ->
                        Text(
                            frame,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaValue(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Section(title: String, view: BytesView, exportableNote: Boolean = true) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    view.text?.let { text ->
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
    }
    Text(
        view.hex,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "共 ${view.totalLength} 字节${if (view.truncated) "(已截断)" else ""}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!exportableNote) {
        Text(
            "密钥材料不可导出(硬件/KeyStore)",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFFB74D),
        )
    }
}

@Composable
private fun SectionText(title: String, value: String, monospace: Boolean = false) {
    Spacer(Modifier.height(12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        value,
        style = if (monospace) {
            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyMedium
        },
    )
}

private val detailTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())