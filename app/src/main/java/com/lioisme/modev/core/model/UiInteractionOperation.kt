package com.lioisme.modev.core.model

/**
 * UI 交互监听操作:具体 API 动作,[kind] 归属到类别。
 * kind = 用户开关粒度([HookSwitch.uiInteractionEnabled] 门控),
 * operation = 列表元行/详情页展示粒度。
 */
enum class UiInteractionOperation(
    val kind: UiInteractionKind,
) {
    // ---- 弹窗 ----

    /** Dialog.show 调用。 */
    DIALOG_SHOW(UiInteractionKind.DIALOG_SHOW),

    /** Dialog.dismiss 调用。 */
    DIALOG_DISMISS(UiInteractionKind.DIALOG_SHOW),

    /** Dialog.cancel 调用。 */
    DIALOG_CANCEL(UiInteractionKind.DIALOG_SHOW),

    /** 关键字匹配命中,弹窗被拦截。 */
    DIALOG_INTERCEPTED(UiInteractionKind.DIALOG_KEYWORD),

    /** 关键字匹配未命中,弹窗放行。 */
    DIALOG_PASSED(UiInteractionKind.DIALOG_KEYWORD),

    // ---- 控件文本 ----

    /** TextView.setText(CharSequence) 调用。 */
    WIDGET_SET_TEXT(UiInteractionKind.WIDGET_TEXT),

    /** TextView.setText(int) 资源 ID 赋值。 */
    WIDGET_SET_TEXT_RES(UiInteractionKind.WIDGET_TEXT),

    // ---- Activity 跳转 ----

    /** Activity.startActivity(Intent) 调用。 */
    ACTIVITY_START_INTENT(UiInteractionKind.ACTIVITY_START),

    /** Activity.startActivity(Intent, Bundle) 调用。 */
    ACTIVITY_START_OPTIONS(UiInteractionKind.ACTIVITY_START),

    /** Activity.finish 调用。 */
    ACTIVITY_FINISH(UiInteractionKind.ACTIVITY_START),

    /** Activity.onCreate 调用。 */
    ACTIVITY_ON_CREATE(UiInteractionKind.ACTIVITY_RESUME),

    /** Activity.onResume 调用。 */
    ACTIVITY_ON_RESUME(UiInteractionKind.ACTIVITY_RESUME),

    /** Activity.onPause 调用。 */
    ACTIVITY_ON_PAUSE(UiInteractionKind.ACTIVITY_RESUME),

    /** Activity.onStop 调用。 */
    ACTIVITY_ON_STOP(UiInteractionKind.ACTIVITY_RESUME),

    /** Activity.onDestroy 调用。 */
    ACTIVITY_ON_DESTROY(UiInteractionKind.ACTIVITY_RESUME),
}
