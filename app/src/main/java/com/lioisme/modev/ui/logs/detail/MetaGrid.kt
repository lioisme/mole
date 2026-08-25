package com.lioisme.modev.ui.logs.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lioisme.modev.R
import com.lioisme.modev.core.model.CryptoRecord
import com.lioisme.modev.core.model.SqlRecord
import com.lioisme.modev.ui.components.directionLabel
import com.lioisme.modev.ui.components.kindLabelRes
import com.lioisme.modev.ui.components.sqlKindLabelRes
import com.lioisme.modev.ui.components.sqlOperationLabel
import com.lioisme.modev.ui.theme.kindColor
import com.lioisme.modev.ui.theme.sqlKindColor

/**
 * 标量元数据两列网格(V24 G3 重设计):替换 V12 起的 label 定宽长表,
 * 首屏纵向成本减半;条件行语义保留(provider/process/thread/context/direction/
 * verify/keyExportable;SQL:dbPath/rowCount/affectedCount/rowId)。
 * 单元格 = label(labelMedium onSurfaceVariant)上 + 值(bodySmall 等宽)下;
 * 两列等宽(weight 1f),基础布局组合(非自研组件,G4 边界内)。
 * 整网格单一 SelectionContainer(继承元数据可复制语义,V12 契约)。
 */

/** 单个元数据条目(valueColor 仅语义行使用:kind/verify)。 */
data class MetaEntry(val label: String, val value: String, val valueColor: Color? = null)

@Composable
internal fun cryptoMetaEntries(record: CryptoRecord): List<MetaEntry> {
    val entries = mutableListOf<MetaEntry>()
    entries += MetaEntry(
        stringResource(R.string.meta_kind),
        stringResource(kindLabelRes(record.kind)),
        kindColor(record.kind),
    )
    record.direction?.let {
        entries += MetaEntry(stringResource(R.string.meta_direction), directionLabel(it))
    }
    record.provider?.let { entries += MetaEntry(stringResource(R.string.meta_provider), it) }
    record.processName?.let { entries += MetaEntry(stringResource(R.string.meta_process), it) }
    entries += MetaEntry(stringResource(R.string.meta_context), "#${record.contextId}")
    entries += MetaEntry(stringResource(R.string.meta_thread), record.threadName)
    record.verifyResult?.let {
        entries += MetaEntry(
            stringResource(R.string.meta_verify),
            stringResource(if (it) R.string.verify_ok else R.string.verify_fail),
            if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
    if (record.keyExportable) {
        entries += MetaEntry(stringResource(R.string.meta_key_exportable), stringResource(R.string.confirm))
    }
    return entries
}

@Composable
internal fun sqlMetaEntries(record: SqlRecord): List<MetaEntry> {
    val entries = mutableListOf<MetaEntry>()
    entries += MetaEntry(
        stringResource(R.string.meta_kind),
        stringResource(sqlKindLabelRes(record.kind)),
        sqlKindColor(record.kind),
    )
    entries += MetaEntry(stringResource(R.string.meta_operation), sqlOperationLabel(record.operation))
    record.dbPath?.let { entries += MetaEntry(stringResource(R.string.meta_db_path), it) }
    entries += MetaEntry(stringResource(R.string.meta_context), "#${record.contextId}")
    entries += MetaEntry(stringResource(R.string.meta_thread), record.threadName)
    record.rowCount?.let { entries += MetaEntry(stringResource(R.string.meta_rows), it.toString()) }
    record.affectedCount?.let { entries += MetaEntry(stringResource(R.string.meta_affected), it.toString()) }
    record.rowId?.let { entries += MetaEntry(stringResource(R.string.meta_row_id), it.toString()) }
    return entries
}

/** 两列网格:奇数末项以空位补行(跨两列占位),保持视觉均衡。 */
@Composable
internal fun MetaGrid(entries: List<MetaEntry>) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth()) {
            var index = 0
            while (index < entries.size) {
                val first = entries[index]
                val second = entries.getOrNull(index + 1)
                Row(Modifier.fillMaxWidth()) {
                    MetaCell(first, Modifier.weight(1f))
                    if (second != null) {
                        MetaCell(second, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
                index += 2
            }
        }
    }
}

@Composable
private fun MetaCell(entry: MetaEntry, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 5.dp, horizontal = 4.dp)) {
        Text(
            text = entry.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = entry.valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
