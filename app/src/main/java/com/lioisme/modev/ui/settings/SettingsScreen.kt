package com.lioisme.modev.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.BuildConfig
import com.lioisme.modev.R
import com.lioisme.modev.core.codec.WireProtocol
import com.lioisme.modev.domain.scope.ScopeState
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.apps.AppListSettings
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsInfoItem
import com.lioisme.modev.ui.components.SettingsItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.components.SettingsToggleItem
import com.lioisme.modev.ui.theme.AppearanceMode
import com.lioisme.modev.ui.theme.LocalIsDarkTheme
import com.lioisme.modev.ui.theme.ThemeConfig
import com.lioisme.modev.ui.theme.ThemeSeeds
import com.lioisme.modev.ui.theme.ThemeSettingsManager
import kotlinx.coroutines.launch

// 主题色预设单一事实源 = ThemeSeeds.OPTIONS(ui/theme)。
private val colorOptions: List<Long> = ThemeSeeds.OPTIONS

/**
 * 设置页(2026-08-17 V9 六分区重组):
 * 卡片式分区(SettingsSection = surfaceContainerLow 卡),按功能域分层:
 * - 显示:外观模式 sheet + 主题色 sheet(跟随壁纸 / 20 色预设网格);
 * - 语言与地区:语言 sheet(LocaleManager 热切换,零重建);
 * - 应用列表:已作用域应用置顶开关(AppListSettings 单一事实源);
 * - 数据:总记录数只读行 + 清空全部日志(errorContainer 危险块);
 * - 服务与诊断:框架与作用域 + 通信服务只读信息(SettingsInfoItem)+
 *   管道统计折叠卡(默认收起,AnimatedVisibility,状态 rememberSaveable);
 * - 关于:版本 + 关于 Dialog。
 * 本页经应用列表筛选菜单「设置」项进入(唯一入口),返回箭头常显。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit = {},
) {
    val connectedCount by viewModel.connectedCount.collectAsStateWithLifecycle()
    val totalRecords by viewModel.totalRecords.collectAsStateWithLifecycle()
    val frameworkState by viewModel.frameworkState.collectAsStateWithLifecycle()
    val pipelineStats by viewModel.pipelineStats.collectAsStateWithLifecycle()
    val pinScoped by AppListSettings.pinScoped.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val config by ThemeSettingsManager.themeConfig.collectAsState()

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    // 语言 sheet 状态(V9.1):点选语言先播退场动画再切换,动画期间语言不变,
    // 避免「sheet 瞬时消失 + 全屏文本同帧切换」叠影闪烁。
    val scope = rememberCoroutineScope()
    val languageSheetState = rememberModalBottomSheetState()

    // 管道统计折叠态(跨重建存活:展开/收起不因重组复位)。
    var statsExpanded by rememberSaveable { mutableStateOf(false) }

    // 语言标签收集 localeFlow(V9 热切换:切换即时刷新,零重建)。
    val currentLanguageTag by LocaleManager.localeFlow.collectAsStateWithLifecycle()
    val languageSubtitle =
        when (currentLanguageTag) {
            LocaleManager.TAG_SYSTEM -> stringResource(R.string.system_default)
            LocaleManager.TAG_EN -> stringResource(R.string.lang_en)
            LocaleManager.TAG_ZH -> stringResource(R.string.lang_zh)
            else -> currentLanguageTag
        }

    Scaffold(
        topBar = {
            ModevTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            SettingsSection(stringResource(R.string.settings_display_section)) {
                SettingsItem(
                    icon =
                        when (config.appearanceMode) {
                            AppearanceMode.SYSTEM -> Icons.Default.PhoneAndroid
                            AppearanceMode.LIGHT -> Icons.Default.LightMode
                            AppearanceMode.DARK -> Icons.Default.DarkMode
                        },
                    title = stringResource(R.string.appearance),
                    subtitle =
                        when (config.appearanceMode) {
                            AppearanceMode.SYSTEM -> stringResource(R.string.follow_system)
                            AppearanceMode.LIGHT -> stringResource(R.string.light_mode)
                            AppearanceMode.DARK -> stringResource(R.string.dark_mode)
                        },
                    onClick = { showAppearanceSheet = true },
                )
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.theme_color),
                    subtitle = if (config.useWallpaper) stringResource(R.string.follow_wallpaper) else stringResource(R.string.custom_accent),
                    onClick = { showColorSheet = true },
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_language_section)) {
                SettingsItem(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.language),
                    subtitle = languageSubtitle,
                    onClick = { showLanguageSheet = true },
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_apps_section)) {
                SettingsToggleItem(
                    icon = Icons.Default.PushPin,
                    title = stringResource(R.string.settings_pin_scoped),
                    checked = pinScoped,
                    onCheckedChange = { AppListSettings.setPinScoped(it, context) },
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_data_section)) {
                SettingsInfoItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_total_records),
                    value = stringResource(R.string.settings_total_records_value, totalRecords),
                )
                Surface(
                    onClick = { showClearAllConfirm = true },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.settings_clear_all_logs),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_service_section)) {
                when (val framework = frameworkState) {
                    is ScopeState.Connecting -> {
                        SettingsInfoItem(
                            icon = Icons.Default.Android,
                            title = stringResource(R.string.settings_framework_service),
                            value = stringResource(R.string.settings_connecting),
                        )
                    }
                    is ScopeState.Ready -> {
                        SettingsInfoItem(
                            icon = Icons.Default.Android,
                            title = stringResource(R.string.settings_framework),
                            value = "${framework.frameworkName} ${framework.frameworkVersion}",
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Apps,
                            title = stringResource(R.string.settings_scoped_count),
                            value = stringResource(R.string.settings_scoped_count_value, framework.scope.size),
                        )
                    }
                    is ScopeState.Unavailable -> {
                        SettingsInfoItem(
                            icon = Icons.Default.Android,
                            title = stringResource(R.string.settings_framework_service),
                            value = stringResource(R.string.settings_not_connected),
                        )
                        if (framework.reason == ScopeState.UnavailableReason.SERVICE_ERROR) {
                            Text(
                                framework.message?.let { msg ->
                                    stringResource(R.string.settings_framework_error, msg)
                                } ?: stringResource(R.string.unknown_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
                SettingsInfoItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.settings_port),
                    value = stringResource(R.string.settings_port_value, WireProtocol.PORT),
                )
                SettingsInfoItem(
                    icon = Icons.Default.Devices,
                    title = stringResource(R.string.settings_online_targets),
                    value = stringResource(R.string.settings_online_targets_value, connectedCount),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                StatsToggleRow(expanded = statsExpanded, onClick = { statsExpanded = !statsExpanded })
                AnimatedVisibility(visible = statsExpanded) {
                    Column {
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_received),
                            value = pipelineStats.received.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_dropped),
                            value = pipelineStats.dropped.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_write_failures),
                            value = pipelineStats.writeFailures.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_trimmed),
                            value = pipelineStats.trimmed.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_rejected_hellos),
                            value = pipelineStats.channel.rejectedHellos.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_dropped_records),
                            value = pipelineStats.channel.droppedRecords.toString(),
                        )
                        SettingsInfoItem(
                            icon = Icons.Default.Dns,
                            title = stringResource(R.string.stats_rejected_records),
                            value = pipelineStats.channel.rejectedRecords.toString(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_about_section)) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                    onClick = { showAboutDialog = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAppearanceSheet) {
        AppearanceSheet(
            current = config.appearanceMode,
            onSelect = { mode ->
                ThemeSettingsManager.update(config.copy(appearanceMode = mode), context)
                showAppearanceSheet = false
            },
            onDismiss = { showAppearanceSheet = false },
        )
    }

    if (showColorSheet) {
        ColorSheet(
            useWallpaper = config.useWallpaper,
            selectedColor = if (config.useWallpaper) null else Color(config.customSeedColor),
            onSelectWallpaper = {
                ThemeSettingsManager.update(config.copy(useWallpaper = true), context)
            },
            onSelectColor = { argb ->
                ThemeSettingsManager.update(
                    config.copy(useWallpaper = false, customSeedColor = argb),
                    context,
                )
            },
            onDismiss = { showColorSheet = false },
        )
    }

    if (showLanguageSheet) {
        LanguageSheet(
            currentTag = currentLanguageTag,
            sheetState = languageSheetState,
            onSelect = { tag ->
                // V9.1 无闪切换:先播 sheet 退场动画(掩蔽切换瞬间),动画完成后
                // 再切语言——避免「sheet 窗口瞬间消失 + 全屏文本同时切换」叠影闪烁
                // (实机复现 2026-08-17)。移除组合(showLanguageSheet=false)在
                // hide 完成后执行,退场动画不会被截断。
                scope.launch {
                    languageSheetState.hide()
                    LocaleManager.setLanguage(context, tag)
                    showLanguageSheet = false
                }
            },
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text(stringResource(R.string.about)) },
            text = {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.version, stringResource(R.string.app_name), BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.confirm))
                }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text(stringResource(R.string.danger_title)) },
            text = { Text(stringResource(R.string.danger_clear_all_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearAllConfirm = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    currentTag: String,
    sheetState: SheetState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))

            val localeData =
                listOf(
                    Triple(LocaleManager.TAG_SYSTEM, Icons.Default.PhoneAndroid, R.string.system_default),
                    Triple(LocaleManager.TAG_EN, Icons.Default.Language, R.string.lang_en),
                    Triple(LocaleManager.TAG_ZH, Icons.Default.Language, R.string.lang_zh),
                )

            localeData.forEach { (tag, icon, labelRes) ->
                val label = stringResource(labelRes)
                val selected = tag == currentTag
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(tag) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSheet(
    current: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            AppearanceMode.entries.forEach { mode ->
                val icon =
                    when (mode) {
                        AppearanceMode.SYSTEM -> Icons.Default.PhoneAndroid
                        AppearanceMode.LIGHT -> Icons.Default.LightMode
                        AppearanceMode.DARK -> Icons.Default.DarkMode
                    }
                val label =
                    when (mode) {
                        AppearanceMode.SYSTEM -> stringResource(R.string.follow_system)
                        AppearanceMode.LIGHT -> stringResource(R.string.light_mode)
                        AppearanceMode.DARK -> stringResource(R.string.dark_mode)
                    }
                val selected = mode == current
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSheet(
    useWallpaper: Boolean,
    selectedColor: Color?,
    onSelectWallpaper: () -> Unit,
    onSelectColor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.theme_color),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelectWallpaper() }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Wallpaper,
                    null,
                    tint = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.system_wallpaper),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (useWallpaper) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (useWallpaper) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.pick_accent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                items(colorOptions) { argb ->
                    val color = Color(argb)
                    val isSelected = !useWallpaper && color == selectedColor
                    // 选中标记色:暗色恒白、亮色恒黑(任意 seed 下清晰可见)。
                    val markColor = selectedMarkColor(color, LocalIsDarkTheme.current)
                    Box(
                        modifier =
                            Modifier
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(3.dp, markColor, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), CircleShape)
                                    },
                                ).semantics { selected = isSelected }
                                .clickable { onSelectColor(argb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = markColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 选中标记色:暗色模式恒白、亮色模式恒黑(与 LIDE 同规,中灰 seed 下不消失)。 */
private fun selectedMarkColor(
    color: Color,
    isDark: Boolean,
): Color = if (isDark) Color.White else Color.Black

/**
 * 管道统计折叠头行(V9):图标 + 标题 + 方向指示图标(展开↑/收起↓),
 * 与详情页字节区块「展开/收起」文案同语义(contentDescription 复用)。
 */
@Composable
private fun StatsToggleRow(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.settings_stats_section),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(if (expanded) R.string.section_collapse else R.string.section_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}