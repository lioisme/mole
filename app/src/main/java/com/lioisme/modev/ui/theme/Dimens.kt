package com.lioisme.modev.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 布局间距 token(V37 T4.1):全 App 间距双轨制单一事实源——
 * 设置类页面(设置/数据/诊断/关于/全局功能页)分区间 24dp;
 * 每应用页(监控入口/监听设置/功能设置)信息密度优先,分区间 16dp;
 * 页尾留白统一 24dp,记录行内边距 16dp。禁止在页面内散落布局 dp 字面量。
 */
object Dimens {
    /** 设置类页面分区间距。 */
    val sectionGap: Dp = 24.dp

    /** 每应用页分区间距(密集列表)。 */
    val sectionGapDense: Dp = 16.dp

    /** 页面水平内边距。 */
    val pagePadding: Dp = 16.dp

    /** 页尾留白。 */
    val pageBottom: Dp = 24.dp

    /** 列表/卡片行内边距。 */
    val rowPadding: Dp = 16.dp
}
