package com.lioisme.modev.ui.settings.profiles

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.domain.config.DeviceCatalogService
import com.lioisme.modev.core.model.DeviceProfile
import com.lioisme.modev.core.model.DeviceBrandEntry
import com.lioisme.modev.core.model.DeviceProfileEntry
import com.lioisme.modev.core.model.ProfileConflict
import com.lioisme.modev.core.model.ProfileField
import com.lioisme.modev.core.model.ProfileFieldCatalog
import com.lioisme.modev.core.model.ProfileSection
import com.lioisme.modev.core.model.ProfileKind
import com.lioisme.modev.core.model.ProfileValueType
import com.lioisme.modev.core.model.stringOf
import com.lioisme.modev.core.model.applyBrandIdentity
import com.lioisme.modev.core.model.applyDevicePreset
import com.lioisme.modev.domain.config.SpoofProfileService
import com.lioisme.modev.ui.apps.ToggleListItem
import com.lioisme.modev.ui.components.ChoiceOption
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.ModevChoiceSheet
import com.lioisme.modev.ui.components.ModevConfirmDialog
import com.lioisme.modev.ui.components.ModevInputSheet
import com.lioisme.modev.ui.components.ModevSearchableChoiceSheet
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SHEET_MAX_VISIBLE_ROWS
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.TipIconButton
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import com.lioisme.modev.ui.theme.Dimens
import com.lioisme.modev.ui.theme.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 画像参数编辑器 VM(V47 WP-5)。
 *
 * 状态本体 = [ProfileEditorState](纯 Kotlin、可注入随机源 ⇒ JVM 直测);VM 只做两件事:
 *  1. 每次变更后把表单投影成一帧 [ProfileEditorView] 快照并推送 —— 组合侧以**值**为参数,
 *     刷新点即数据变化点(41 个 `MutableState` 的散点形态会让「改一处、忘刷一处」成为常态);
 *  2. 保存 = 经 [SpoofProfileService] 唯一写入口
 *     (UI 永不直写 prefs、永不自造第二条持久化通路)。
 *
 * **草稿态**([draftName] 非 null):库里没有对应行,首次保存经 [SpoofProfileService.create]
 * 落行并**就地升格**为主键(第二次保存起走 update)。新建不走「先落一行空画像」是因为
 * 契约拒绝零字段条目,且用户中途放弃不得在库里留下一行装不了任何钩子的垃圾。
 *
 * 改名与改摘要**不在本屏职责内**(画像库行才是身份编辑处);本屏只改正文。
 */
