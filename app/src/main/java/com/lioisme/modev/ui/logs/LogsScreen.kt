package com.lioisme.modev.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.core.model.CryptoKind
import com.lioisme.modev.core.model.CryptoOperation
import com.lioisme.modev.core.model.CryptoRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日志页:包/类别/关键字过滤,点击记录查看详情(含堆栈)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: LogsViewModel) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val packages by viewModel.packages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selectedKind by remember { mutableStateOf<CryptoKind?>(null) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var selectedRecord by remember { mutableStateOf<CryptoRecord?>(null) }
    var packageMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("算法日志") },
                actions = {
                    IconButton(onClick = viewModel::clearAll) {
                        Icon(Icons.Filled.Delete, contentDescription = "清空全部")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            // 包过滤
            ExposedDropdownMenuBox(
                expanded = packageMenu,
                onExpandedChange = { packageMenu = it },
            ) {
                OutlinedTextField(
                    value = selectedPackage ?: "全部应用",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("目标应用") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = packageMenu) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                ExposedDropdownMenu(
                    expanded = packageMenu,
                    onDismissRequest = { packageMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("全部应用") },
                        onClick = {
                            selectedPackage = null
                            viewModel.setPackage(null)
                            packageMenu = false
                        },
                    )
                    packages.forEach { pkg ->
                        DropdownMenuItem(
                            text = { Text(pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                selectedPackage = pkg
                                viewModel.setPackage(pkg)
                                packageMenu = false
                            },
                        )
                    }
                }
            }

            // 搜索
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索算法 / 密钥 / 数据…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            // 类别过滤
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedKind == null,
                    onClick = {
                        selectedKind = null
                        viewModel.setKind(null)
                    },
                    label = { Text("全部") },
                )
                CryptoKind.entries.forEach { kind ->
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = {
                            selectedKind = kind
                            viewModel.setKind(kind)
                        },
                        label = { Text(kindLabel(kind)) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "暂无记录\n\n开启目标应用的算法捕获开关后,\n算法调用将实时显示在这里。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(records, key = { it.id }) { record ->
                        RecordRow(record = record, onClick = { selectedRecord = record })
                    }
                }
            }
        }
    }

    selectedRecord?.let { record ->
        RecordDetailDialog(record = record, onDismiss = { selectedRecord = null })
    }
}

@Composable
private fun RecordRow(record: CryptoRecord, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = kindColor(record.kind),
                    shape = MaterialTheme.shapes.small,
                ) {}
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.algorithm,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        operationLabel(record.operation),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    summaryOf(record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                timeFmt.format(Date(record.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 记录行摘要:密钥/输入/输出中最有价值的一段。 */
private fun summaryOf(record: CryptoRecord): String = buildString {
    append(record.packageName.substringAfterLast('.'))
    append(" · ")
    append(record.threadName)
    record.direction?.let { append(" · ").append(directionLabel(it)) }
    record.key?.let { append(" · key:").append(it.hex.take(24)) }
    record.keyAlias?.let { append(" · alias:").append(it) }
    record.input?.let { append(" · in:").append(it.hex.take(24)) }
    record.output?.let { append(" · out:").append(it.hex.take(24)) }
}

fun kindLabel(kind: CryptoKind): String = when (kind) {
    CryptoKind.MESSAGE_DIGEST -> "摘要"
    CryptoKind.CIPHER -> "加解密"
    CryptoKind.MAC -> "HMAC"
    CryptoKind.SIGNATURE -> "签名"
    CryptoKind.KEY_PARAM -> "密钥参数"
    CryptoKind.KEY_GENERATION -> "密钥生成"
    CryptoKind.KEY_FACTORY -> "密钥还原"
    CryptoKind.BASE64 -> "Base64"
}

fun operationLabel(operation: CryptoOperation): String = when (operation) {
    CryptoOperation.GET_INSTANCE -> "GET"
    CryptoOperation.INIT -> "INIT"
    CryptoOperation.UPDATE -> "UPDATE"
    CryptoOperation.DO_FINAL -> "FINAL"
    CryptoOperation.DIGEST -> "DIGEST"
    CryptoOperation.SIGN -> "SIGN"
    CryptoOperation.VERIFY -> "VERIFY"
    CryptoOperation.GET_IV -> "IV"
    CryptoOperation.CONSTRUCT -> "NEW"
    CryptoOperation.GENERATE -> "GEN"
    CryptoOperation.GENERATE_PRIVATE -> "PRIV"
    CryptoOperation.GENERATE_PUBLIC -> "PUB"
    CryptoOperation.GENERATE_SECRET -> "SEC"
    CryptoOperation.ENCODE -> "ENC"
    CryptoOperation.DECODE -> "DEC"
}

fun directionLabel(direction: Int): String = when (direction) {
    1 -> "ENCRYPT"
    2 -> "DECRYPT"
    3 -> "WRAP"
    4 -> "UNWRAP"
    else -> "MODE=$direction"
}

fun kindColor(kind: CryptoKind): Color = when (kind) {
    CryptoKind.CIPHER -> Color(0xFF4FC3F7)
    CryptoKind.MESSAGE_DIGEST -> Color(0xFFFFB74D)
    CryptoKind.MAC -> Color(0xFFBA68C8)
    CryptoKind.SIGNATURE -> Color(0xFFE57373)
    CryptoKind.KEY_PARAM -> Color(0xFF81C784)
    CryptoKind.KEY_GENERATION -> Color(0xFF4DB6AC)
    CryptoKind.KEY_FACTORY -> Color(0xFFFFF176)
    CryptoKind.BASE64 -> Color(0xFF90A4AE)
}

private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())