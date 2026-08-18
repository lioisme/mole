package com.lioisme.modev.ui.settings

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioisme.modev.BuildConfig
import com.lioisme.modev.R
import com.lioisme.modev.i18n.LocaleManager
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.theme.AppearanceMode
import com.lioisme.modev.ui.theme.LocalIsDarkTheme
import com.lioisme.modev.ui.theme.ThemeConfig
import com.lioisme.modev.ui.theme.ThemeSeeds
import com.lioisme.modev.ui.theme.ThemeSettingsManager
import kotlinx.coroutines.launch

// 主题色预设单一事实源 = ThemeSeeds.OPTIONS(ui/theme)。
private val colorOptions: List<Long> = ThemeSeeds.OPTIONS

/**
 * 设置页(V10 设置分级,通用设置收敛本页):
 * 卡片式分区(SettingsSection = surfaceContainerLow 卡),仅通用设置 + 入口:
 * - 显示:外观模式 sheet + 主题色 sheet(跟随壁纸 / 20 色预设网格);
 * - 语言与地区:语言 sheet(LocaleManager 热切换,零重建);
 * - 功能:能力注册表驱动(CapabilityRegistry 单一事实源),当前为「捕获设置」
 *   (全局算法捕获,自注册的 CryptoCaptureCapability),新增能力自动出现入口;
 * - 数据与服务:数据管理(记录总量 / 清空全部)、服务与诊断(框架/端口/统计)
 *   ——两者为二级设置页入口(V10 分级,配置/数据/诊断类均不直占主设置页);
 * - 关于:版本 + 关于 Dialog。
 * 本页经应用列表筛选菜单「设置」项进入(唯一入口),返回箭头常显。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    capabilityUi: CapabilityUiRegistry,
    onBack: () -> Unit = {},
    onOpenDataSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
) {
    val context = LocalContext.current
    val config by ThemeSettingsManager.themeConfig.collectAsState()

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // 语言 sheet 状态(V9.1):点选语言先播退场动画再切换,动画期间语言不变,
    // 避免「sheet 瞬时消失 + 全屏文本同帧切换」叠影闪烁。
    val scope = rememberCoroutineScope()
    val languageSheetState = rememberModalBottomSheetState()

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

            // V10 能力注册表驱动:M19 起呈现经 CapabilityUiRegistry(图标/标题/导航),
            // 本行仅投影领域能力 + 启用态——新增能力不改本文件。
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_features_section)) {
                viewModel.capabilities.forEach { capability ->
                    val entry = capabilityUi.entryFor(capability.kind) ?: return@forEach
                    val enabled by capability.enabled.collectAsStateWithLifecycle()
                    SettingsItem(
                        icon = entry.icon,
                        title = stringResource(entry.titleRes),
                        subtitle = stringResource(
                            if (enabled) R.string.settings_capability_enabled else R.string.settings_capability_disabled
                        ),
                        onClick = entry.onClick,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.data_settings_title),
                    onClick = onOpenDataSettings,
                )
                SettingsItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.diagnostics_settings_title),
                    onClick = onOpenDiagnostics,
                )
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
}

/** 能力图标呈现已收敛至 CapabilityUiRegistry(M19)。 */

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
                            // 单选行语义(选中态 + RadioButton 角色):TalkBack 读「已选中」。
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(tag) },
                            )
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
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mode) },
                            )
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
                        .selectable(
                            selected = useWallpaper,
                            role = Role.RadioButton,
                            onClick = { onSelectWallpaper() },
                        )
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
                    val markColor = selectedMarkColor(LocalIsDarkTheme.current)
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
                                ).semantics {
                                    selected = isSelected
                                    // 色块朗读自身十六进制值(自描述,替代泛泛的「色块」)。
                                    contentDescription = "#%06X".format(argb and 0xFFFFFF)
                                }
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
    isDark: Boolean,
): Color = if (isDark) Color.White else Color.Black