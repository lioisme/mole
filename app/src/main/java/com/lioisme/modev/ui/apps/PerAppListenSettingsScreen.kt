package com.lioisme.modev.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import com.lioisme.modev.core.model.CategoryTaxonomy
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.core.model.TriState
import com.lioisme.modev.data.repo.SettingsRepository
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.common.rememberLocaleAwareResources
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 单应用监听覆盖状态(V23 G3 三态继承模型):
 * - [override] 该应用当前覆盖(无则 DEFAULT,行尾态文本「跟随全局」);
 * - 写操作即时持久化 + 定向推送注入侧(setOverride/removeOverride 全链路);
 * - 全 INHERIT 的写入自动等价删除存储键(SettingsRepository 契约)。
 */
class PerAppListenSettingsViewModel(
    val packageName: String,
    private val settings: SettingsRepository,
) : ViewModel() {

    val override: StateFlow<AppListenOverride> = settings.overrides
        .map { it[packageName] ?: AppListenOverride.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppListenOverride.DEFAULT)

    /** 总开关覆盖(crypto/db 二选一)。 */
    fun setMaster(isCrypto: Boolean, value: TriState) {
        update {
            if (isCrypto) it.copy(cryptoListen = value) else it.copy(dbListen = value)
        }
    }

    /** 类别覆盖(kind 名跨枚举;非法名由 AppListenOverride 容错忽略)。 */
    fun setKind(kindId: String, value: TriState) {
        update {
            it.copy(kindOverrides = it.kindOverrides + (kindId to value)).normalized()
        }
    }

    /** 重置为跟随全局(删除覆盖存储键)。 */
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

/** 三态编辑目标(sheet 单选对象):总开关或某类别。 */
private sealed interface EditTarget {
    data object CryptoMaster : EditTarget
    data object DbMaster : EditTarget
    data class Kind(val id: String) : EditTarget
}

/**
 * 每应用监听设置页(V23 G3/T5.3,三态继承):
 * - 说明行:未覆盖项跟随全局、变更即时生效;
 * - 总开关区(算法/数据库)+ 算法 12 类别 + 数据库 5 类别;
 *   每行尾态按钮(跟随全局/强制开/强制关)→ 点击弹 ModalBottomSheet 单选,
 *   选择即写即推(ConfigServer.pushToPackage,注入侧下一次调用生效);
 * - 重置为跟随全局(危险确认 AlertDialog 后 removeOverride)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppListenSettingsScreen(
    viewModel: PerAppListenSettingsViewModel,
    onBack: () -> Unit,
) {
    val override by viewModel.override.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
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
                TriStateRow(
                    label = stringResource(R.string.algorithm_monitor_master),
                    state = override.cryptoListen,
                    onClick = { editTarget = EditTarget.CryptoMaster },
                    testTag = "tri-row-master-crypto",
                )
                HorizontalDivider16Compat()
                TriStateRow(
                    label = stringResource(R.string.database_monitor_master),
                    state = override.dbListen,
                    onClick = { editTarget = EditTarget.DbMaster },
                    testTag = "tri-row-master-db",
                )
            }
            Spacer(Modifier.height(16.dp))

            // 算法 12 类别(CategoryTaxonomy 单一事实源排序,V22 契约)
            SettingsSection(stringResource(R.string.filter_type_crypto)) {
                CategoryTaxonomy.forType(RecordType.CRYPTO).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16Compat()
                    TriStateRow(
                        label = res.getString(switchLabelRes(category.id)),
                        state = override.kindOverrides[category.id] ?: TriState.INHERIT,
                        onClick = { editTarget = EditTarget.Kind(category.id) },
                        testTag = "tri-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 数据库 5 类别
            SettingsSection(stringResource(R.string.filter_type_database)) {
                CategoryTaxonomy.forType(RecordType.DATABASE).forEachIndexed { index, category ->
                    if (index > 0) HorizontalDivider16Compat()
                    TriStateRow(
                        label = res.getString(switchLabelRes(category.id)),
                        state = override.kindOverrides[category.id] ?: TriState.INHERIT,
                        onClick = { editTarget = EditTarget.Kind(category.id) },
                        testTag = "tri-row-${category.id}",
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SettingsSection(stringResource(R.string.app_monitor_actions_section)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    headlineContent = {
                        Text(
                            stringResource(R.string.listen_reset_action),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Filled.RestartAlt, null, tint = MaterialTheme.colorScheme.error)
                    },
                    trailingContent = {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0f))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listen-reset")
                        .clickable { confirmReset = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editTarget?.let { target ->
        TriStateSheet(
            current = when (target) {
                EditTarget.CryptoMaster -> override.cryptoListen
                EditTarget.DbMaster -> override.dbListen
                is EditTarget.Kind -> override.kindOverrides[target.id] ?: TriState.INHERIT
            },
            onSelect = { value ->
                when (target) {
                    EditTarget.CryptoMaster -> viewModel.setMaster(true, value)
                    EditTarget.DbMaster -> viewModel.setMaster(false, value)
                    is EditTarget.Kind -> viewModel.setKind(target.id, value)
                }
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = { Icon(Icons.Filled.Delete, null) },
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
}

/**
 * 三态行:label + 尾部态按钮(跟随全局/强制开/强制关);整行可点击弹选择器。
 * 态按钮 primary 色 = 有显式覆盖的感知指示(INHERIT 时中性色)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriStateRow(
    label: String,
    state: TriState,
    onClick: () -> Unit,
    testTag: String,
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
        trailingContent = {
            Text(
                text = triStateLabel(state),
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (state == TriState.INHERIT) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                modifier = Modifier.testTag("$testTag-value"),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
    )
}

/** 三态单选底部弹层(契约 4/13:ModalBottomSheet + surfaceContainerHigh,选项即选即效)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriStateSheet(
    current: TriState,
    onSelect: (TriState) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf(TriState.INHERIT to R.string.tri_inherit, TriState.ON to R.string.tri_on, TriState.OFF to R.string.tri_off)
                .forEach { (value, labelRes) ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        headlineContent = { Text(stringResource(labelRes)) },
                        leadingContent = { RadioButton(selected = current == value, onClick = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                            },
                    )
                }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun triStateLabel(state: TriState): String = stringResource(
    when (state) {
        TriState.INHERIT -> R.string.tri_inherit
        TriState.ON -> R.string.tri_on
        TriState.OFF -> R.string.tri_off
    },
)

@Composable
private fun HorizontalDivider16Compat() {
    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

/**
 * kind id → 设置页类别开关标签资源(switch_* 组与 HookSwitch 字段一一对应;
 * 与 AlgorithmSettingsScreen/DatabaseSettingsScreen 同源映射)。
 */
private fun switchLabelRes(kindId: String): Int = when (kindId) {
    "MESSAGE_DIGEST" -> R.string.switch_message_digest
    "CIPHER" -> R.string.switch_cipher
    "MAC" -> R.string.switch_mac
    "SIGNATURE" -> R.string.switch_signature
    "KEY_PARAM" -> R.string.switch_key_param
    "KEY_GENERATION" -> R.string.switch_key_generation
    "KEY_FACTORY" -> R.string.switch_key_factory
    "KEY_STORE" -> R.string.switch_key_store
    "KEY_AGREEMENT" -> R.string.switch_key_agreement
    "RANDOM" -> R.string.switch_random
    "BASE64" -> R.string.switch_base64
    "WEB_CRYPTO" -> R.string.switch_webview
    "OPEN" -> R.string.switch_db_open
    "QUERY" -> R.string.switch_db_query
    "DML" -> R.string.switch_db_dml
    "EXEC" -> R.string.switch_db_exec
    "TRANSACTION" -> R.string.switch_db_transaction
    else -> R.string.unknown_error
}
