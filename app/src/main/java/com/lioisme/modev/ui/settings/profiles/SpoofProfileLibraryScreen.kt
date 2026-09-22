package com.lioisme.modev.ui.settings.profiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.FeatureConfig
import com.lioisme.modev.core.model.ProfileEnvelope
import com.lioisme.modev.core.model.ProfileImportOutcome
import com.lioisme.modev.core.model.SpoofProfiles
import com.lioisme.modev.domain.config.FeatureService
import com.lioisme.modev.domain.config.ListenConfigService
import com.lioisme.modev.domain.config.SpoofProfileService
import com.lioisme.modev.ui.apps.spoofProfileLabelRes
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.ModevConfirmDialog
import com.lioisme.modev.ui.components.ModevInputSheet
import com.lioisme.modev.ui.components.ModevSheet
import com.lioisme.modev.ui.components.ModevSheetButtonRow
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SheetContentPadding
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import com.lioisme.modev.ui.theme.Dimens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设备画像库页 VM(V47 WP-5)。
 *
 * 依赖面 = 三个 domain 契约(容器 public 属性恒为接口类型,AGENTS #18 G2 闸):
 * [SpoofProfileService] 画像库唯一写入口、[ListenConfigService] 绑定计数唯一读取源、
 * [FeatureService] 旧版全局档位与字段覆写。
 *
 * 后两者为何仍在页上:逐组接管(WP-3)完成之前,`spoofProfileId`(旧三档常量 id 空间)+
 * `spoofOverrides` 仍是既有 hooker 的取值通路,且与 MCP `spoof_global_set` 同一 id 空间 ——
 * 撤掉本段等于撤掉一条仍生效的用户能力,并让 UI 与工具面在同一个上游上分叉。
 */
class SpoofProfileLibraryViewModel(
    private val profiles: SpoofProfileService,
    private val settings: ListenConfigService,
    private val featureConfig: FeatureService,
) : ViewModel() {

    val entries: StateFlow<List<DeviceProfileEntry>> = profiles.profiles

    val feature: StateFlow<FeatureConfig> = featureConfig.globalFeature

    /** 画像 id → 绑定应用数(遍历已存在的每应用偏差,零新增查询、零 binder)。 */
    val boundCounts: StateFlow<Map<String, Int>> =
        combine(profiles.profiles, settings.overrides) { list, overrides ->
            val counts = LinkedHashMap<String, Int>()
            list.forEach { entry -> counts[entry.id] = 0 }
            overrides.values.forEach { override ->
                override.spoofProfileId?.let { id -> counts[id] = (counts[id] ?: 0) + 1 }
            }
            counts
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 首入即补内置档(幂等且守墓碑,账本语义见契约 KDoc)。 */
    fun synchronize() {
        viewModelScope.launch { profiles.synchronizeBuiltins() }
    }

    /** 复制为 DIY(内置档定制的标准路径)。 */
    fun duplicate(id: String, name: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(profiles.duplicate(id, name.trim()) != null) }
    }

    fun remove(id: String) {
        viewModelScope.launch { profiles.delete(id) }
    }

    // ---------------------------------------------------------------- 传输(WP-6)

    /** 导出全库为信封文本(与 MCP `spoof_profile_import` 同一格式,两侧零分叉)。 */
    fun exportAll(): String = profiles.exportJson()

    /** 导入预览:只校验不落库,逐条结论交回屏侧确认(与 [create] 同一回调形态)。 */
    fun previewImport(text: String, defaultName: String, onResult: (ProfileImportOutcome) -> Unit) {
        viewModelScope.launch { onResult(profiles.importJson(text, dryRun = true, defaultName = defaultName)) }
    }

    /** 导入落库(名称空白的条目回落 [defaultName] —— 数据层零用户可见字面量)。 */
    fun commitImport(text: String, defaultName: String, onResult: (ProfileImportOutcome) -> Unit) {
        viewModelScope.launch { onResult(profiles.importJson(text, dryRun = false, defaultName = defaultName)) }
    }

    /** 旧版全局档位(auto / 两个内置常量档,与 MCP `spoof_global_set` 同一 id 空间)。 */
    fun setProfile(id: String) {
        viewModelScope.launch {
            featureConfig.setGlobalFeature(feature.value.copy(spoofProfileId = id))
        }
    }

    fun setOverride(key: String, value: String) {
        val trimmedKey = key.trim()
        if (trimmedKey.isEmpty()) return
        viewModelScope.launch {
            featureConfig.setGlobalFeature(
                feature.value.copy(spoofOverrides = feature.value.spoofOverrides + (trimmedKey to value)),
            )
        }
    }

    fun removeOverride(key: String) {
        viewModelScope.launch {
            featureConfig.setGlobalFeature(
                feature.value.copy(spoofOverrides = feature.value.spoofOverrides - key),
            )
        }
    }
}

class SpoofProfileLibraryViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SpoofProfileLibraryViewModel(
            profiles = container.spoofProfiles,
            settings = container.listenConfig,
            featureConfig = container.featureConfig,
        ) as T
}

