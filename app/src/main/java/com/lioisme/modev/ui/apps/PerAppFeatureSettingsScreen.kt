package com.lioisme.modev.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.FeatureGroup
import com.lioisme.modev.core.model.FeatureGroups
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SpoofProfiles
import com.lioisme.modev.data.repo.KeywordLibraryRepository
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import com.lioisme.modev.ui.theme.Dimens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 单应用功能设置状态(V37 T3.4):功能域干预开关 + 关键词启用子集 + 画像选择;
 * 与监听页共享同一偏差存储(override_{pkg}),各写各的字段互不覆盖。
 */
class PerAppFeatureSettingsViewModel(
    val packageName: String,
    private val settings: SettingsRepository,
    private val keywordLibrary: KeywordLibraryRepository,
) : ViewModel() {

    val effective: StateFlow<HookSwitch> =
        combine(settings.globalSwitch, settings.overrides) { default, map ->
            EffectiveSwitch.resolve(default, map[packageName])
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.effectiveSwitch(packageName),
        )

    val override: StateFlow<AppListenOverride> = settings.overrides
        .map { it[packageName] ?: AppListenOverride.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListenOverride.DEFAULT)

    /** 全局关键词库快照(启用条目)。 */
    val keywordEntries: StateFlow<List<KeywordEntry>> = keywordLibrary.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setUiMaster(value: Boolean) = update { it.copy(uiListen = value) }
    fun setNetworkMaster(value: Boolean) = update { it.copy(networkListen = value) }
    fun setDeviceEnvMaster(value: Boolean) = update { it.copy(deviceEnvListen = value) }
    fun setProcessControlMaster(value: Boolean) = update { it.copy(processControlListen = value) }

    fun setKind(kindId: String, value: Boolean) {
        update { it.copy(kinds = it.kinds + (kindId to value)) }
    }

    /** 关键词跟随全局开关:关闭时物化当前全局启用集为显式子集。 */
    fun setKeywordFollowGlobal(follow: Boolean) {
        update { current ->
            if (follow) {
                current.copy(keywordFollowGlobal = null, keywordIds = null)
            } else {
                val materialized = keywordLibrary.resolveRules(null).mapNotNull { rule ->
                    keywordLibrary.entries.value.firstOrNull { it.text == rule.text }?.id
                }
                current.copy(keywordFollowGlobal = false, keywordIds = materialized)
            }
        }
    }

    /** 单关键词启停(首次关闭跟随全局时自动物化子集)。 */
    fun toggleKeyword(id: Long, enabled: Boolean) {
        update { current ->
            val base = if (current.keywordFollowGlobal == false) {
                current.keywordIds ?: emptyList()
            } else {
                keywordLibrary.resolveRules(null).mapNotNull { rule ->
                    keywordLibrary.entries.value.firstOrNull { it.text == rule.text }?.id
                }
            }
            val next = if (enabled) (base + id).distinct() else base - id
            current.copy(keywordFollowGlobal = false, keywordIds = next)
        }
    }

    /** 画像选择(null = 跟随全局)。 */
    fun setSpoofProfile(profileId: String?) {
        update { it.copy(spoofProfileId = profileId) }
    }

    fun reset() {
        viewModelScope.launch { settings.removeOverride(packageName) }
    }

    private fun update(transform: (AppListenOverride) -> AppListenOverride) {
        viewModelScope.launch {
            settings.setOverride(packageName, transform(settings.overrideFor(packageName)))
        }
    }
}

class PerAppFeatureSettingsViewModelFactory(
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PerAppFeatureSettingsViewModel(
            packageName = packageName,
            settings = container.settings,
            keywordLibrary = container.keywordLibrary,
        ) as T
    }
}

