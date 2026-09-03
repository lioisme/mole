package com.lioisme.modev.core.model

/**
 * UI 交互监听类别(V33):覆盖弹窗/控件文本/Activity 跳转三大子域。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致:
 * - 高信号默认开:DIALOG_SHOW/DIALOG_KEYWORD/ACTIVITY_START;
 * - 高频噪声默认关:WIDGET_TEXT/ACTIVITY_RESUME。
 */
enum class UiInteractionKind {
    // ---- 弹窗监听 ----

    /** Dialog.show() 记录。 */
    DIALOG_SHOW,

    /** 关键字弹窗拦截记录。 */
    DIALOG_KEYWORD,

    // ---- 控件文本 ----

    /** TextView.setText() 文本赋值记录(极高频噪声,默认关)。 */
    WIDGET_TEXT,

    // ---- Activity 跳转 ----

    /** Activity 启动(startActivity)记录。 */
    ACTIVITY_START,

    /** Activity 恢复(onResume)记录。 */
    ACTIVITY_RESUME,
}
