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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.lioisme.modev.ui.components.ModevSheet
import com.lioisme.modev.ui.components.ModevTopBar
import com.lioisme.modev.ui.components.SettingsItem
import com.lioisme.modev.ui.components.SettingsSection
import com.lioisme.modev.ui.theme.AppearanceMode
import com.lioisme.modev.ui.theme.LocalIsDarkTheme
import com.lioisme.modev.ui.theme.ThemeConfig
import com.lioisme.modev.ui.theme.ThemeSeeds
import com.lioisme.modev.ui.theme.ThemeSettingsManager
import com.lioisme.modev.ui.theme.Tokens

// 主题色预设单一事实源 = ThemeSeeds.OPTIONS(ui/theme)。
private val colorOptions: List<Long> = ThemeSeeds.OPTIONS

/**
 * 色板选中标记色(V42 T4 回退 v8 helper):暗色模式恒白、亮色模式恒黑,
 * 任意 seed 色点(含黑/白灰阶)下勾选边框与图标均清晰可见。
 */
private fun selectedMarkColor(isDark: Boolean): Color = if (isDark) Color.White else Color.Black

/**
 * 设置页(V10 设置分级,通用设置收敛本页;V21 T3 功能入口;V25 收敛二入口):
 * 卡片式分区(SettingsSection = surfaceContainerLow 卡),仅通用设置 + 入口:
 * - 显示:外观模式 sheet + 主题色 sheet(跟随壁纸 / 20 色预设网格);
 * - 语言与地区:语言 sheet(LocaleManager 热切换,零重建);
 * - 功能入口:数据管理(记录总量 / 清空全部)/ 服务(wire/MCP 配置)/ 诊断(运行时观测)
 *   —— V39「服务与诊断」按功能类型拆分;算法监听 / 数据库监听两全局页于 V25 退役,
 *   监听配置唯一入口 = 每应用「监听设置」;
 * - 关于:版本行 → 独立关于页(V22 R2 升级,弃用 AlertDialog,内含开源许可入口)。
 * 本页经应用列表筛选菜单「设置」项进入(唯一入口),返回箭头常显。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit = {},
    onOpenDataSettings: () -> Unit = {},
    onOpenServices: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenKeywords: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onOpenBlacklist: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val context = LocalContext.current
    val config by ThemeSettingsManager.themeConfig.collectAsState()

    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    // 语言标签收集 localeFlow(V9 热切换:切换即时刷新,零重建)。
    val currentLanguageTag by LocaleManager.localeFlow.collectAsStateWithLifecycle()
    val languageSubtitle =
        when (currentLanguageTag) {
            LocaleManager.TAG_SYSTEM -> stringResource(R.string.system_default)
            LocaleManager.TAG_EN -> stringResource(R.string.lang_en)
            LocaleManager.TAG_ZH -> stringResource(R.string.lang_zh)
            LocaleManager.TAG_JA -> stringResource(R.string.lang_ja)
            LocaleManager.TAG_KO -> stringResource(R.string.lang_ko)
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
                // V45 T3.7 同规:屏级锚点(插桩导航链的「到哪一屏」判定,禁按文案反查)。
                .testTag("settings-page")
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            // V41 T6 根页 IA 重排:分区按归属归类——通用(显示/语言)/ 数据 / 服务与诊断 /
            // 功能域(全局资产)/ 关于;行内容、路由、回调零变化,仅分区容器重组。
            SettingsSection(stringResource(R.string.settings_section_general)) {
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
            // V39:「服务与诊断」按功能类型拆分为「服务」(配置面)与「诊断」(运行时观测)。
            // V41 T6:按归属重排为「数据」「服务与诊断」两分区。
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_section_data)) {
                SettingsItem(
                    icon = Icons.Default.Inventory,
                    title = stringResource(R.string.data_settings_title),
                    onClick = onOpenDataSettings,
                )
            }

            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_section_services)) {
                SettingsItem(
                    icon = Icons.Default.Dns,
                    title = stringResource(R.string.services_settings_title),
                    onClick = onOpenServices,
                )
                SettingsItem(
                    icon = Icons.Default.Insights,
                    title = stringResource(R.string.diagnostics_settings_title),
                    onClick = onOpenDiagnostics,
                )
            }

            // V37 T3.5 功能域全局资产:关键词库 / 伪装画像 / 检测黑名单
            // (V41 T6:分区名「拦截与伪装」→「功能域」,对齐双域词汇表 Feature Domain)
            Spacer(Modifier.height(24.dp))
            SettingsSection(stringResource(R.string.settings_section_feature_domain)) {
                SettingsItem(
                    icon = Icons.Default.Chat,
                    title = stringResource(R.string.settings_keywords_entry),
                    subtitle = stringResource(R.string.settings_keywords_entry_hint),
                    onClick = onOpenKeywords,
                )
                SettingsItem(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.settings_profiles_entry),
                    subtitle = stringResource(R.string.settings_profiles_entry_hint),
                    onClick = onOpenProfiles,
                )
                SettingsItem(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.settings_blacklist_entry),
                    subtitle = stringResource(R.string.settings_blacklist_entry_hint),
                    onClick = onOpenBlacklist,
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
            onSelect = { tag ->
                // V9.1 无闪切换:点选先播 sheet 退场动画(ModevSheet 收口),动画完成后
                // 再切语言 + 移除组合——避免「sheet 窗口瞬间消失 + 全屏文本同帧切换」
                // 叠影闪烁(实机复现 2026-08-17)。
                LocaleManager.setLanguage(context, tag)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
        )
    }
}

/** 语言选择 sheet(V39 收编统一 [ModevSheet] 脚手架;V9.1 hide 后切换语义由 dismissWith 保证)。 */
@Composable
private fun LanguageSheet(
    currentTag: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModevSheet(
        title = stringResource(R.string.language),
        onDismiss = onDismiss,
    ) { sheet ->
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Spacer(Modifier.height(8.dp))
            IconChoiceRow(
                icon = Icons.Default.PhoneAndroid,
                label = stringResource(R.string.system_default),
                selected = currentTag == LocaleManager.TAG_SYSTEM,
                onClick = { sheet.dismissWith { onSelect(LocaleManager.TAG_SYSTEM) } },
            )
            IconChoiceRow(
                icon = Icons.Default.Language,
                label = stringResource(R.string.lang_en),
                selected = currentTag == LocaleManager.TAG_EN,
                onClick = { sheet.dismissWith { onSelect(LocaleManager.TAG_EN) } },
            )
            IconChoiceRow(
                icon = Icons.Default.Language,
                label = stringResource(R.string.lang_zh),
                selected = currentTag == LocaleManager.TAG_ZH,
                onClick = { sheet.dismissWith { onSelect(LocaleManager.TAG_ZH) } },
            )
            // V42 T5:日/韩新增(V42 R5;选择项与 SUPPORTED_TAGS/locales_config 同表)。
            IconChoiceRow(
                icon = Icons.Default.Language,
                label = stringResource(R.string.lang_ja),
                selected = currentTag == LocaleManager.TAG_JA,
                onClick = { sheet.dismissWith { onSelect(LocaleManager.TAG_JA) } },
            )
            IconChoiceRow(
                icon = Icons.Default.Language,
                label = stringResource(R.string.lang_ko),
                selected = currentTag == LocaleManager.TAG_KO,
                onClick = { sheet.dismissWith { onSelect(LocaleManager.TAG_KO) } },
            )
        }
    }
}

