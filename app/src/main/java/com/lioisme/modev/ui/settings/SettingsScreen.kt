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
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
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
 * 设置页(V10 设置分级,通用设置收敛本页;V21 T3 功能入口;V25 收敛二入口):
 * 卡片式分区(SettingsSection = surfaceContainerLow 卡),仅通用设置 + 入口:
 * - 显示:外观模式 sheet + 主题色 sheet(跟随壁纸 / 20 色预设网格);
 * - 语言与地区:语言 sheet(LocaleManager 热切换,零重建);
 * - 功能二入口:数据管理(记录总量 / 清空全部)/ 服务与诊断(框架/端口/统计)
 *   —— 算法监听 / 数据库监听两全局页于 V25 退役,监听配置唯一入口 = 每应用「监听设置」;
 * - 关于:版本行 → 独立关于页(V22 R2 升级,弃用 AlertDialog,内含开源许可入口)。
 * 本页经应用列表筛选菜单「设置」项进入(唯一入口),返回箭头常显。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit = {},
    onOpenDataSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val config by ThemeSettingsManager.themeConfig.collectAsState()

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    // 语言 sheet 状态(V9.1):点选语言先播退场动画再切换,动画期间语言不变,
    // 避免「sheet 瞬间消失 + 全屏文本同帧切换」叠影闪烁。
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
        containerColor = MaterialTheme.colorScheme.surface,
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

            // V21 T3 功能入口;V25 收敛二入口 —— 数据管理 / 服务与诊断
            // (算法监听 / 数据库监听全局页退役,监听配置唯一入口 = 每应用「监听设置」;
            // 能力注册表 UI 呈现已于 V19 退役)。已作用域应用置顶开关于 V22 R2 迁回筛选菜单。
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_features_section)) {
                SettingsItem(
                    icon = Icons.Default.Inventory,
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
                    onClick = onOpenAbout,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    currentTag: String,
    sheetState: SheetState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
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
                // V19 T6 矩阵 M10:单选行收敛为 M3 ListItem + selectable(RadioButton 角色);
                // 外留 8dp 使有效留白保持 24dp,与 sheet 标题/网格对齐。
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    headlineContent = {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingContent = {
                        Icon(
                            icon,
                            null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    trailingContent = {
                        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(tag) },
                            ),
                )
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
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
                // V19 T6 矩阵 M10:单选行收敛为 M3 ListItem + selectable(RadioButton 角色)。
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    headlineContent = {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    leadingContent = {
                        Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    },
                    trailingContent = {
                        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mode) },
                            ),
                )
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.theme_color),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(8.dp))

            // V19 T6 矩阵 M10:单选行收敛为 M3 ListItem + selectable(RadioButton 角色)。
            ListItem(
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                headlineContent = {
                    Text(
                        stringResource(R.string.system_wallpaper),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (useWallpaper) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Default.Wallpaper,
                        null,
                        tint = if (useWallpaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                },
                trailingContent = {
                    if (useWallpaper) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .selectable(
                            selected = useWallpaper,
                            role = Role.RadioButton,
                            onClick = { onSelectWallpaper() },
                        ),
            )

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
                    // 选中标记色:暗色恒白、亮色恒黑,任意 seed 下清晰可见。
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
                                    // 色块朗读自身十六进制值(自描述,替代泛滥的「色块」)。
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

/** 选中标记色:暗色模式恒白、亮色模式恒黑(与 LIDE 同规,中灰 seed 下不消失)。*/
private fun selectedMarkColor(
    isDark: Boolean,
): Color = if (isDark) Color.White else Color.Black