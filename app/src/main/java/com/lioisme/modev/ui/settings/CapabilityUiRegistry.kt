package com.lioisme.modev.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.lioisme.modev.domain.capability.CapabilityKind

/**
 * 能力 → UI 呈现注册表(M19,2026-08-17):图标 / 标题 / 导航动作按能力注册,
 * 消灭 SettingsScreen 内 when(CapabilityKind) 硬编码——新增能力 = 注册一行,
 * 设置页「功能」区入口与导航自动驱动,主链路零改动。
 * 与领域 [com.lioisme.modev.domain.capability.CapabilityRegistry] 分置:
 * 领域侧管能力本身,本表只承载呈现(Compose 依赖不进入 domain)。
 */
class CapabilityUiRegistry {

    data class Entry(
        val kind: CapabilityKind,
        val icon: ImageVector,
        val titleRes: Int,
        val onClick: () -> Unit,
    )

    private val entries = HashMap<CapabilityKind, Entry>()

    /** 注册(同 kind 后注册覆盖,幂等)。 */
    fun register(entry: Entry) {
        entries[entry.kind] = entry
    }

    fun entryFor(kind: CapabilityKind): Entry? = entries[kind]

    /** 已注册呈现,按能力声明顺序(枚举序)稳定输出。 */
    fun all(): List<Entry> = entries.values.sortedBy { it.kind.ordinal }
}