/**
 * 每应用功能设置页(V37 T3.4 双域拆分):功能域总开关 + 六功能卡
 * (弹窗拦截 / 进程守护 / SSL 绕过 / 代理 VPN 隐藏 / Root·Xposed 隐藏 / 设备伪装),
 * 干预开关单击即写即推;弹窗卡内嵌关键词启用子集,伪装卡内嵌画像选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppFeatureSettingsScreen(
    viewModel: PerAppFeatureSettingsViewModel,
    onBack: () -> Unit,
    onOpenKeywordLibrary: () -> Unit,
) {
    val effective by viewModel.effective.collectAsStateWithLifecycle()
    val override by viewModel.override.collectAsStateWithLifecycle()
    val keywordEntries by viewModel.keywordEntries.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    val res = rememberLocaleAwareResources()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.feature_settings_entry),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.pagePadding),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                viewModel.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                stringResource(R.string.feature_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp),
            )

            SettingsSection(stringResource(R.string.feature_masters_section)) {
                ToggleListItem(
                    label = res.getString(R.string.listen_row_master_ui),
                    checked = effective.uiListen,
                    onCheckedChange = { viewModel.setUiMaster(it) },
                    testTag = "feature-row-master-ui",
                )
                HorizontalDivider16()
                ToggleListItem(
                    label = res.getString(R.string.network_monitor_master),
                    checked = effective.networkListen,
                    onCheckedChange = { viewModel.setNetworkMaster(it) },
                    testTag = "feature-row-master-network",
                )
                HorizontalDivider16()
                ToggleListItem(
                    label = res.getString(R.string.device_env_master),
                    checked = effective.deviceEnvListen,
                    onCheckedChange = { viewModel.setDeviceEnvMaster(it) },
                    testTag = "feature-row-master-device-env",
                )
                HorizontalDivider16()
                ToggleListItem(
                    label = res.getString(R.string.listen_row_master_process),
                    checked = effective.processControlListen,
                    onCheckedChange = { viewModel.setProcessControlMaster(it) },
                    testTag = "feature-row-master-process",
                )
            }
            Spacer(Modifier.height(Dimens.sectionGapDense))

            FeatureGroups.ALL.forEach { group ->
                SettingsSection(
                    title = stringResource(featureGroupLabelRes(group)),
                    modifier = Modifier.testTag("feature-group-header-${group.name}"),
                ) {
                    FeatureGroups.kindIds(group).forEachIndexed { index, kindId ->
                        if (index > 0) HorizontalDivider16()
                        ToggleListItem(
                            label = res.getString(KindLabelRes.resolve(kindId)),
                            checked = EffectiveSwitch.readKind(effective, kindId) == true,
                            onCheckedChange = { viewModel.setKind(kindId, it) },
                            testTag = "feature-row-$kindId",
                        )
                    }
                    when (group) {
                        FeatureGroup.DIALOG_INTERCEPT -> KeywordSubsetSection(
                            followGlobal = override.keywordFollowGlobal != false,
                            selectedIds = effective.feature.dialogKeywordRules
                                .mapNotNull { rule -> keywordEntries.firstOrNull { it.text == rule.text }?.id }
                                .toSet(),
                            entries = keywordEntries,
                            onFollowGlobalChange = { viewModel.setKeywordFollowGlobal(it) },
                            onToggleKeyword = { id, on -> viewModel.toggleKeyword(id, on) },
                            onOpenLibrary = onOpenKeywordLibrary,
                        )
                        FeatureGroup.DEVICE_SPOOF -> {
                            HorizontalDivider16()
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                headlineContent = {
                                    Text(
                                        res.getString(R.string.feature_profile_row),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        res.getString(R.string.feature_profile_row_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        res.getString(spoofProfileLabelRes(override.spoofProfileId)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("feature-profile-picker")
                                    .clickable { showProfileSheet = true },
                            )
                        }
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(Dimens.sectionGapDense))
            }

            if (!override.isDefault) {
                SettingsSection(stringResource(R.string.app_monitor_actions_section)) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        headlineContent = {
                            Text(
                                stringResource(R.string.listen_reset_action),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.listen_settings_custom_n,
                                    override.deviationCount,
                                    override.deviationCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.RestartAlt, null, tint = MaterialTheme.colorScheme.error)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feature-reset")
                            .clickable { confirmReset = true },
                    )
                }
                Spacer(Modifier.height(Dimens.sectionGapDense))
            }
            Spacer(Modifier.height(Dimens.pageBottom))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Filled.RestartAlt, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = { Text(stringResource(R.string.danger_reset_feature_msg, viewModel.packageName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    confirmReset = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showProfileSheet) {
        SpoofProfileSheet(
            selectedId = override.spoofProfileId,
            onSelect = { viewModel.setSpoofProfile(it) },
            onDismiss = { showProfileSheet = false },
        )
    }
}

/** 弹窗卡内嵌:关键词启用子集(跟随全局开关 + 库条目 chips)。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordSubsetSection(
    followGlobal: Boolean,
    selectedIds: Set<Long>,
    entries: List<KeywordEntry>,
    onFollowGlobalChange: (Boolean) -> Unit,
    onToggleKeyword: (Long, Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    HorizontalDivider16()
    ToggleListItem(
        label = stringResource(R.string.feature_keyword_follow_global),
        checked = followGlobal,
        onCheckedChange = onFollowGlobalChange,
        testTag = "feature-keyword-follow-global",
        supporting = stringResource(R.string.feature_keyword_follow_global_hint),
    )
    val enabledEntries = entries.filter { it.enabled }
    if (enabledEntries.isEmpty()) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            headlineContent = {
                Text(
                    stringResource(R.string.feature_keyword_library_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                TextButton(onClick = onOpenLibrary, modifier = Modifier.testTag("feature-keyword-goto-library")) {
                    Text(stringResource(R.string.feature_keyword_open_library))
                }
            },
        )
    } else {
        HorizontalDivider16()
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.feature_keyword_subset_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                enabledEntries.forEach { entry ->
                    FilterChip(
                        selected = followGlobal || selectedIds.contains(entry.id),
                        onClick = {
                            onToggleKeyword(entry.id, !(followGlobal || selectedIds.contains(entry.id)))
                        },
                        label = { Text(entry.text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("feature-keyword-chip-${entry.id}"),
                    )
                }
            }
        }
    }
}

/** 画像选择底部 sheet(跟随全局 + 内置档位单选;契约 4)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpoofProfileSheet(
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val res = rememberLocaleAwareResources()

    fun choose(id: String?) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onSelect(id)
            onDismiss()
        }
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.feature_profile_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val options = buildList {
                add(null as String?)
                addAll(SpoofProfiles.BUILTINS.map { it.id })
            }
            options.forEach { id ->
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    headlineContent = { Text(res.getString(spoofProfileLabelRes(id))) },
                    leadingContent = {
                        RadioButton(
                            selected = (selectedId ?: GLOBAL_FOLLOW_ID) == (id ?: GLOBAL_FOLLOW_ID),
                            onClick = { choose(id) },
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feature-profile-option-${id ?: "follow-global"}")
                        .clickable { choose(id) },
                )
            }
        }
    }
}

private const val GLOBAL_FOLLOW_ID = "__follow_global__"

/** 画像档位标签(auto 展示为「自动」;null 展示为「跟随全局」)。 */
internal fun spoofProfileLabelRes(profileId: String?): Int = when (profileId) {
    null -> R.string.feature_profile_follow_global
    SpoofProfiles.AUTO -> R.string.feature_profile_auto
    SpoofProfiles.PIXEL_7_PRO -> R.string.feature_profile_pixel7pro
    SpoofProfiles.XIAOMI_13 -> R.string.feature_profile_xiaomi13
    else -> R.string.feature_profile_auto
}

