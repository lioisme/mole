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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.CapabilityDomain
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.FeatureGroup
import com.lioisme.modev.core.model.FeatureGroups
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.KeywordEntry
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileFieldDeviation
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.SpoofProfiles
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.core.model.withText
import com.lioisme.modev.domain.asset.KeywordLibraryService
import com.lioisme.modev.domain.config.ListenConfigService
import com.lioisme.modev.domain.config.SpoofProfileService
import com.lioisme.modev.ui.components.ChoiceOption
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.ModevChoiceSheet
import com.lioisme.modev.ui.components.ModevConfirmDialog
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import com.lioisme.modev.ui.settings.profiles.ProfileBindingDisplay
import com.lioisme.modev.ui.settings.profiles.ProfileEditorState
import com.lioisme.modev.ui.settings.profiles.ProfileEditorView
import com.lioisme.modev.ui.settings.profiles.profileDisplayNameFor
import com.lioisme.modev.ui.settings.profiles.toBindingDisplay
import com.lioisme.modev.ui.settings.profiles.toView
import com.lioisme.modev.ui.theme.Dimens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 单应用功能设置状态(V37 T3.4;V47 WP-5 画像行改绑画像库):
 * 功能域干预开关 + 关键词启用子集 + 画像绑定;
 * 与监听页共享同一偏差存储(override_{pkg}),各写各的字段互不覆盖。
 *
 * 画像绑定恒经 [SpoofProfileService.resolve] 的**三态**结论呈现:旧形态按三个编译期常量
 * 反查标签,任何 DIY 画像 id 都会被吞成「自动」—— 用户在界面上选了档而伪装不发生
 * 且零提示(GAP-02),本行把它显式化。
 */
