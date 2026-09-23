package com.lioisme.modev.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lioisme.modev.core.model.DeviceEnvKind

/*
 * 设备环境类别色 token(V31 统一 dual-map 架构;V35 momo 增强 8 项补全):
 * 与算法/SQL/行为/偏好/WebView/网络色板同规——浅/深两套调色板,基于 Material
 * 扩展色板选取,保证类别色文本与淡底徽章在对应表面上的对比度 ≥ AA(≥4.5:1)。
 * 用法:deviceEnvKindColor(kind) 经 LocalIsDarkTheme 自动取浅/深;
 * 徽章底 = 语义色 @13% alpha(RecordRow 徽章统一透明度)。
 *
 * 色组分配:
 * - Root(红) / Xposed(橙) / Emulator(紫) / SysProp(青) / Proc(蓝灰) / Detect(灰)
 */

/** 浅色模式设备环境类别色。 */
private val deviceEnvLightPalette: Map<DeviceEnvKind, Color> =
    mapOf(
        // Root 检测干预(红)
        DeviceEnvKind.ROOT_FILE to Color(0xFFB3261E),
        DeviceEnvKind.ROOT_EXEC to Color(0xFFB3261E),
        DeviceEnvKind.ROOT_PACKAGE_MANAGER to Color(0xFFB3261E),
        DeviceEnvKind.ROOT_PROPERTY to Color(0xFFB3261E),
        // Xposed 检测干预(橙)
        DeviceEnvKind.XPOSED_CLASS to Color(0xFF8A4D00),
        DeviceEnvKind.XPOSED_STACK to Color(0xFF8A4D00),
        DeviceEnvKind.XPOSED_MAPS to Color(0xFF8A4D00),
        // 模拟器检测干预(紫)
        DeviceEnvKind.EMULATOR_BUILD to Color(0xFF7A3F8F),
        DeviceEnvKind.EMULATOR_FILE to Color(0xFF7A3F8F),
        DeviceEnvKind.EMULATOR_SENSOR to Color(0xFF7A3F8F),
        DeviceEnvKind.EMULATOR_NETWORK to Color(0xFF7A3F8F),
        DeviceEnvKind.EMULATOR_TELEPHONY to Color(0xFF7A3F8F),
        DeviceEnvKind.EMULATOR_CPU to Color(0xFF7A3F8F),
        DeviceEnvKind.EMULATOR_BATTERY to Color(0xFF7A3F8F),
        // 系统属性 Hook(青)
        DeviceEnvKind.SYSTEM_PROPERTIES to Color(0xFF006B5E),
        // 进程文件过滤(蓝灰)
        DeviceEnvKind.PROC_STATUS to Color(0xFF3D5A80),
        DeviceEnvKind.PROC_MOUNT_INFO to Color(0xFF3D5A80),
        DeviceEnvKind.PROC_CGROUP to Color(0xFF3D5A80),
        DeviceEnvKind.PROC_NET to Color(0xFF3D5A80),
        // 检测行为监控(灰)
        DeviceEnvKind.DETECT_ROOT to Color(0xFF495057),
        DeviceEnvKind.DETECT_EMULATOR to Color(0xFF495057),
        // 画像强制档(青绿:与条件档的紫/蓝灰区分开 —— 记录列表里「按画像改写」与「掩蔽脏值」
        // 是两种不同的用户意图,颜色不得撞)
        DeviceEnvKind.PROFILE_BUILD_IDENTITY to Color(0xFF0F766E),
        DeviceEnvKind.PROFILE_BUILD_VERSION to Color(0xFF0F766E),
    )

/** 深色模式设备环境类别色。 */
private val deviceEnvDarkPalette: Map<DeviceEnvKind, Color> =
    mapOf(
        // Root 检测干预(红)
        DeviceEnvKind.ROOT_FILE to Color(0xFFF2B8B5),
        DeviceEnvKind.ROOT_EXEC to Color(0xFFF2B8B5),
        DeviceEnvKind.ROOT_PACKAGE_MANAGER to Color(0xFFF2B8B5),
        DeviceEnvKind.ROOT_PROPERTY to Color(0xFFF2B8B5),
        // Xposed 检测干预(橙)
        DeviceEnvKind.XPOSED_CLASS to Color(0xFFFFB980),
        DeviceEnvKind.XPOSED_STACK to Color(0xFFFFB980),
        DeviceEnvKind.XPOSED_MAPS to Color(0xFFFFB980),
        // 模拟器检测干预(紫)
        DeviceEnvKind.EMULATOR_BUILD to Color(0xFFDBA3EC),
        DeviceEnvKind.EMULATOR_FILE to Color(0xFFDBA3EC),
        DeviceEnvKind.EMULATOR_SENSOR to Color(0xFFDBA3EC),
        DeviceEnvKind.EMULATOR_NETWORK to Color(0xFFDBA3EC),
        DeviceEnvKind.EMULATOR_TELEPHONY to Color(0xFFDBA3EC),
        DeviceEnvKind.EMULATOR_CPU to Color(0xFFDBA3EC),
        DeviceEnvKind.EMULATOR_BATTERY to Color(0xFFDBA3EC),
        // 系统属性 Hook(青)
        DeviceEnvKind.SYSTEM_PROPERTIES to Color(0xFF80CBC4),
        // 进程文件过滤(蓝灰)
        DeviceEnvKind.PROC_STATUS to Color(0xFF90B4CE),
        DeviceEnvKind.PROC_MOUNT_INFO to Color(0xFF90B4CE),
        DeviceEnvKind.PROC_CGROUP to Color(0xFF90B4CE),
        DeviceEnvKind.PROC_NET to Color(0xFF90B4CE),
        // 检测行为监控(灰)
        DeviceEnvKind.DETECT_ROOT to Color(0xFFB2BAC2),
        DeviceEnvKind.DETECT_EMULATOR to Color(0xFFB2BAC2),
        // 画像强制档(青绿暗档变体,与浅色侧同一族相)
        DeviceEnvKind.PROFILE_BUILD_IDENTITY to Color(0xFF5EEAD4),
        DeviceEnvKind.PROFILE_BUILD_VERSION to Color(0xFF5EEAD4),
    )

/** 设备环境类别语义色(非 Composable 访问器,UI 与对比度门禁测试同源)。 */
internal fun deviceEnvKindColorForMode(kind: DeviceEnvKind, isDark: Boolean): Color =
    if (isDark) deviceEnvDarkPalette.getValue(kind) else deviceEnvLightPalette.getValue(kind)

/** 设备环境类别语义色(随外观模式自动切换)。 */
@Composable
fun deviceEnvKindColor(kind: DeviceEnvKind): Color =
    deviceEnvKindColorForMode(kind, LocalIsDarkTheme.current)
