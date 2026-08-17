package com.lioisme.modev.ui.common

import androidx.annotation.StringRes

/**
 * 跨层消息:资源 id + 格式化参数,由 UI 解析(便于 i18n)。
 * 用于应用列表 / 应用记录 / 数据管理等页面的 Snackbar 一次性提示。
 */
data class UiMessage(
    @StringRes val resId: Int,
    val args: List<String> = emptyList(),
)
