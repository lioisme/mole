package com.lioisme.modev.core.model

/**
 * 进程控制监听类别:覆盖退出检测/崩溃检测/录屏检测三大子域。
 *
 * 默认启用态与 [CategoryTaxonomy] 噪点分级一致:
 * - 全部默认关(干预型 hook,安全优先,需用户显式开启)。
 *
 * 注意:各子类 Hooker 的实际行为在 V34 审计后分为两类:
 * - 真正干预型:ExitInterceptHooker/CrashPreventHooker/ScreenRecordHooker
 *   在 before 中调用 setResult 阻止原始方法执行,阻止信息通过
 *   [ProcessRecord.interventionAction] 字段记录("BLOCKED"|"DETECTED")。
 * - 纯监控型:仅在 after 中记录事件,不修改返回值。
 */
enum class ProcessKind {
    // ---- 退出检测/拦截 ----

    /** System.exit / Process.killProcess 检测与拦截(干预型,默认关)。 */
    EXIT_INTERCEPT,

    // ---- 崩溃检测/预防 ----

    /** 未捕获异常检测与预防(干预型,默认关)。 */
    CRASH_PREVENT,

    // ---- 录屏检测/绕过 ----

    /** 屏幕录屏限制检测与解除(MediaProjection / FLAG_SECURE,干预型,默认关)。 */
    SCREEN_RECORD,
}