class ProfileEditorViewModel(
    profileId: String,
    private val draftName: String?,
    private val profiles: SpoofProfileService,
    private val deviceCatalog: DeviceCatalogService,
    randomProvider: () -> kotlin.random.Random = { kotlin.random.Random.Default },
) : ViewModel() {

    /** 当前落点主键;null = 仍是草稿(尚未创建)。 */
    private var targetId: String? = if (draftName != null) null else profileId

    /** 被编辑条目;null = 草稿,或库里已无此行(已删 ⇒ 屏侧点名,不静默新建覆盖)。 */
    val entry: DeviceProfileEntry? get() = profiles.find(targetId)

    /** 展示名原始串(内置行为空串 ⇒ 屏侧按 seedId 取四语资源)。 */
    val rawName: String get() = entry?.name ?: draftName.orEmpty()

    /** 仍处草稿态(保存 = 新建)。 */
    val isDraft: Boolean get() = targetId == null

    private val state = ProfileEditorState(
        initial = entry?.profile ?: DeviceProfile.EMPTY,
        randomProvider = randomProvider,
    )

    private val _view = MutableStateFlow(state.toView())

    val view: StateFlow<ProfileEditorView> = _view

    private val _saveEvents = MutableStateFlow(0)

    /** 保存事件序号(每次保存 +1);与 [_lastSaveResult] 配对驱动 Snackbar。 */
    val saveEvents: StateFlow<Int> = _saveEvents

    private val _lastSaveResult = MutableStateFlow<ProfileSaveResult?>(null)

    val lastSaveResult: StateFlow<ProfileSaveResult?> = _lastSaveResult

    // -------------------------------------------------------------- 机型库(V47 WP-5b)

    private val _catalog = MutableStateFlow<DeviceCatalogState>(DeviceCatalogState.Idle)

    /** 机型库加载态:未加载 / 加载中 / 不可用 / 可用必须可区分(零静默红线)。 */
    val catalog: StateFlow<DeviceCatalogState> = _catalog

    init {
        // 预取:选择器点开即得,不做「第一次点只触发加载、第二次才开列」的两段式;
        // 解析只在进程内发生一次(仓储带缓存),代价不换语义。
        loadCatalog()
    }

    private fun loadCatalog() {
        _catalog.value = DeviceCatalogState.Loading
        viewModelScope.launch {
            val brands = runCatching { deviceCatalog.catalog()?.brands.orEmpty() }.getOrNull().orEmpty()
            _catalog.value = if (brands.isEmpty()) DeviceCatalogState.Unavailable
            else DeviceCatalogState.Ready(brands)
        }
    }

    /**
     * 选型号:一次写全 MODEL / DEVICE / BRAND / MANUFACTURER。
     *
     * **PRODUCT / BOARD / HARDWARE 恒不动** —— 内置机型库(MobileModels)不提供这三个值,
     * 按 MODEL 或 DEVICE 猜 PRODUCT 会造出自相矛盾的画像(基线 `DevicePreset` 同一裁决)。
     * 猜不出来就留空,让用户自己填,这比编一个像真的假值诚实。
     */
    fun pickDevice(brandKey: String, picked: String) {
        val brands = (_catalog.value as? DeviceCatalogState.Ready)?.brands ?: return
        val brand = brands.firstOrNull { it.key == brandKey } ?: return
        val next = applyBrandIdentity(applyDevicePreset(state.snapshot(), picked), brand)
        state.applyValues(
            mapOf(
                ProfileField.MODEL to ProfileField.MODEL.stringOf(next),
                ProfileField.DEVICE to ProfileField.DEVICE.stringOf(next),
                ProfileField.BRAND to ProfileField.BRAND.stringOf(next),
                ProfileField.MANUFACTURER to ProfileField.MANUFACTURER.stringOf(next),
            ),
        )
        bump()
    }

    fun update(field: ProfileField, raw: String) {
        state.update(field, raw)
        bump()
    }

    fun clear(field: ProfileField) {
        state.clear(field)
        bump()
    }

    fun clearAll() {
        state.clearAll()
        bump()
    }

    fun writeBool(field: ProfileField, value: Boolean) {
        state.writeBool(field, value)
        bump()
    }

    fun writeSlot(field: ProfileField, slot: Int) {
        state.writeSlot(field, slot)
        bump()
    }

    fun randomizeField(field: ProfileField) {
        if (state.randomizeField(field)) bump()
    }

    fun randomizeAll() {
        if (state.randomizeAll() > 0) bump()
    }

    /**
     * 保存:草稿走 create(成功后记住主键),已落行走 update(名称与摘要维持库里现值)。
     * 两条路都拒绝「条目已不在库里」的静默覆盖 ⇒ [ProfileSaveResult.Rejected]。
     */
    fun save() {
        viewModelScope.launch {
            val snapshot = state.snapshot()
            val id = targetId
            val saved = if (id == null) {
                profiles.create(draftName.orEmpty(), "", snapshot)
            } else {
                val current = profiles.find(id)
                if (current != null &&
                    profiles.update(id, current.name, current.summary, snapshot)
                ) {
                    current
                } else {
                    null
                }
            }
            if (saved != null) {
                targetId = saved.id
                state.commit()
            }
            _lastSaveResult.value = if (saved != null) ProfileSaveResult.Saved else ProfileSaveResult.Rejected
            _saveEvents.value += 1
            bump()
        }
    }

    private fun bump() {
        _view.value = state.toView()
    }
}

class ProfileEditorViewModelFactory(
    private val profileId: String,
    private val draftName: String?,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProfileEditorViewModel(
            profileId = profileId,
            draftName = draftName,
            profiles = container.spoofProfiles,
            deviceCatalog = container.deviceCatalog,
        ) as T
}

/** 机型库加载态(V47 WP-5b;Unavailable 与 Idle 必须分文案,前者是数据缺失不是未开始)。 */
sealed interface DeviceCatalogState {
    data object Idle : DeviceCatalogState
    data object Loading : DeviceCatalogState

    /** 资产读不到或解析失败(选择行必须点名,不得退化成「库里没机型」)。 */
    data object Unavailable : DeviceCatalogState

    data class Ready(val brands: List<DeviceBrandEntry>) : DeviceCatalogState
}