/** sheet 单选行(V19 T6 矩阵 M10:ListItem + selectable RadioButton 角色;leading 图标 + 尾部勾选)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconChoiceRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
                    onClick = onClick,
                ),
    )
}

/** 外观模式选择 sheet(V39 收编统一脚手架,修复此前 onDismiss 直连无退场动画偏差)。 */
@Composable
private fun AppearanceSheet(
    current: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModevSheet(
        title = stringResource(R.string.appearance),
        onDismiss = onDismiss,
    ) { sheet ->
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
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
                IconChoiceRow(
                    icon = icon,
                    label = label,
                    selected = mode == current,
                    onClick = { sheet.dismissWith { onSelect(mode) } },
                )
            }
        }
    }
}

/** 主题色 sheet(V39 收编统一脚手架;壁纸行 + 20 色预设网格,选择即写即生效不关闭)。 */
@Composable
private fun ColorSheet(
    useWallpaper: Boolean,
    selectedColor: Color?,
    onSelectWallpaper: () -> Unit,
    onSelectColor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModevSheet(
        title = stringResource(R.string.theme_color),
        onDismiss = onDismiss,
        modifier = Modifier.testTag("theme-color-sheet"),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Spacer(Modifier.height(8.dp))
            IconChoiceRow(
                icon = Icons.Default.Wallpaper,
                label = stringResource(R.string.system_wallpaper),
                selected = useWallpaper,
                onClick = { onSelectWallpaper() },
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

            // V42 T4:色板回退 v8 原版形态(5 列圆形色点网格 + selectedMarkColor
            // 对比安全选中标记,任意 seed 下清晰可见);V39 FilterChip + hex 文本
            // 标签形态退役(禁用旧词)。20 色固定数量 → 非惰性 chunked(5) × Row
            // (ModevSheet 纯 Column 容器,禁嵌套惰性布局)。
            // V46 R2/D7:横纵步长共用 [ColorSwatchGap] 单点 —— 旧实装只有 Row 的横向
            // spacedBy,Column 无 verticalArrangement ⇒ 四行色点上下相切。
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ColorSwatchGap),
            ) {
                colorOptions.chunked(5).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ColorSwatchGap),
                    ) {
                        rowColors.forEach { argb ->
                            val color = Color(argb)
                            val isSelected = !useWallpaper && color == selectedColor
                            // 选中标记色:暗色恒白、亮色恒黑(与 LIDE 同规,中灰 seed 下不消失)。
                            val markColor = selectedMarkColor(LocalIsDarkTheme.current)
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .testTag("color-swatch")
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(3.dp, markColor, CircleShape)
                                            } else {
                                                Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = Tokens.swatchBorderAlpha),
                                                    CircleShape,
                                                )
                                            },
                                        )
                                        .semantics { selected = isSelected }
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
                        // 尾行不满 5 列时以 weight 占位保持等宽网格节奏。
                        repeat(5 - rowColors.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** 色板网格步长(横 = 纵;V46 R2/D7 单点,[ColorSheet] 两向 spacedBy 共用)。 */
private val ColorSwatchGap = 12.dp
