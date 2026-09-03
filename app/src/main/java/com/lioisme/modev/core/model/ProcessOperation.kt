package com.lioisme.modev.core.model

/**
 * 进程控制监听操作(V33):具体 API 动作,[kind] 归属到类别。
 *
 * 操作枚举名保持不变(与数据库 wire 协议兼容),但 KDoc 明确标注实际行为:
 * - EXIT_INTERCEPT 子类:before() 中 setResult(null) 阻止执行。
 * - CRASH_PREVENT 子类:通道 A 注入 wrapper handler,通道 B 吞掉异常。
 * - SCREEN_RECORD 子类:before() 中清除 FLAG_SECURE 位。
 */
enum class ProcessOperation(
    val kind: ProcessKind,
) {
    // ---- 退出拦截 ----

    /** System.exit(int) 调用拦截。 */
    SYSTEM_EXIT(ProcessKind.EXIT_INTERCEPT),

    /** Process.killProcess(int) 调用拦截。 */
    KILL_PROCESS(ProcessKind.EXIT_INTERCEPT),

    /** Runtime.exit(int) 调用拦截。 */
    RUNTIME_EXIT(ProcessKind.EXIT_INTERCEPT),

    // ---- 崩溃防止 ----

    /** UncaughtExceptionHandler 捕获异常。 */
    UNCAUGHT_EXCEPTION(ProcessKind.CRASH_PREVENT),

    /** AndroidRuntime.handleException 捕获异常。 */
    RUNTIME_EXCEPTION(ProcessKind.CRASH_PREVENT),

    // ---- 录屏绕过 ----

    /** MediaProjection 权限 Intent 绕过。 */
    SCREEN_CAPTURE_INTENT(ProcessKind.SCREEN_RECORD),

    /** FLAG_SECURE 检测拦截。 */
    FLAG_SECURE(ProcessKind.SCREEN_RECORD),

    /** Window.addFlags 中 FLAG_SECURE 拦截。 */
    ADD_FLAGS(ProcessKind.SCREEN_RECORD),

    /** Surface.lockCanvas 拦截。 */
    SURFACE_LOCK(ProcessKind.SCREEN_RECORD),

    /** SurfaceControl screenshot 拦截。 */
    SCREENSHOT(ProcessKind.SCREEN_RECORD),

    /** MediaProjection 权限自动授权。 */
    MEDIA_PROJECTION(ProcessKind.SCREEN_RECORD),
}