class PerAppFeatureSettingsViewModel(
    val packageName: String,
    private val settings: ListenConfigService,
    private val keywordLibrary: KeywordLibraryService,
    private val profiles: SpoofProfileService,
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

    /** 画像库快照(绑定 sheet 的唯一选项源,禁再列编译期常量档)。 */
    val profileLibrary: StateFlow<List<DeviceProfileEntry>> = profiles.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本应用画像绑定三态(未绑定 / 已绑定 / 绑了但库里没有)。 */
    val binding: StateFlow<ProfileBindingDisplay> =
        combine(settings.overrides, profiles.profiles) { map, _ ->
            profiles.resolve(map[packageName]?.spoofProfileId).toBindingDisplay()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            profiles.resolve(settings.overrideFor(packageName).spoofProfileId).toBindingDisplay(),
        )

    fun setUiMaster(value: Boolean) = update { it.copy(uiListen = value) }
    fun setNetworkMaster(value: Boolean) = update { it.copy(networkListen = value) }
    fun setDeviceEnvMaster(value: Boolean) = update { it.copy(deviceEnvListen = value) }
    fun setProcessControlMaster(value: Boolean) = update { it.copy(processControlListen = value) }

    /** 类别定制(V44 限定键;类型 = 组观测记录类型,读写经 taxonomy (type,id) 消歧)。 */
    fun setKind(type: RecordType, kindId: String, value: Boolean) {
        update { it.copy(kinds = it.kinds + (AppListenOverride.kindKey(type, kindId) to value)) }
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

    /** 画像绑定(null = 不绑定、跟随全局档);写入即经 setOverride 定向推送。 */
    fun setSpoofProfile(profileId: String?) {
        update { it.copy(spoofProfileId = profileId) }
    }

    // ---- V49 R-2 每应用画像微调(逐项可定制,不必为改一项而编辑整张画像) ----

    /**
     * 本应用**生效画像**的表单视图(所绑画像 ⊕ 本应用覆写,由仓库 `expandProfile` 单点展开)。
     *
     * 读的是 `settings.effectiveSwitchFlow` 而不是本地 `effective`:后者是
     * `EffectiveSwitch.resolve` 的直接组合,**不经画像库展开** —— 用它画屏会出现
     * 「屏上是画像正文、下发的是合并结果」的两套值。
     */
    val tunedView: StateFlow<ProfileEditorView> =
        settings.effectiveSwitchFlow(packageName)
            .map { ProfileEditorState(initial = it.feature.deviceProfile).toView() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                ProfileEditorState(
                    initial = settings.effectiveSwitch(packageName).feature.deviceProfile,
                ).toView(),
            )

    /** 本应用覆写了哪几项(呈现「已定制 N 项」与逐行的还原按钮锚点)。 */
    val tunedOverrides: StateFlow<Map<String, String>> = override
        .map { it.profileOverrides.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 所绑画像正文(未绑定 = 空正文 ⇒ 覆写就是全部值)。同步读,供写侧反推覆写集。 */
    private fun boundProfile(): DeviceProfile =
        profiles.resolve(settings.overrideFor(packageName).spoofProfileId).profileOrEmpty()

    /**
     * 所绑画像正文的流形态(V49 R-2 微调区要用它点名「画像原值」)。
     *
     * 与 [tunedView] 是两件事:生效快照 = 画像 ⊕ 覆写(屏上那行大字),画像正文 = 未叠加覆写的
     * 原值(行下那行小字)。合成一个就把「你改过」这个信息抹掉了。
     */
    val boundProfileFlow: StateFlow<DeviceProfile> =
        combine(settings.overrides, profiles.profiles) { map, _ ->
            profiles.resolve(map[packageName]?.spoofProfileId).profileOrEmpty()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            boundProfile(),
        )

    /**
     * 写一项本应用覆写:合并到生效快照 → 反推稀疏覆写集 → 即写即推。
     *
     * 非法值在**写侧**就拒(信任边界):弹层的实时校验只是提示,`validate = { true }` 让提交
     * 可达,所以这里不挡就会落一条「存了但下发时被丢掉」的幽灵偏差 —— 屏上显示已定制、
     * 靶程序读到的却是画像值,正是本仓最忌的静默分叉。
     */
    fun setProfileField(field: ProfileField, raw: String) {
        if (ProfileFieldCatalog.validate(field, raw) != null) return
        val bound = boundProfile()
        val merged = field.withText(
            settings.effectiveSwitch(packageName).feature.deviceProfile,
            raw,
        )
        val texts = ProfileField.entries.associateWith { it.stringOf(merged) }
        val deviations = ProfileFieldDeviation.between(bound, texts)
        update { it.copy(profileOverrides = deviations) }
    }

    /** 还原一项 = 删掉这条覆写,交回所绑画像(与「清除(写空)」是两件事)。 */
    fun revertProfileField(field: ProfileField) {
        update { current ->
            val next = current.profileOverrides?.minus(ProfileFieldCatalog.jsonKeyOf(field))
            current.copy(profileOverrides = next?.ifEmpty { null })
        }
    }

    /** 就近随机一项(仅目录 RANDOMIZABLE 内生效);随机结果同样经 [setProfileField] 落覆写。 */
    fun randomizeProfileField(field: ProfileField) {
        val seeded = ProfileEditorState(
            initial = settings.effectiveSwitch(packageName).feature.deviceProfile,
        )
        if (seeded.randomizeField(field)) setProfileField(field, seeded.textOf(field))
    }

    /**
     * 一键恢复默认配置(V45 T3.5 / DUAL-12:**只清功能域**)。
     * 旧形态走全量删键,在功能页点恢复会连带抹掉监听页定制。
     */
    fun reset() {
        viewModelScope.launch { settings.resetOverride(packageName, CapabilityDomain.FEATURE) }
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
            settings = container.listenConfig,
            keywordLibrary = container.keywordLibrary,
            profiles = container.spoofProfiles,
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
    onOpenProfileTune: () -> Unit = {},
    onOpenKeywordLibrary: () -> Unit,
) {
    val effective by viewModel.effective.collectAsStateWithLifecycle()
    val override by viewModel.override.collectAsStateWithLifecycle()
    val keywordEntries by viewModel.keywordEntries.collectAsStateWithLifecycle()
    val profileLibrary by viewModel.profileLibrary.collectAsStateWithLifecycle()
    val binding by viewModel.binding.collectAsStateWithLifecycle()
    val tunedOverrides by viewModel.tunedOverrides.collectAsStateWithLifecycle()
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
                // V48 R-2:标签与现值一律经单点解析(KindLabelRes.masterLabelRes / HookSwitch.masterFor)
                // —— 监听页的跨域闸提示要报出**同一个名字**,这里再各写一份字面量就会各说各话。
                ToggleListItem(
                    label = res.getString(KindLabelRes.masterLabelRes(RecordType.UI_INTERACTION)),
                    checked = effective.masterFor(RecordType.UI_INTERACTION),
                    onCheckedChange = { viewModel.setUiMaster(it) },
                    testTag = "feature-row-master-ui",
                )
                HorizontalDivider16()
                ToggleListItem(
                    label = res.getString(KindLabelRes.masterLabelRes(RecordType.NETWORK)),
                    checked = effective.masterFor(RecordType.NETWORK),
                    onCheckedChange = { viewModel.setNetworkMaster(it) },
                    testTag = "feature-row-master-network",
                )
                HorizontalDivider16()
                ToggleListItem(
                    label = res.getString(KindLabelRes.masterLabelRes(RecordType.DEVICE_ENV)),
                    checked = effective.masterFor(RecordType.DEVICE_ENV),
                    onCheckedChange = { viewModel.setDeviceEnvMaster(it) },
                    testTag = "feature-row-master-device-env",
                )
                HorizontalDivider16()
                ToggleListItem(
                    label = res.getString(KindLabelRes.masterLabelRes(RecordType.PROCESS_CONTROL)),
                    checked = effective.masterFor(RecordType.PROCESS_CONTROL),
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
                    FeatureGroups.kindRefs(group).forEachIndexed { index, (kindType, kindId) ->
                        if (index > 0) HorizontalDivider16()
                        ToggleListItem(
                            label = res.getString(KindLabelRes.resolve(kindType, kindId)),
                            checked = EffectiveSwitch.readKind(effective, kindType, kindId) == true,
                            onCheckedChange = { viewModel.setKind(kindType, kindId, it) },
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
                            val missing = binding is ProfileBindingDisplay.Missing
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
                                    // 缺失态恒点名被绑定的 id:静默按「不伪装」处理正是旧三档
                                    // 常量表把未知 id 吞成 auto 的那条洞(GAP-02)。
                                    Text(
                                        text = when (val current = binding) {
                                            is ProfileBindingDisplay.Missing -> current.id
                                            ProfileBindingDisplay.Unbound ->
                                                res.getString(R.string.feature_profile_row_hint)

                                            is ProfileBindingDisplay.Bound -> pluralStringResource(
                                                R.plurals.profile_summary_filled,
                                                current.filledCount,
                                                current.filledCount,
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (missing) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.testTag("feature-profile-supporting"),
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        text = when (val current = binding) {
                                            is ProfileBindingDisplay.Bound ->
                                                profileDisplayNameFor(res, current.name, current.builtinSeedId)

                                            is ProfileBindingDisplay.Missing ->
                                                res.getString(R.string.profile_binding_missing)

                                            ProfileBindingDisplay.Unbound ->
                                                res.getString(R.string.feature_profile_follow_global)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (missing) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("feature-profile-binding"),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("feature-profile-picker")
                                    .clickable { showProfileSheet = true },
                            )
                            // V49 R-2:绑定只是「选一张档」;要改其中一项必须来这里,
                            // 否则唯一的通路就是编辑整张共享画像(改一档波及所有绑它的包)。
                            HorizontalDivider16()
                            ListItem(
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                headlineContent = {
                                    Text(
                                        res.getString(R.string.profile_tune_row),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = if (tunedOverrides.isEmpty()) {
                                            res.getString(R.string.profile_tune_row_hint)
                                        } else {
                                            res.getQuantityString(
                                                R.plurals.profile_tune_hint_n,
                                                tunedOverrides.size,
                                                tunedOverrides.size,
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("feature-profile-tune-supporting"),
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        text = Integer.toString(tunedOverrides.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("feature-profile-tune-count"),
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("feature-profile-tune")
                                    .clickable(onClick = onOpenProfileTune),
                            )
                        }
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(Dimens.sectionGapDense))
            }

            // V45 T3.5 / DUAL-12:恢复默认行按本域偏差出现(旧形态读跨域总数)
            if (override.deviationCountFor(CapabilityDomain.FEATURE) > 0) {
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
                            // V45 T3.5:本页只数功能域偏差
                            val n = override.deviationCountFor(CapabilityDomain.FEATURE)
                            Text(
                                pluralStringResource(R.plurals.listen_settings_custom_n, n, n),
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
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(R.string.danger_reset_feature_msg, viewModel.packageName),
            onConfirm = {
                viewModel.reset()
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }

    if (showProfileSheet) {
        SpoofProfileSheet(
            selectedId = override.spoofProfileId,
            entries = profileLibrary,
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

/**
 * 画像绑定底部 sheet(V47 WP-5:选项源改画像库,取代旧三常量档)。
 *
 * 内置行按 seedId 取四语展示名,DIY 行取存储名(与画像库页同一实现);
 * 当前绑定了库里已不存在的 id 时**恒无选项命中**(不得回落成某一档),
 * 缺失语义由本应用页的画像行红字承载。
 */
@Composable
private fun SpoofProfileSheet(
    selectedId: String?,
    entries: List<DeviceProfileEntry>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val res = rememberLocaleAwareResources()
    val options = buildList {
        add(
            ChoiceOption<String?>(
                value = null,
                label = res.getString(R.string.feature_profile_follow_global),
                testTag = "feature-profile-option-follow-global",
            ),
        )
        addAll(
            entries.map { entry ->
                ChoiceOption<String?>(
                    value = entry.id,
                    label = profileDisplayNameFor(res, entry.name, entry.builtinSeedId),
                    testTag = "feature-profile-option-${entry.id}",
                )
            },
        )
    }
    ModevChoiceSheet(
        title = stringResource(R.string.feature_profile_sheet_title),
        options = options,
        selected = selectedId,
        onSelect = { id ->
            onSelect(id)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/** 画像档位标签(仅旧三常量档,供画像页的接管前配置面使用;DIY 与内置库行走展示名解析)。 */
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
    FeatureGroups.kindRefs(group).count { (type, id) -> EffectiveSwitch.readKind(effective, type, id) == true }

/** 功能组类别总数。 */
internal fun featureGroupTotalCount(group: FeatureGroup): Int = FeatureGroups.kindRefs(group).size
