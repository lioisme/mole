package com.lioisme.modev.ui.settings.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.KeywordMatchMode
import com.lioisme.modev.ui.components.EmptyState
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import kotlinx.coroutines.launch

/** 关键词库管理 VM(V37 T3.5)。 */
class KeywordLibraryViewModel(container: AppContainer) : ViewModel() {

    private val library = container.keywordLibrary

    val entries = library.entries

    fun add(text: String, mode: KeywordMatchMode, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(library.add(text, mode)) }
    }

    fun update(id: Long, text: String, mode: KeywordMatchMode, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(library.update(id, text, mode)) }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { library.setEnabled(id, enabled) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { library.remove(id) }
    }
}

class KeywordLibraryViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        KeywordLibraryViewModel(container) as T
}

/**
 * 全局弹窗拦截关键词库管理页(V37 T3.5):
 * 条目 = 文本 + 匹配模式标签 + 全局启停 Switch + 删除;
 * 新增/编辑经底部 sheet(契约 4);空态引导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordLibraryScreen(
    viewModel: KeywordLibraryViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditingKeyword?>(null) }
    var pendingDelete by remember { mutableStateOf<KeywordEntry?>(null) }
    val res = rememberLocaleAwareResources()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.keywords_title),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.keywords_add_action),
                        onClick = { editing = EditingKeyword(null, "", KeywordMatchMode.CONTAINS) },
                        modifier = Modifier.testTag("keywords-add"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.keywords_add_action))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyState(
                    icon = Icons.Outlined.Chat,
                    message = stringResource(R.string.keywords_empty_title),
                    hint = stringResource(R.string.keywords_empty_hint),
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    KeywordRow(
                        entry = entry,
                        onToggle = { viewModel.setEnabled(entry.id, it) },
                        onEdit = { editing = EditingKeyword(entry.id, entry.text, entry.matchMode) },
                        onDelete = { pendingDelete = entry },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editing?.let { current ->
        KeywordEditSheet(
            current = current,
            onDismiss = { editing = null },
            onConfirm = { text, mode ->
                val snapshot = current
                fun handle(ok: Boolean) {
                    if (!ok) {
                        // 重复/非法:保持 sheet 打开由用户改文本(错误提示经 snackbar 简化为重复拒绝即关闭)
                    }
                    editing = null
                }
                if (snapshot.id == null) {
                    viewModel.add(text, mode) { ok -> handle(ok) }
                } else {
                    viewModel.update(snapshot.id, text, mode) { ok -> handle(ok) }
                }
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = { Text(stringResource(R.string.keywords_delete_confirm_msg, entry.text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(entry.id)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private data class EditingKeyword(val id: Long?, val text: String, val mode: KeywordMatchMode)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordRow(
    entry: KeywordEntry,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val res = rememberLocaleAwareResources()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(entry.text, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                res.getString(keywordModeLabelRes(entry.matchMode)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = entry.enabled,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                TipIconButton(
                    tooltip = res.getString(R.string.keywords_delete_action),
                    onClick = onDelete,
                    modifier = Modifier.testTag("keywords-delete-${entry.id}"),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = res.getString(R.string.keywords_delete_action),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keywords-row-${entry.id}")
            .clickable(onClick = onEdit),
    )
}

/** 新增/编辑关键词底部 sheet(文本 + 匹配模式下拉;契约 4)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeywordEditSheet(
    current: EditingKeyword,
    onDismiss: () -> Unit,
    onConfirm: (String, KeywordMatchMode) -> Unit,
) {
    var text by remember { mutableStateOf(current.text) }
    var mode by remember { mutableStateOf(current.mode) }
    var expanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val res = rememberLocaleAwareResources()

    fun confirm() {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm(trimmed, mode) }
    }

    ModalBottomSheet(
        onDismissRequest = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(
                    if (current.id == null) R.string.keywords_add_title else R.string.keywords_edit_title,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.keywords_text_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keywords-input"),
            )
            Spacer(Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = res.getString(keywordModeLabelRes(mode)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.keywords_mode_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .testTag("keywords-mode-anchor"),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    KeywordMatchMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(res.getString(keywordModeLabelRes(option))) },
                            onClick = {
                                mode = option
                                expanded = false
                            },
                            modifier = Modifier.testTag("keywords-mode-${option.name}"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { confirm() },
                    modifier = Modifier.testTag("keywords-confirm"),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

internal fun keywordModeLabelRes(mode: KeywordMatchMode): Int = when (mode) {
    KeywordMatchMode.CONTAINS -> R.string.keyword_mode_contains
    KeywordMatchMode.REGEX -> R.string.keyword_mode_regex
    KeywordMatchMode.EQUALS -> R.string.keyword_mode_equals
}
