package com.lioisme.modev.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.AppContainer
import com.lioisme.modev.R
import com.lioisme.modev.core.model.AppListenOverride
import com.lioisme.modev.core.model.BehaviorKind
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.EffectiveSwitch
import com.lioisme.modev.core.model.HookSwitch
import com.lioisme.modev.core.model.PrefsKind
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.WebViewKind
import com.lioisme.modev.core.model.NetworkKind
import com.lioisme.modev.core.model.DeviceEnvKind
import com.lioisme.modev.core.model.UiInteractionKind
import com.lioisme.modev.core.model.SystemKind
import com.lioisme.modev.core.model.ProcessKind
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.ui.components.HorizontalDivider16
import com.lioisme.modev.ui.components.IntLimitSheet
import com.lioisme.modev.ui.components.IgnorePathSheet
import com.lioisme.modev.ui.components.IgnoreStackSheet
import com.lioisme.modev.ui.components.MaxBytesSheet
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 单应用监听自定义状态(V24 二态偏差模型;V25 扩展采集微调项):
 * - [effective] 有效配置 = 内置默认配置 ⊕ 本应用偏差(默认恒为内置推荐值);
 * - [override] 当前偏差(零偏差时恢复默认行隐藏);
 * - 写操作即时持久化 + 定向推送注入侧(setOverride/removeOverride 全链路);
 * - 零偏差写入自动等价删除存储键(SettingsRepository 契约)。
 */
class PerAppListenSettingsViewModel(
    val packageName: String,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 行值回显来源:有效配置快照流(默认配置或偏差任一变化即重发射)。 */
    val effective: StateFlow<HookSwitch> =
        combine(settings.globalSwitch, settings.overrides) { default, map ->
            EffectiveSwitch.resolve(default, map[packageName])
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.effectiveSwitch(packageName),
        )

    /** 当前偏差(供恢复默认行可见性与计数摘要)。 */
    val override: StateFlow<AppListenOverride> = settings.overrides
        .map { it[packageName] ?: AppListenOverride.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListenOverride.DEFAULT)

    /** 总开关定制(crypto/db/behavior;显式布尔,无第三态)。 */
    fun setMaster(isCrypto: Boolean, value: Boolean) {
        update {
            if (isCrypto) it.copy(cryptoListen = value) else it.copy(dbListen = value)
        }
    }

    /** 行为监听总开关定制(V27)。 */
    fun setBehaviorMaster(value: Boolean) {
        update { it.copy(behaviorListen = value) }
    }

    /** 偏好监听总开关定制(V28)。 */
    fun setPrefsMaster(value: Boolean) {
        update { it.copy(prefsListen = value) }
    }

    /** WebView 监听总开关定制(V29)。 */
    fun setWebviewMaster(value: Boolean) {
        update { it.copy(webviewListen = value) }
    }

    /** 系统信息监听总开关定制(V33)。 */
    fun setSystemMaster(value: Boolean) {
        update { it.copy(systemListen = value) }
    }

    /** 类别定制(kind 名跨枚举;非法名由 AppListenOverride 容错忽略)。 */
    fun setKind(kindId: String, value: Boolean) {
        update { it.copy(kinds = it.kinds + (kindId to value)) }
    }

    /** 记录堆栈定制(V25)。 */
    fun setLogStack(value: Boolean) {
        update { it.copy(logStack = value) }
    }

    /** 单字段字节上限定制(V25;≤0 语义为不限)。 */
    fun setMaxBytesPerField(value: Int) {
        update { it.copy(maxBytesPerField = value) }
    }

    /** 忽略栈前缀定制(V25;整表替换,空表归零为取默认)。 */
    fun setIgnoreStackPrefixes(value: List<String>) {
        update { it.copy(ignoreStackPrefixes = value.ifEmpty { null }) }
    }

    /** 结果行数上限定制(V25)。 */
    fun setMaxResultRows(value: Int) {
        update { it.copy(maxResultRows = value) }
    }

    /** 结果字节上限定制(V25)。 */
    fun setMaxResultChars(value: Int) {
        update { it.copy(maxResultChars = value) }
    }

    /** 文件内容捕获定制(V27)。 */
    fun setFileContentCapture(value: Boolean) {
        update { it.copy(fileContentCapture = value) }
    }

    /** 忽略路径前缀定制(V27;整表替换,空表归零为取默认)。 */
    fun setIgnorePathPrefixes(value: List<String>) {
        update { it.copy(ignorePathPrefixes = value.ifEmpty { null }) }
    }

    /** 一键恢复默认配置(删除本包全部偏差存储键)。 */
    fun reset() {
        viewModelScope.launch { settings.removeOverride(packageName) }
    }

    private fun update(transform: (AppListenOverride) -> AppListenOverride) {
        viewModelScope.launch {
            settings.setOverride(packageName, transform(settings.overrideFor(packageName)))
        }
    }
}