/**
 * 画像参数编辑器(V47 WP-5 的核心):**41 项参数按取值形态落控件,而非一面文本输入框**。
 *
 *  - 顶部设备身份预览卡:brand / manufacturer / model / product / device 实况呈现,并把
 *    fingerprint 的 `brand/product/device` 前缀与三元组对账成三色标记(一致 / 不一致 /
 *    尚不可判)—— 自相矛盾的画像会被真实反作弊直接冻结设备,这是整屏最有用的视觉信号;
 *  - 实时生效概览:已填参数数 + 将安装的干预组 chips + 配置矛盾内联提示(非阻断弹窗);
 *  - 逐分区一行一参数,当前值随行呈现(未填显式「未设置」);点击按 [ProfileValueType] 分派:
 *    TEXT / INT / DOUBLE → [ModevInputSheet](输入期实时校验错误)、
 *    BOOL → 行内 Switch(零弹层)、枚举槽 → [ModevChoiceSheet]
 *    (槽集由 `ProfileFieldCatalog.validate` 反推,与 MCP 目录工具同一手法 ⇒ 两侧不可能分叉);
 *  - RANDOMIZABLE 参数行带就近随机;整组随机 = 同品牌世代同运营商同源派生;
 *  - 保存闸 = `isDirty() && canSave`,非法输入由 sheet 实时硬闸在前、保存闸在后。
 *
 * 本页无搜索 ⇒ Appbar actions 合法(排他规范不触发);「从真机读取当前值」不在本包范围。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    viewModel: ProfileEditorViewModel,
    onBack: () -> Unit,
) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    val saveEvents by viewModel.saveEvents.collectAsStateWithLifecycle()
    val lastSaveResult by viewModel.lastSaveResult.collectAsStateWithLifecycle()
    val res = rememberLocaleAwareResources()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingField by remember { mutableStateOf<ProfileField?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    var showBrandSheet by remember { mutableStateOf(false) }
    var pickBrandKey by remember { mutableStateOf<String?>(null) }

    val entry = viewModel.entry
    if (entry == null && !viewModel.isDraft) {
        // 编辑中的条目已从库里消失:无正文可编,交回上一页(禁以空画像继续编辑并覆盖式保存)。
        LaunchedEffect(Unit) { onBack() }
        return
    }

    LaunchedEffect(saveEvents) {
        if (saveEvents > 0) {
            snackbarHostState.showSnackbar(
                res.getString(profileSaveMessage(requireNotNull(lastSaveResult))),
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.profile_editor_title),
                onBack = onBack,
                actions = {
                    TipIconButton(
                        tooltip = stringResource(R.string.profile_randomize_all),
                        onClick = { viewModel.randomizeAll() },
                        modifier = Modifier.testTag("profile-randomize-all"),
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = stringResource(R.string.profile_randomize_all),
                        )
                    }
                    TipIconButton(
                        tooltip = stringResource(R.string.profile_clear_all),
                        onClick = { confirmClearAll = true },
                        modifier = Modifier.testTag("profile-clear-all"),
                    ) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.profile_clear_all),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        bottomBar = {
            val filled = view.filledCount
            ProfileSaveBar(
                leading = view.firstError?.let { (field, error) ->
                    res.getString(
                        R.string.profile_invalid_field,
                        res.getString(profileFieldLabelRes(field)),
                        res.getString(profileFieldErrorRes(error)),
                    )
                } ?: if (filled == 0) {
                    // 全空屏该说「下一步可以做什么」,而不是重复报一个 0。
                    stringResource(R.string.profile_randomize_all_hint)
                } else {
                    pluralStringResource(R.plurals.profile_summary_filled, filled, filled)
                },
                invalid = view.firstError != null,
                canSave = view.canSave,
                onSave = { viewModel.save() },
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
            if (entry?.isBuiltin == true) {
                BuiltinEditingHint()
                Spacer(Modifier.height(Dimens.sectionGapDense))
            }
            ProfileIdentityCard(preview = view.identity)
            Spacer(Modifier.height(Dimens.sectionGapDense))
            ProfileCoverageCard(
                filledCount = view.filledCount,
                groups = view.groups,
                conflicts = view.conflicts,
            )
            Spacer(Modifier.height(Dimens.sectionGap))

            view.rowsBySection.forEach { (section, fields) ->
                SettingsSection(
                    title = stringResource(profileSectionLabelRes(section)),
                    modifier = Modifier.testTag("profile-section-${section.name}"),
                ) {
                    if (section == ProfileSection.DEVICE) {
                        // 机型库选择行恒在设备分区首行:它替掉的是「手打四个互相牵连的字段」,
                        // 放在字段列表中间会被读成又一个字段,放前面才读成「先选设备,再修细节」。
                        DevicePresetRow(
                            state = catalog,
                            onPick = { showBrandSheet = true },
                        )
                        HorizontalDivider16()
                    }
                    fields.forEachIndexed { index, field ->
                        if (index > 0) HorizontalDivider16()
                        ProfileFieldRow(
                            spec = ProfileFieldSpec.of(res, field, view),
                            onOpen = { editingField = field },
                            onToggleBool = { value -> viewModel.writeBool(field, value) },
                            onClear = { viewModel.clear(field) },
                            onRandomize = { viewModel.randomizeField(field) },
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.sectionGap))
            }
            Spacer(Modifier.height(Dimens.pageBottom))
        }
    }

    editingField?.let { field ->
        when (field.type) {
            // 布尔项 = 行内 Switch,无弹层(契约 #20 开关行形态;置 false 即取消该项干预)。
            ProfileValueType.BOOL -> Unit

            ProfileValueType.NETWORK_TYPE, ProfileValueType.SCREENSHOT_MODE -> ModevChoiceSheet(
                title = res.getString(profileFieldLabelRes(field)),
                options = ProfileEditorState.acceptedSlots(field).map { slot ->
                    ChoiceOption(
                        value = slot,
                        label = slotLabel(res, field, slot),
                        testTag = "profile-slot-${field.name}-$slot",
                    )
                },
                selected = view.texts[field]?.toIntOrNull()
                    ?: ProfileEditorState.unhookSlotOf(field),
                onSelect = { slot ->
                    viewModel.writeSlot(field, slot)
                    editingField = null
                },
                onDismiss = { editingField = null },
                modifier = Modifier.testTag("profile-choice-${field.name}"),
            )

            else -> ModevInputSheet(
                title = res.getString(profileFieldLabelRes(field)),
                label = res.getString(profileFieldLabelRes(field)),
                initialValue = view.texts[field].orEmpty(),
                hint = fieldHint(res, field),
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (field.type) {
                        ProfileValueType.INT -> KeyboardType.Number
                        ProfileValueType.DOUBLE -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    },
                ),
                digitOnly = field.type == ProfileValueType.INT,
                maxLength = maxInputLengthOf(field),
                liveError = { text -> liveFieldError(res, field, text) },
                validate = { true },
                onSubmit = { text ->
                    viewModel.update(field, text)
                    editingField = null
                },
                onDismiss = { editingField = null },
                modifier = Modifier.testTag("profile-input-${field.name}"),
                inputTestTag = "profile-input-field",
                confirmTestTag = "profile-input-confirm",
            )
        }
    }

    // 机型库两级选择:品牌 → 型号(同屏恒一个 sheet,型号表随品牌换)。
    val readyBrands = (catalog as? DeviceCatalogState.Ready)?.brands.orEmpty()
    val pickedBrand = readyBrands.firstOrNull { it.key == pickBrandKey }
    if (pickedBrand != null) {
        ModevSearchableChoiceSheet(
            title = pickedBrand.displayName,
            options = pickedBrand.models.map { model ->
                ChoiceOption(
                    value = "${model.model}:${model.resolvedDevice()}",
                    label = model.label(),
                    testTag = "profile-device-model-${model.model}",
                )
            },
            selected = null,
            placeholder = stringResource(R.string.profile_device_search_hint),
            emptyText = stringResource(R.string.profile_device_no_match),
            truncatedText = { total ->
                res.getString(R.string.profile_device_truncated, SHEET_MAX_VISIBLE_ROWS, total)
            },
            onSelect = { picked ->
                viewModel.pickDevice(pickedBrand.key, picked)
                pickBrandKey = null
            },
            onDismiss = { pickBrandKey = null },
            modifier = Modifier.testTag("profile-device-model-sheet"),
        )
    } else if (showBrandSheet) {
        ModevSearchableChoiceSheet(
            title = stringResource(R.string.profile_field_brand),
            options = readyBrands.map { brand ->
                ChoiceOption(
                    value = brand.key,
                    label = "${brand.displayName} (${brand.buildBrand})",
                    testTag = "profile-device-brand-${brand.key}",
                )
            },
            selected = null,
            placeholder = stringResource(R.string.profile_device_search_hint),
            emptyText = stringResource(R.string.profile_device_no_match),
            truncatedText = { total ->
                res.getString(R.string.profile_device_truncated, SHEET_MAX_VISIBLE_ROWS, total)
            },
            onSelect = { key -> pickBrandKey = key },
            onDismiss = { showBrandSheet = false },
            modifier = Modifier.testTag("profile-device-brand-sheet"),
        )
    }

    if (confirmClearAll) {
        ModevConfirmDialog(
            title = stringResource(R.string.profile_clear_all),
            message = res.getString(
                R.string.profile_clear_all_msg,
                profileDisplayNameFor(res, viewModel.rawName, entry?.builtinSeedId),
            ),
            confirmTextRes = R.string.confirm,
            onConfirm = {
                viewModel.clearAll()
                confirmClearAll = false
            },
            onDismiss = { confirmClearAll = false },
            confirmTestTag = "profile-clear-all-confirm",
        )
    }
}

/** 一行的全部呈现值(由 [ProfileEditorView] 快照派生,组合侧以值为参数)。 */
private data class ProfileFieldSpec(
    val field: ProfileField,
    val label: String,
    val value: String,
    val filled: Boolean,
    val error: String?,
    val randomizable: Boolean,
    val bool: Boolean,
    val boolChecked: Boolean,
) {
    companion object {
        fun of(
            res: android.content.res.Resources,
            field: ProfileField,
            view: ProfileEditorView,
        ): ProfileFieldSpec {
            val text = view.texts[field].orEmpty()
            val bool = field.type == ProfileValueType.BOOL
            return ProfileFieldSpec(
                field = field,
                label = res.getString(profileFieldLabelRes(field)),
                value = when {
                    text.isEmpty() -> res.getString(R.string.profile_unset)
                    field.type == ProfileValueType.NETWORK_TYPE ||
                        field.type == ProfileValueType.SCREENSHOT_MODE -> slotLabel(res, field, text.toIntOrNull() ?: 0)

                    else -> text
                },
                filled = text.isNotEmpty(),
                error = view.errors[field]?.let { res.getString(profileFieldErrorRes(it)) },
                randomizable = ProfileFieldCatalog.isRandomizable(field) && !bool,
                bool = bool,
                boolChecked = text.toBooleanStrictOrNull() == true,
            )
        }
    }
}

