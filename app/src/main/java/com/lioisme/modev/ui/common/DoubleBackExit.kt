package com.lioisme.modev.ui.common

/**
 * 双击返回退出状态机:根页首次返回提示「再按一次退出程序」,
 * [windowMs] 窗口内再次返回才真正退出;窗口外重新计时。
 *
 * 纯 Kotlin + 可注入时钟([now]),JVM 直测(测试金字塔第一层);
 * 挂接点 = ModevNav 根页 BackHandler 空闲分支(popGuarded 返回 false 时)。
 */
class DoubleBackExit(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastBackAt: Long? = null

    /**
     * 记录一次返回并按窗口判定:
     * true = 应退出(窗口内第二次);false = 应提示(首次或窗口已过)。
     */
    fun press(): Boolean {
        val t = now()
        val prev = lastBackAt
        lastBackAt = t
        return prev != null && t - prev < windowMs
    }

    /** 清空窗口(离开根页/重新前台时可选复位)。 */
    fun reset() {
        lastBackAt = null
    }

    companion object {
        /** 二次返回判定窗口(行业惯例 2s)。 */
        const val DEFAULT_WINDOW_MS = 2_000L
    }
}