class PerAppListenSettingsViewModelFactory(
    private val packageName: String,
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PerAppListenSettingsViewModel(
            packageName = packageName,
            settings = container.settings,
        ) as T
    }
}

/**
 * 每应用监听设置页(V24 G1 二态重构;V25 扩展采集微调项):
 * - 每行直接承载 M3 Switch(整行 toggleable,单击即写即推 ConfigServer.pushToPackage);
 * - 总开关区(算法/数据库)+ 算法 12 类别 + 数据库 5 类别 + 记录选项 + 结果闸门;
 *   行值显示有效配置(未定制项恒为内置默认值,无「跟随」第三态);
 * - 数值行经底部 sheet 编辑(MaxBytesSheet/IgnoreStackSheet/IntLimitSheet,契约 4);
 * - 操作区仅在存在自定义时出现:「恢复默认配置」(危险确认 AlertDialog 后 removeOverride)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppListenSettingsScreen(
    viewModel: PerAppListenSettingsViewModel,
    onBack: () -> Unit,
) {
    val effective by viewModel.effective.collectAsStateWithLifecycle()
    val override by viewModel.override.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    var showMaxBytesSheet by remember { mutableStateOf(false) }
    var showIgnoreStackSheet by remember { mutableStateOf(false) }
    var showIgnorePathSheet by remember { mutableStateOf(false) }
    var showResultRowsSheet by remember { mutableStateOf(false) }
    var showResultCharsSheet by remember { mutableStateOf(false) }
    val res = rememberLocaleAwareResources()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.listen_settings_entry),
                onBack = onBack,
            )
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
                viewModel.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                stringResource(R.string.listen_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp),
            )

            SettingsSection(stringResource(R.string.section_monitor_kinds)) {
                ListenRow(
                    label = stringResource(R.string.algorithm_monitor_master),
                    checked = effective.cryptoListen,
                    onCheckedChange = { viewModel.setMaster(isCrypto = true, value = it) },
                    testTag = "listen-row-master-crypto",
                )
                HorizontalDivider16()
                ListenRow(
                    label = stringResource(R.string.database_monitor_master),
                    checked = effective.dbListen,
                    onCheckedChange = { viewModel.setMaster(isCrypto = false, value = it) },
                    testTag = "listen-row-master-db",
                )
                HorizontalDivider16()
                ListenRow(
                    label = res.getString(R.string.behavior_monitor_master),
                    checked = effective.behaviorListen,
                    onCheckedChange = { viewModel.setBehaviorMaster(it) },
                    testTag = "listen-row-master-behavior",
                )
                HorizontalDivider16()
                ListenRow(
                    label = res.getString(R.string.prefs_monitor_master),
                    checked = effective.prefsListen,
                    onCheckedChange = { viewModel.setPrefsMaster(it) },
                    testTag = "listen-row-master-prefs",
                )
                HorizontalDivider16()
                ListenRow(
                    label = res.getString(R.string.webview_monitor_master),
                    checked = effective.webviewListen,
                    onCheckedChange = { viewModel.setWebviewMaster(it) },
                    testTag = "listen-row-master-webview",
                )
                HorizontalDivider16()
                ListenRow(
                    label = res.getString(R.string.listen_row_master_system),
                    checked = effective.systemListen,
                    onCheckedChange = { viewModel.setSystemMaster(it) },
                    testTag = "listen-row-master-system",
                )
            }
            Spacer(Modifier.height(16.dp))

            // 算法 12 类别(CategoryTaxonomy 单一事实源排序,V22 契约)
            SettingsSection(stringResource(R.string.filter_type_crypto)) {
                CategoryTaxonomy.forType(RecordType.CRYPTO).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16()
                    ListenRow(
                        label = res.getString(KindLabelRes.resolve(category.id)),
                        checked = EffectiveSwitch.readKind(effective, category.id) == true,
                        onCheckedChange = { viewModel.setKind(category.id, it) },
                        testTag = "listen-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 数据库 5 类别
            SettingsSection(stringResource(R.string.filter_type_database)) {
                CategoryTaxonomy.forType(RecordType.DATABASE).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16()
                    ListenRow(
                        label = res.getString(KindLabelRes.resolve(category.id)),
                        checked = EffectiveSwitch.readKind(effective, category.id) == true,
                        onCheckedChange = { viewModel.setKind(category.id, it) },
                        testTag = "listen-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 行为 7 类别 + 内容捕获 + 忽略路径前缀(V27 一等公民)
            SettingsSection(stringResource(R.string.filter_type_behavior)) {
                CategoryTaxonomy.forType(RecordType.BEHAVIOR).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16()
                    ListenRow(
                        label = res.getString(KindLabelRes.resolve(category.id)),
                        checked = EffectiveSwitch.readKind(effective, category.id) == true,
                        onCheckedChange = { viewModel.setKind(category.id, it) },
                        testTag = "listen-row-${category.id}",
                    )
                }
                HorizontalDivider16()
                ListenRow(
                    label = res.getString(R.string.switch_file_content_capture),
                    checked = effective.fileContentCapture,
                    onCheckedChange = { viewModel.setFileContentCapture(it) },
                    testTag = "listen-row-file-content",
                    supporting = res.getString(R.string.switch_file_content_capture_hint),
                )
                HorizontalDivider16()
                OptionRowItem(
                    icon = Icons.Outlined.FolderOpen,
                    title = res.getString(R.string.capture_ignore_path),
                    value = (effective.ignorePathPrefixes.size).toString(),
                    hint = res.getString(R.string.capture_ignore_path_hint),
                    onClick = { showIgnorePathSheet = true },
                    modifier = Modifier.testTag("listen-row-ignore-path"),
                )
            }
            Spacer(Modifier.height(16.dp))

            // 偏好 5 类别(V28 一等公民;值捕获复用 fileContentCapture、路径降噪复用 ignorePathPrefixes)
            SettingsSection(stringResource(R.string.filter_type_prefs)) {
                CategoryTaxonomy.forType(RecordType.PREFS).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16()
                    ListenRow(
                        label = res.getString(KindLabelRes.resolve(category.id)),
                        checked = EffectiveSwitch.readKind(effective, category.id) == true,
                        onCheckedChange = { viewModel.setKind(category.id, it) },
                        testTag = "listen-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // WebView 5 类别(V29 一等公民)
            SettingsSection(stringResource(R.string.filter_type_webview)) {
                CategoryTaxonomy.forType(RecordType.WEBVIEW).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16()
                    ListenRow(
                        label = res.getString(KindLabelRes.resolve(category.id)),
                        checked = EffectiveSwitch.readKind(effective, category.id) == true,
                        onCheckedChange = { viewModel.setKind(category.id, it) },
                        testTag = "listen-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 环境观测(V37 双域拆分):NETWORK/DEVICE_ENV/UI_INTERACTION 三类型中属
            // 监听域的观测类别(请求监控/检测监控/弹窗·控件·Activity 观测),
            // 由 CategoryTaxonomy LISTEN 域驱动;干预类别一律在「功能设置」页管理。
            SettingsSection(stringResource(R.string.listen_section_env_observe)) {
                val envObserveKinds = listOf(RecordType.NETWORK, RecordType.DEVICE_ENV, RecordType.UI_INTERACTION)
                    .flatMap { type -> CategoryTaxonomy.forType(type) }
                    .filter { it.domain == com.lioisme.modev.core.model.CapabilityDomain.LISTEN }
                envObserveKinds.forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16()
                    ListenRow(
                        label = res.getString(KindLabelRes.resolve(category.id)),
                        checked = EffectiveSwitch.readKind(effective, category.id) == true,
                        onCheckedChange = { viewModel.setKind(category.id, it) },
                        testTag = "listen-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 系统信息 3 类别(V33 一等公民)
            SettingsSection(stringResource(R.string.filter_type_system)) {
                CategoryTaxonomy.forType(RecordType.SYSTEM).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16()
                    ListenRow(
                        label = res.getString(KindLabelRes.resolve(category.id)),
                        checked = EffectiveSwitch.readKind(effective, category.id) == true,
                        onCheckedChange = { viewModel.setKind(category.id, it) },
                        testTag = "listen-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // V25 记录选项:堆栈开关(行内 Switch)+ 单字段字节上限 / 忽略栈前缀(sheet 编辑)。
            SettingsSection(stringResource(R.string.section_record_options)) {
                ListenRow(
                    label = res.getString(R.string.switch_log_stack),
                    checked = effective.logStack,
                    onCheckedChange = { viewModel.setLogStack(it) },
                    testTag = "listen-row-log-stack",
                )
                HorizontalDivider16()
                OptionRowItem(
                    icon = Icons.Filled.DataUsage,
                    title = res.getString(R.string.capture_max_bytes),
                    value = limitValue(effective.maxBytesPerField),
                    hint = res.getString(R.string.capture_max_bytes_hint),
                    onClick = { showMaxBytesSheet = true },
                    modifier = Modifier.testTag("listen-row-max-bytes"),
                )
                HorizontalDivider16()
                OptionRowItem(
                    icon = Icons.Filled.ContentCut,
                    title = res.getString(R.string.capture_ignore_stack),
                    value = (effective.ignoreStackPrefixes.size).toString(),
                    hint = res.getString(R.string.capture_ignore_stack_hint),
                    onClick = { showIgnoreStackSheet = true },
                    modifier = Modifier.testTag("listen-row-ignore-stack"),
                )
            }
            Spacer(Modifier.height(16.dp))

            // V25 结果闸门:结果行数 / 结果字节上限(sheet 编辑)。
            SettingsSection(stringResource(R.string.section_db_result_limits)) {
                OptionRowItem(
                    icon = Icons.Filled.FormatListNumbered,
                    title = res.getString(R.string.db_max_result_rows),
                    value = limitValue(effective.maxResultRows),
                    hint = res.getString(R.string.db_max_result_rows_hint),
                    onClick = { showResultRowsSheet = true },
                    modifier = Modifier.testTag("listen-row-result-rows"),
                )
                HorizontalDivider16()
                OptionRowItem(
                    icon = Icons.Filled.TextFields,
                    title = res.getString(R.string.db_max_result_chars),
                    value = limitValue(effective.maxResultChars),
                    hint = res.getString(R.string.db_max_result_chars_hint),
                    onClick = { showResultCharsSheet = true },
                    modifier = Modifier.testTag("listen-row-result-chars"),
                )
            }
            Spacer(Modifier.height(16.dp))

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
                            // V37 C8:偏差计数收敛单一事实源 AppListenOverride.deviationCount
                            val n = override.deviationCount
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
                            .testTag("listen-reset")
                            .clickable { confirmReset = true },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Filled.RestartAlt, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = {
                Text(stringResource(R.string.danger_reset_listen_msg, viewModel.packageName))
            },
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

    if (showMaxBytesSheet) {
        MaxBytesSheet(
            initial = effective.maxBytesPerField,
            onConfirm = {
                viewModel.setMaxBytesPerField(it)
                showMaxBytesSheet = false
            },
            onDismiss = { showMaxBytesSheet = false },
        )
    }
    if (showIgnoreStackSheet) {
        IgnoreStackSheet(
            initial = effective.ignoreStackPrefixes,
            onConfirm = {
                viewModel.setIgnoreStackPrefixes(it)
                showIgnoreStackSheet = false
            },
            onDismiss = { showIgnoreStackSheet = false },
        )
    }
    if (showIgnorePathSheet) {
        IgnorePathSheet(
            initial = effective.ignorePathPrefixes,
            onConfirm = {
                viewModel.setIgnorePathPrefixes(it)
                showIgnorePathSheet = false
            },
            onDismiss = { showIgnorePathSheet = false },
        )
    }
    if (showResultRowsSheet) {
        IntLimitSheet(
            titleRes = R.string.db_max_result_rows_dialog_title,
            hintRes = R.string.db_max_result_rows_hint,
            initial = effective.maxResultRows,
            onConfirm = {
                viewModel.setMaxResultRows(it)
                showResultRowsSheet = false
            },
            onDismiss = { showResultRowsSheet = false },
        )
    }
    if (showResultCharsSheet) {
        IntLimitSheet(
            titleRes = R.string.db_max_result_chars_dialog_title,
            hintRes = R.string.db_max_result_chars_hint,
            initial = effective.maxResultChars,
            onConfirm = {
                viewModel.setMaxResultChars(it)
                showResultCharsSheet = false
            },
            onDismiss = { showResultCharsSheet = false },
        )
    }
}

/** 上限值显示惯例:≤0 = 不限(∞),否则十进制数值。 */
private fun limitValue(value: Int): String = if (value <= 0) "∞" else value.toString()

/**
 * 监听行:label(+可选 supporting 副文)+ 尾部 M3 Switch;整行 toggleable(role=Switch)
 * 单击切换,内层 Switch onCheckedChange=null 并 clearAndSetSemantics 去重(契约 13 同款语义)。
 */
@Composable
private fun ListenRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    supporting: String? = null,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent =
            if (supporting.isNullOrBlank()) {
                null
            } else {
                {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