/** 一行一参数:标签 + 当前值(或未设置)+ 就近随机 / 清除;BOOL 行走内 Switch。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFieldRow(
    spec: ProfileFieldSpec,
    onOpen: () -> Unit,
    onToggleBool: (Boolean) -> Unit,
    onClear: () -> Unit,
    onRandomize: () -> Unit,
) {
    val res = rememberLocaleAwareResources()
    if (spec.bool) {
        ToggleListItem(
            label = spec.label,
            checked = spec.boolChecked,
            onCheckedChange = onToggleBool,
            testTag = "profile-toggle-${spec.field.name}",
        )
        return
    }
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        headlineContent = {
            Text(
                spec.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    spec.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (spec.filled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Tokens.emptyIconHintAlpha)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("profile-value-${spec.field.name}"),
                )
                if (spec.error != null) {
                    Text(
                        spec.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("profile-error-${spec.field.name}"),
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (spec.randomizable) {
                    TipIconButton(
                        tooltip = res.getString(R.string.profile_randomize_one),
                        onClick = onRandomize,
                        modifier = Modifier.testTag("profile-randomize-${spec.field.name}"),
                    ) {
                        Icon(
                            Icons.Filled.Calculate,
                            contentDescription = res.getString(R.string.profile_randomize_one),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (spec.filled) {
                    TipIconButton(
                        tooltip = res.getString(R.string.profile_clear_one),
                        onClick = onClear,
                        modifier = Modifier.testTag("profile-clear-${spec.field.name}"),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = res.getString(R.string.profile_clear_one),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-row-${spec.field.name}")
            .clickable(onClick = onOpen),
    )
}

/** 机型库选择行:状态即文案(加载 / 不可用 / 可点),禁用态不出 sheet(点了没反应最费解)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePresetRow(
    state: DeviceCatalogState,
    onPick: () -> Unit,
) {
    val enabled = state is DeviceCatalogState.Ready
    val supporting = stringResource(
        when (state) {
            DeviceCatalogState.Loading -> R.string.profile_device_loading
            DeviceCatalogState.Unavailable -> R.string.profile_device_unavailable
            else -> R.string.profile_device_pick_hint
        },
    )
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        headlineContent = {
            Text(
                stringResource(R.string.profile_device_pick_action),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = Tokens.disabledContentAlpha)
                },
            )
        },
        supportingContent = {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (state == DeviceCatalogState.Unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.testTag("profile-device-pick-hint"),
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-device-pick")
            .then(if (enabled) Modifier.clickable(onClick = onPick) else Modifier),
    )
}

/** 枚举槽标签:未知槽回落裸数字(槽集由目录反推,UI 不留第二张槽表,也不可崩)。 */
private fun slotLabel(res: android.content.res.Resources, field: ProfileField, slot: Int): String {
    @StringRes val labelRes = profileSlotLabelRes(field, slot)
    return if (labelRes == 0) slot.toString() else res.getString(labelRes)
}

