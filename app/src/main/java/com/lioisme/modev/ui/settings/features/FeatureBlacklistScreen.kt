package com.lioisme.modev.ui.settings.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.ui.components.ModevInputSheet
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import kotlinx.coroutines.launch

/** 可编辑黑名单分区种类(V37 T2.4)。 */
enum class BlacklistKind { ROOT_PACKAGES, XPOSED_PREFIXES, FRIDA_PORTS, MASK_KEYWORDS }

/** 全局检测黑名单追加 VM(写全局 feature 种子;内置基线只增不删)。 */
class FeatureBlacklistViewModel(private val container: AppContainer) : ViewModel() {

    val feature = container.settings.globalFeature

    fun add(kind: BlacklistKind, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = feature.value
            val next = when (kind) {
                BlacklistKind.ROOT_PACKAGES ->
                    current.copy(extraRootPackages = (current.extraRootPackages + trimmed).distinct())
                BlacklistKind.XPOSED_PREFIXES ->
                    current.copy(extraXposedPrefixes = (current.extraXposedPrefixes + trimmed).distinct())
                BlacklistKind.FRIDA_PORTS -> {
                    val port = trimmed.toIntOrNull() ?: return@launch
                    current.copy(extraFridaPorts = (current.extraFridaPorts + port).distinct())
                }
                BlacklistKind.MASK_KEYWORDS ->
                    current.copy(extraMaskKeywords = (current.extraMaskKeywords + trimmed).distinct())
            }
            container.settings.setGlobalFeature(next)
        }
    }

    fun remove(kind: BlacklistKind, value: String) {
        viewModelScope.launch {
            val current = feature.value
            val next = when (kind) {
                BlacklistKind.ROOT_PACKAGES -> current.copy(extraRootPackages = current.extraRootPackages - value)
                BlacklistKind.XPOSED_PREFIXES -> current.copy(extraXposedPrefixes = current.extraXposedPrefixes - value)
                BlacklistKind.FRIDA_PORTS -> {
                    val port = value.toIntOrNull() ?: return@launch
                    current.copy(extraFridaPorts = current.extraFridaPorts - port)
                }
                BlacklistKind.MASK_KEYWORDS -> current.copy(extraMaskKeywords = current.extraMaskKeywords - value)
            }
            container.settings.setGlobalFeature(next)
        }
    }
}

class FeatureBlacklistViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FeatureBlacklistViewModel(container) as T
}

/**
 * 全局检测黑名单追加页(V37 T3.5/T2.4):四类词表(Root 包名 / Xposed 前缀 /
 * Frida 端口 / 掩码关键词)的**用户追加项**管理;内置基线不可删(只增不删语义)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureBlacklistScreen(
    viewModel: FeatureBlacklistViewModel,
    onBack: () -> Unit,
) {
    val feature by viewModel.feature.collectAsStateWithLifecycle()
    var addingKind by remember { mutableStateOf<BlacklistKind?>(null) }
    val res = rememberLocaleAwareResources()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { ModevTopBar(title = stringResource(R.string.blacklist_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.blacklist_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            BlacklistKind.entries.forEach { kind ->
                val items = when (kind) {
                    BlacklistKind.ROOT_PACKAGES -> feature.extraRootPackages
                    BlacklistKind.XPOSED_PREFIXES -> feature.extraXposedPrefixes
                    BlacklistKind.FRIDA_PORTS -> feature.extraFridaPorts.map { it.toString() }
                    BlacklistKind.MASK_KEYWORDS -> feature.extraMaskKeywords
                }
                SettingsSection(stringResource(blacklistKindLabelRes(kind))) {
                    if (items.isEmpty()) {
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            headlineContent = {
                                Text(
                                    stringResource(R.string.blacklist_section_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                items.chunked(3).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { value ->
                                            InputChip(
                                                selected = false,
                                                onClick = { viewModel.remove(kind, value) },
                                                label = { Text(value) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = null,
                                                        modifier = Modifier.width(16.dp).height(16.dp),
                                                    )
                                                },
                                                modifier = Modifier.testTag("blacklist-chip-${kind.name}-$value"),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        headlineContent = {
                            Text(
                                stringResource(R.string.blacklist_add_action),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blacklist-add-${kind.name}")
                            .clickable { addingKind = kind },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    addingKind?.let { kind ->
        BlacklistAddSheet(
            kind = kind,
            onDismiss = { addingKind = null },
            onConfirm = { value ->
                viewModel.add(kind, value)
                addingKind = null
            },
        )
    }
}

/** 黑名单追加项输入 sheet(V39 收编:统一 [ModevInputSheet] 脚手架,hide 动画收口)。 */
@Composable
private fun BlacklistAddSheet(
    kind: BlacklistKind,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val res = rememberLocaleAwareResources()
    val numeric = kind == BlacklistKind.FRIDA_PORTS
    ModevInputSheet(
        title = res.getString(blacklistKindLabelRes(kind)),
        initialValue = "",
        label = stringResource(R.string.blacklist_value_label),
        errorText = if (numeric) stringResource(R.string.settings_port_invalid) else "",
        inputTestTag = "blacklist-input",
        confirmTestTag = "blacklist-confirm",
        validate = { text ->
            val trimmed = text.trim()
            trimmed.isNotEmpty() && (!numeric || trimmed.toIntOrNull() in 1..65535)
        },
        onSubmit = { onConfirm(it.trim()) },
        onDismiss = onDismiss,
    )
}

internal fun blacklistKindLabelRes(kind: BlacklistKind): Int = when (kind) {
    BlacklistKind.ROOT_PACKAGES -> R.string.blacklist_root_packages
    BlacklistKind.XPOSED_PREFIXES -> R.string.blacklist_xposed_prefixes
    BlacklistKind.FRIDA_PORTS -> R.string.blacklist_frida_ports
    BlacklistKind.MASK_KEYWORDS -> R.string.blacklist_mask_keywords
}