/** 功能组标题资源。 */
internal fun featureGroupLabelRes(group: FeatureGroup): Int = when (group) {
    FeatureGroup.DIALOG_INTERCEPT -> R.string.feature_group_dialog_intercept
    FeatureGroup.PROCESS_GUARD -> R.string.feature_group_process_guard
    FeatureGroup.SSL_BYPASS -> R.string.feature_group_ssl_bypass
    FeatureGroup.PROXY_VPN_HIDE -> R.string.feature_group_proxy_vpn_hide
    FeatureGroup.ROOT_XPOSED_HIDE -> R.string.feature_group_root_xposed_hide
    FeatureGroup.DEVICE_SPOOF -> R.string.feature_group_device_spoof
}

/** 功能组观测记录类型(入口页「查看记录」路由用;null = 无记录面)。 */
internal fun featureGroupRecordType(group: FeatureGroup): RecordType? = FeatureGroups.recordType(group)

/** 功能组启用类别数(入口页摘要「N/M 已启用」)。 */
internal fun featureGroupEnabledCount(effective: HookSwitch, group: FeatureGroup): Int =
    FeatureGroups.kindIds(group).count { EffectiveSwitch.readKind(effective, it) == true }

/** 功能组类别总数。 */
internal fun featureGroupTotalCount(group: FeatureGroup): Int = FeatureGroups.kindIds(group).size