/** 输入期实时校验:结论 → 四语文案(null = 当前输入合法)。 */
private fun liveFieldError(res: android.content.res.Resources, field: ProfileField, text: String): String? =
    ProfileFieldCatalog.validate(field, text)?.let { res.getString(profileFieldErrorRes(it)) }

/** 输入框提示:数值项给有效域,有形态要求的给形态要求,自由文本不给噪音。 */
private fun fieldHint(res: android.content.res.Resources, field: ProfileField): String? {
    ProfileFieldCatalog.rangeOf(field)?.let { range ->
        return res.getString(R.string.profile_range_hint, "${range.first}-${range.last}")
    }
    @StringRes val shapeRes = shapeHintResOf(field)
    return if (shapeRes == 0) null else res.getString(shapeRes)
}

/** 该参数的形态要求(目录里确有格式校验的字段才有;其余返回 0 = 无提示)。 */
@StringRes
private fun shapeHintResOf(field: ProfileField): Int = when (field) {
    ProfileField.SIM_OPERATOR -> R.string.profile_error_sim_operator_format
    ProfileField.SIM_COUNTRY -> R.string.profile_error_sim_country_format
    ProfileField.IMEI -> R.string.profile_error_imei_format
    ProfileField.ANDROID_ID -> R.string.profile_error_android_id_format
    ProfileField.WIFI_BSSID, ProfileField.WIFI_MAC_ADDRESS -> R.string.profile_error_mac_format
    ProfileField.LANGUAGE -> R.string.profile_error_language_format
    ProfileField.TIME_ZONE -> R.string.profile_error_time_zone_unknown
    ProfileField.FINGERPRINT -> R.string.profile_error_fingerprint_structure
    ProfileField.LONGITUDE, ProfileField.LATITUDE -> R.string.profile_error_number_out_of_range
    else -> 0
}

