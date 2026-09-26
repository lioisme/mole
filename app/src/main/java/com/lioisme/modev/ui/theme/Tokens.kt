package com.lioisme.modev.ui.theme

/**
 * 视觉语义 token:全 App 复用的 alpha/尺寸魔法数单一事实源,
 * 消灭散落字面量。取值经亮/暗双主题人工走查定档。
 */
object Tokens {
    /** 状态胶囊容器 alpha(tonal pill)。 */
    const val pillContainerAlpha = 0.15f

    /** 类别徽章容器 alpha(记录行 leading 徽章)。 */
    const val badgeContainerAlpha = 0.13f

    /** 弱化图标 alpha(chevron 等导航指示)。 */
    const val chevronAlpha = 0.5f

    /** 禁用态内容 alpha(M3 onSurface 38% 惯例)。 */
    const val disabledContentAlpha = 0.38f

    /** 空态图标容器 alpha。 */
    const val emptyIconContainerAlpha = 0.08f

    /** 空态图标 alpha。 */
    const val emptyIconAlpha = 0.5f

    /** 空态副文 alpha。 */
    const val emptyIconHintAlpha = 0.7f

    /** 代码块描边 alpha(outlineVariant)。 */
    const val codeBorderAlpha = 0.4f

    /** 主题色板未选中色点描边 alpha(surfaceVariant,V42 T4 回退 v8 定档)。 */
    const val swatchBorderAlpha = 0.3f
}