/**
 * 设备画像库页(V47 WP-5,取代 V37 的三常量单选页):
 * ①画像库列表 —— 内置行按 `builtinSeedId` 取四语展示名,DIY 行取存储名;摘要 = 已填参数数
 *   (+ 被绑定应用数);行动作恒收在**单一** trailing「更多」菜单:编辑 / 复制为 DIY / 删除;
 * ②旧版全局档位单选 + ③字段覆写清单(接管完成前仍是生效通路,原样搬迁不重设计)。
 *
 * 删除内置档按墓碑语义分文案;新建/复制走 [ModevInputSheet](契约 #4 输入即底部 sheet),
 * 危险清理走居中 [ModevConfirmDialog](契约 #27)。本页无搜索 ⇒ Appbar actions 仅「新建」一枚。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoofProfileLibraryScreen(
    viewModel: SpoofProfileLibraryViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onCreateProfile: (String) -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val boundCounts by viewModel.boundCounts.collectAsStateWithLifecycle()
    val feature by viewModel.feature.collectAsStateWithLifecycle()
    val res = rememberLocaleAwareResources()

    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var duplicateTarget by remember { mutableStateOf<DeviceProfileEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<DeviceProfileEntry?>(null) }
    var showOverrideSheet by remember { mutableStateOf(false) }
    var pendingDeleteKey by remember { mutableStateOf<String?>(null) }
    var exportText by remember { mutableStateOf<String?>(null) }
    /** 待确认的导入正文 + 其 dryRun 结论(两者同时非 null 才出确认框;确认前零写入)。 */
    var importSource by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<ProfileImportOutcome?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    fun snack(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = exportText
        exportText = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                }
            }.onSuccess { snack(res.getString(R.string.export_ok)) }
                .onFailure { snack(res.getString(R.string.export_failed)) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 读盘前先按 statSize 硬拒:字符闸在解析阶段,拦不住「先把整文件读进内存」这一步。
            val size = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            }.getOrDefault(-1L)
            if (size > ProfileEnvelope.MAX_IMPORT_BYTES) {
                snack(res.getString(R.string.profile_import_too_large))
                return@launch
            }
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                snack(res.getString(R.string.profile_import_failed))
                return@launch
            }
            viewModel.previewImport(text, res.getString(R.string.profile_import_default_name)) { outcome ->
                importSource = text
                importPreview = outcome
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.synchronize() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.profiles_title),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.profile_create_action),
                        onClick = { showCreateSheet = true },
                        modifier = Modifier.testTag("profiles-create"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.profile_create_action))
                    }
                    TipIconButton(
                        tooltip = stringResource(R.string.profile_export_action),
                        onClick = {
                            scope.launch {
                                exportText = withContext(Dispatchers.Default) { viewModel.exportAll() }
                                exportLauncher.launch("modev-profiles.json")
                            }
                        },
                        modifier = Modifier.testTag("profiles-export"),
                    ) {
                        Icon(
                            Icons.Filled.FileUpload,
                            contentDescription = stringResource(R.string.profile_export_action),
                        )
                    }
                    TipIconButton(
                        tooltip = stringResource(R.string.profile_import_action),
                        onClick = { scope.launch { importLauncher.launch(arrayOf("application/json", "text/plain")) } },
                        modifier = Modifier.testTag("profiles-import"),
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = stringResource(R.string.profile_import_action),
                        )
                    }
                },
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
                stringResource(R.string.profile_library_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            // V48 R-3 接管边界(AGENTS #30 的 UI 兑现):本页管编辑/校验/绑定,而靶程序当前
            // 仍读旧档 —— 不写这句,页面就会被读成「画像已经生效」。这是诚实呈现,不是免责声明。
            Text(
                stringResource(R.string.profile_takeover_status_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(start = 4.dp, bottom = 12.dp)
                    .testTag("profile-takeover-status"),
            )

            SettingsSection(
                title = stringResource(R.string.profile_library_section),
                modifier = Modifier.testTag("profiles-library-section"),
            ) {
                if (entries.isEmpty()) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        headlineContent = {
                            Text(
                                stringResource(R.string.profile_library_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.profile_library_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider16()
                        ProfileLibraryRow(
                            entry = entry,
                            displayName = profileEntryDisplayName(res, entry),
                            boundCount = boundCounts[entry.id] ?: 0,
                            onEdit = { onOpenProfile(entry.id) },
                            onDuplicate = { duplicateTarget = entry },
                            onDelete = { deleteTarget = entry },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.sectionGap))

            LegacyPresetSection(
                currentId = feature.spoofProfileId,
                onSelect = viewModel::setProfile,
            )
            Spacer(Modifier.height(Dimens.sectionGapDense))
            LegacyOverridesSection(
                overrides = feature.spoofOverrides,
                onAdd = { showOverrideSheet = true },
                onRemove = { pendingDeleteKey = it },
            )
            Spacer(Modifier.height(Dimens.pageBottom))
        }
    }

    if (showCreateSheet) {
        ModevInputSheet(
            title = stringResource(R.string.profile_create_action),
            label = stringResource(R.string.profile_name_label),
            initialValue = "",
            errorText = stringResource(R.string.profile_name_required),
            validate = { it.isNotBlank() },
            // 名称只是草稿标签:正文进编辑器填,首次保存才落库(库里不留空行)。
            onSubmit = { name ->
                showCreateSheet = false
                onCreateProfile(name.trim())
            },
            onDismiss = { showCreateSheet = false },
            modifier = Modifier.testTag("profile-create-sheet"),
            inputTestTag = "profile-create-input",
            confirmTestTag = "profile-create-confirm",
        )
    }

    duplicateTarget?.let { target ->
        ModevInputSheet(
            title = stringResource(R.string.profile_action_duplicate),
            label = stringResource(R.string.profile_name_label),
            initialValue = "${profileEntryDisplayName(res, target)} 2",
            errorText = stringResource(R.string.profile_name_required),
            validate = { it.isNotBlank() },
            onSubmit = { name ->
                viewModel.duplicate(target.id, name) { duplicateTarget = null }
            },
            onDismiss = { duplicateTarget = null },
            modifier = Modifier.testTag("profile-duplicate-sheet"),
            inputTestTag = "profile-duplicate-input",
            confirmTestTag = "profile-duplicate-confirm",
        )
    }

    deleteTarget?.let { target ->
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = if (target.isBuiltin) {
                res.getString(R.string.profile_delete_builtin_msg, profileEntryDisplayName(res, target))
            } else {
                res.getString(R.string.profile_delete_diy_msg, profileEntryDisplayName(res, target))
            },
            confirmTextRes = R.string.profile_action_delete,
            onConfirm = {
                viewModel.remove(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            confirmTestTag = "profile-delete-confirm",
        )
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

    /**
     * 导入确认(先 dryRun 逐条结论上屏,确认才落库):
     * 版本不匹配整包拒绝的形态必须与「可导入 N 条」分文案,否则用户读到的是矛盾的数。
     */
    importSource?.let { text ->
        importPreview?.let { preview ->
            ModevConfirmDialog(
                title = stringResource(R.string.profile_import_action),
                message = if (preview.schemaRejected) {
                    res.getString(R.string.profile_import_schema_rejected)
                } else {
                    res.getQuantityString(
                        R.plurals.profile_import_preview_msg,
                        preview.imported,
                        preview.imported,
                        preview.skipped,
                        preview.rejected,
                    )
                },
                confirmTextRes = R.string.profile_import_action,
                onConfirm = {
                    importSource = null
                    importPreview = null
                    viewModel.commitImport(text, res.getString(R.string.profile_import_default_name)) { outcome ->
                        snack(
                            res.getQuantityString(
                                R.plurals.profile_import_done,
                                outcome.imported,
                                outcome.imported,
                            ),
                        )
                    }
                },
                onDismiss = {
                    importSource = null
                    importPreview = null
                },
                confirmTestTag = "profile-import-confirm",
            )
        }
    }

    pendingDeleteKey?.let { key ->
        ModevConfirmDialog(
            title = stringResource(R.string.danger_title),
            message = stringResource(R.string.profiles_override_delete_confirm, key),
            onConfirm = {
                viewModel.removeOverride(key)
                pendingDeleteKey = null
            },
            onDismiss = { pendingDeleteKey = null },
        )
    }
}

/** 一行一画像:展示名 + 已填参数数 + 绑定应用数 + 内置徽章 + 单一「更多」菜单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileLibraryRow(
    entry: DeviceProfileEntry,
    displayName: String,
    boundCount: Int,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val res = rememberLocaleAwareResources()
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isBuiltin) {
                    Spacer(Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("profile-badge-builtin-${entry.id}"),
                    ) {
                        Text(
                            res.getString(
                                if (entry.managed) R.string.profile_badge_builtin
                                else R.string.profile_badge_customized,
                            ),
                        )
                    }
                }
            }
        },
        supportingContent = {
            Column {
                Text(
                    pluralStringResource(R.plurals.profile_summary_filled, entry.filledCount, entry.filledCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (boundCount > 0) {
                    Text(
                        pluralStringResource(R.plurals.profile_summary_bound, boundCount, boundCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                Icons.Filled.PhoneAndroid,
                null,
                tint = if (entry.isBuiltin) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            // Box 锚定:菜单挂在「更多」按钮处(与令牌页 V46 形态一致)。
            Box {
                TipIconButton(
                    tooltip = stringResource(R.string.profile_action_more),
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("profile-more-${entry.id}"),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.profile_action_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(res.getString(R.string.profile_action_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                        modifier = Modifier.testTag("profile-menu-edit-${entry.id}"),
                    )
                    DropdownMenuItem(
                        text = { Text(res.getString(R.string.profile_action_duplicate)) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        },
                        modifier = Modifier.testTag("profile-menu-duplicate-${entry.id}"),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(res.getString(R.string.profile_action_delete), color = MaterialTheme.colorScheme.error)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("profile-menu-delete-${entry.id}"),
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-row-${entry.id}")
            .clickable(onClick = onEdit),
    )
}

/** 旧版全局档位单选(逐组接管完成前仍被既有钩子消费)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyPresetSection(currentId: String, onSelect: (String) -> Unit) {
    val res = rememberLocaleAwareResources()
    SettingsSection(
        title = stringResource(R.string.profiles_global_section),
        modifier = Modifier.testTag("profiles-legacy-preset"),
    ) {
        Text(
            stringResource(R.string.profiles_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
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
                leadingContent = {
                    RadioButton(
                        selected = currentId == profile.id,
                        onClick = { onSelect(profile.id) },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-option-${profile.id}")
                    .clickable { onSelect(profile.id) },
            )
        }
    }
}

/** 旧版字段覆写清单(键值通路,与全局档位同属接管前的生效面)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyOverridesSection(
    overrides: Map<String, String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val res = rememberLocaleAwareResources()
    SettingsSection(title = stringResource(R.string.profiles_overrides_section)) {
        if (overrides.isEmpty()) {
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
            overrides.entries.sortedBy { it.key }.forEachIndexed { index, (key, value) ->
                if (index > 0) HorizontalDivider16()
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    headlineContent = { Text(key, style = MaterialTheme.typography.bodyMedium) },
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
                            onClick = { onRemove(key) },
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
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile-override-add")
                .clickable(onClick = onAdd),
        )
    }
}

/** 覆写条目编辑 sheet(自 V37 页原样搬迁;契约 #4 输入即底部 sheet)。 */
@Composable
private fun OverrideEditSheet(
    existingKeys: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    ModevSheet(
        title = stringResource(R.string.profiles_override_add),
        onDismiss = onDismiss,
    ) { sheet ->
        Column(Modifier.fillMaxWidth().padding(SheetContentPadding)) {
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
            ModevSheetButtonRow(
                onConfirm = {
                    val trimmed = key.trim()
                    if (trimmed.isNotEmpty()) {
                        sheet.dismissWith { onConfirm(trimmed, value.trim()) }
                    }
                },
                onCancel = onDismiss,
                confirmTestTag = "profile-override-confirm",
            )
        }
    }
}