/** 输入长度上限:数值项取有效域上界的位数(电池 3、小区标识 9),文本项不限。 */
private fun maxInputLengthOf(field: ProfileField): Int? =
    ProfileFieldCatalog.rangeOf(field)?.last?.toString()?.length

/** 保存结果 → Snackbar 文案(VM 零资源感知,映射在本侧)。 */
@StringRes
internal fun profileSaveMessage(result: ProfileSaveResult): Int = when (result) {
    ProfileSaveResult.Saved -> R.string.profile_saved
    ProfileSaveResult.Rejected -> R.string.profile_save_rejected
}

/** 库/编辑两页共用的条目展示名(内置行取 seedId 四语资源,DIY 行取存储名)。 */
internal fun profileEntryDisplayName(res: android.content.res.Resources, entry: DeviceProfileEntry?): String =
    if (entry == null) {
        res.getString(R.string.unknown_error)
    } else {
        profileDisplayNameFor(res, entry.name, entry.builtinSeedId)
    }

/**
 * 展示名解析的**唯一实现**(条目态与绑定三态共用):
 * 内置档的存储 name 恒为空串(身份与呈现分离,见 [DeviceProfileEntry] KDoc),
 * 故内置行必须按 seedId 取四语资源,DIY 行才取存储名。
 */
internal fun profileDisplayNameFor(
    res: android.content.res.Resources,
    name: String,
    builtinSeedId: String?,
): String = when {
    builtinSeedId != null -> res.getString(builtinProfileNameRes(builtinSeedId))
    name.isNotBlank() -> name
    else -> res.getString(R.string.unknown_error)
}

