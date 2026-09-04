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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.SpoofProfiles
import com.lioisme.modev.ui.apps.spoofProfileLabelRes
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import kotlinx.coroutines.launch

/** 全局伪装画像 VM:档位选择 + 字段覆写(写全局 feature 种子,V37 T3.5)。 */
class SpoofProfileViewModel(private val container: AppContainer) : ViewModel() {

    val feature = container.settings.globalFeature

    fun setProfile(id: String) {
        viewModelScope.launch {
            container.settings.setGlobalFeature(feature.value.copy(spoofProfileId = id))
        }
    }

    fun setOverride(key: String, value: String) {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return
        viewModelScope.launch {
            container.settings.setGlobalFeature(
                feature.value.copy(spoofOverrides = feature.value.spoofOverrides + (trimmedKey to value)),
            )
        }
    }

    fun removeOverride(key: String) {
        viewModelScope.launch {
            container.settings.setGlobalFeature(
                feature.value.copy(spoofOverrides = feature.value.spoofOverrides - key),
            )
        }
    }
}

class SpoofProfileViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SpoofProfileViewModel(container) as T
}

/**
 * 全局伪装画像页(V37 T3.5):内置档位单选 + 字段覆写清单(增删)。
 * 覆写键形态见 [SpoofProfiles.resolve](build./ro./telephony./wifi./cpu. 前缀)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofProfileScreen(
    viewModel: SpoofProfileViewModel,
    onBack: () -> Unit,
) {
    val feature by viewModel.feature.collectAsStateWithLifecycle()
    var showOverrideSheet by remember { mutableStateOf(false) }
    var pendingDeleteKey by remember { mutableStateOf<String?>(null) }
    val res = rememberLocaleAwareResources()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(title = stringResource(R.string.profiles_title), onBack = onBack)
        },
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
                stringResource(R.string.profiles_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )
            SettingsSection(stringResource(R.string.profiles_global_section)) {
                SpoofProfiles.BUILTINS.forEachIndexed { index, profile ->
                    if (index > 0) HorizontalDivider16()
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        headlineContent = {
                            Text(
                                res.getString(spoofProfileLabelRes(profile.id)),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                res.getString(spoofProfileSupportRes(profile.id)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = feature.spoofProfileId == profile.id,
                                onClick = { viewModel.setProfile(profile.id) },
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile-option-${profile.id}")
                            .clickable { viewModel.setProfile(profile.id) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsSection(stringResource(R.string.profiles_overrides_section)) {
                if (feature.spoofOverrides.isEmpty()) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        headlineContent = {
                            Text(
                                stringResource(R.string.profiles_overrides_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                } else {
                    feature.spoofOverrides.entries.sortedBy { it.key }.forEachIndexed { index, (key, value) ->
                        if (index > 0) HorizontalDivider16()
                        ListItem(
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            headlineContent = {
                                Text(key, style = MaterialTheme.typography.bodyMedium)
                            },
                            supportingContent = {
                                Text(
                                    value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                TipIconButton(
                                    tooltip = res.getString(R.string.profiles_override_delete),
                                    onClick = { pendingDeleteKey = key },
                                    modifier = Modifier.testTag("profile-override-delete-$key"),
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = res.getString(R.string.profiles_override_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
                HorizontalDivider16()
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    headlineContent = {
                        Text(
                            stringResource(R.string.profiles_override_add),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Add,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile-override-add")
                        .clickable { showOverrideSheet = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showOverrideSheet) {
        OverrideEditSheet(
            existingKeys = feature.spoofOverrides.keys,
            onDismiss = { showOverrideSheet = false },
            onConfirm = { key, value ->
                viewModel.setOverride(key, value)
                showOverrideSheet = false
            },
        )
    }

    pendingDeleteKey?.let { key ->
        AlertDialog(
            onDismissRequest = { pendingDeleteKey = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = { Text(stringResource(R.string.profiles_override_delete_confirm, key)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeOverride(key)
                    pendingDeleteKey = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteKey = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** 覆写条目编辑 sheet(键 + 值;契约 4)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverrideEditSheet(
    existingKeys: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val res = rememberLocaleAwareResources()

    fun confirm() {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirm(trimmed, value.trim()) }
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
                stringResource(R.string.profiles_override_add),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.profiles_override_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.profiles_override_key_label)) },
                singleLine = true,
                isError = key.isNotBlank() && existingKeys.contains(key.trim()),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-override-key"),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.profiles_override_value_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-override-value"),
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { confirm() },
                    modifier = Modifier.testTag("profile-override-confirm"),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

private fun spoofProfileSupportRes(id: String): Int = when (id) {
    SpoofProfiles.AUTO -> R.string.feature_profile_auto
    SpoofProfiles.PIXEL_7_PRO -> R.string.feature_profile_pixel7pro
    SpoofProfiles.XIAOMI_13 -> R.string.feature_profile_xiaomi13
    else -> R.string.feature_profile_auto
}