/** 内置档编辑提示(脱离账本管辖的后果,恒内联不弹窗)。 */
@Composable
private fun BuiltinEditingHint() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-builtin-hint"),
    ) {
        Text(
            stringResource(R.string.profile_builtin_edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** 设备身份预览卡:身份逐段一行 + 指纹三元组对账三色标记(实时随输入重算)。 */
@Composable
private fun ProfileIdentityCard(preview: ProfileIdentityPreview) {
    val res = rememberLocaleAwareResources()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-identity-card"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_identity_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                preview.model.ifBlank { stringResource(R.string.profile_unset) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("profile-identity-model"),
            )
            IdentityLine(label = res.getString(R.string.profile_field_brand), value = preview.brand)
            IdentityLine(label = res.getString(R.string.profile_field_manufacturer), value = preview.manufacturer)
            IdentityLine(label = res.getString(R.string.profile_field_product), value = preview.product)
            IdentityLine(label = res.getString(R.string.profile_field_device), value = preview.device)
            IdentityLine(label = res.getString(R.string.profile_field_android_version), value = preview.androidVersion)
            IdentityLine(label = res.getString(R.string.profile_field_fingerprint), value = preview.fingerprint)
            Spacer(Modifier.height(12.dp))
            IdentityCheckLine(preview)
        }
    }
}

/** 身份卡内一行一参数(空值行不渲染,免得满屏「未设置」噪音)。 */
@Composable
private fun IdentityLine(label: String, value: String) {
    if (value.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(IDENTITY_LABEL_WIDTH),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一致性标记:一致 = primary、不一致 = error、尚不可判 = onSurfaceVariant。 */
@Composable
private fun IdentityCheckLine(preview: ProfileIdentityPreview) {
    val res = rememberLocaleAwareResources()
    val dotColor = when (preview.check) {
        ProfileIdentityCheck.Consistent -> MaterialTheme.colorScheme.primary
        ProfileIdentityCheck.Mismatched -> MaterialTheme.colorScheme.error
        ProfileIdentityCheck.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val message = when (preview.check) {
        ProfileIdentityCheck.Consistent -> stringResource(R.string.profile_identity_ok)
        ProfileIdentityCheck.Mismatched -> res.getString(
            R.string.profile_identity_mismatch,
            preview.fingerprintPrefix,
            preview.triplePrefix,
        )

        ProfileIdentityCheck.Unknown -> stringResource(R.string.profile_identity_unknown)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-identity-check"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
                .clearAndSetSemantics {},
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (preview.check == ProfileIdentityCheck.Mismatched) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 实时生效概览:已填参数数 + 将安装的干预组 chips + 矛盾内联提示。 */
@Composable
private fun ProfileCoverageCard(
    filledCount: Int,
    groups: List<ProfileKind>,
    conflicts: List<ProfileConflict>,
) {
    val res = rememberLocaleAwareResources()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-coverage-card"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_coverage_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.profile_summary_filled, filledCount, filledCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profile-filled-count"),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.profile_groups_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (groups.isEmpty()) {
                Text(
                    stringResource(R.string.profile_groups_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("profile-groups-empty"),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.chunked(GROUPS_PER_LINE).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            line.forEach { kind ->
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            res.getString(profileKindLabelRes(kind)),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    modifier = Modifier.testTag("profile-group-${kind.name}"),
                                )
                            }
                        }
                    }
                }
            }
            if (conflicts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.profile_conflicts_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                conflicts.forEach { conflict ->
                    Text(
                        res.getString(profileConflictLabelRes(conflict)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("profile-conflict-${conflict.name}"),
                    )
                }
            }
        }
    }
}

/** 保存闸底栏:非法项恒点名「哪一项 + 为什么」;可用性由 [ProfileEditorView.canSave] 单点裁决。 */
@Composable
private fun ProfileSaveBar(
    leading: String,
    invalid: Boolean,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-save-bar"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = leading,
                style = MaterialTheme.typography.bodySmall,
                color = if (invalid) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .testTag(if (invalid) "profile-save-invalid" else "profile-save-summary"),
            )
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.testTag("profile-save"),
            ) {
                Text(stringResource(R.string.profile_save))
            }
        }
    }
}

private val IDENTITY_LABEL_WIDTH = 120.dp
private const val GROUPS_PER_LINE = 3
