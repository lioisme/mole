package com.lioisme.modev.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/*
 * 导航门控(2026-08-15 UI 重构 Phase 3,移植自 LIDE):
 * 官方契约「仅当当前 destination 的 Lifecycle 处于 RESUMED 时才能触发导航」。
 *
 * NavHost 的 AnimatedContent 转场运行期间,进出栈的 NavBackStackEntry 仅处于
 * STARTED;此时修改导航栈会让「被 pop 的页面内容仍留在组合中可见(波纹有效、
 * 点击失效)」——navigation-compose 2.8.x 在快速进出压力下会形成不可恢复的
 * 栈/转场失步(整页失去事件响应)。门控采用入队执行:任何输入都不丢失、不乱序,
 * 且绝不在转场中修改导航栈。
 *
 * 本文件是全部导航调用的唯一门控(single funnel):
 * - [navigateGuarded] / [popGuarded]:空闲(已就绪且队列/在途为空)时立即执行;
 *   否则入队,由 [drainNavigationQueue](ModevNavHost 内常驻协程)在每次转场完成后按序执行。
 *   相邻两个 navigate 入队时后者覆盖前者(同页连点只导航一次,防止叠栈)。
 * - [navigateWhenReady] / [popWhenReady]:一次性系统事件(深链打开、非法参数回退等)
 *   挂起等待就绪后执行,与队列互斥串行(同一主线程)。
 *
 * 线程契约:全部队列访问必须发生在主线程(Compose 回调 + Main dispatcher 协程),
 * 因此 [pendingStates] 用主线程专用的 WeakHashMap,禁止后台线程触碰。
 */

/** 就绪轮询间隔(约一帧)。 */
private const val NAV_READY_POLL_INTERVAL_MS = 16L

/** 等待就绪的超时上界,超时后放弃该动作(防永久挂起)。 */
private const val NAV_READY_TIMEOUT_MS = 5_000L

/** 待执行导航动作(入队单元)。 */
private sealed interface PendingNav {
    data class Navigate(
        val route: String,
        val navOptions: NavOptions?,
    ) : PendingNav

    data object Pop : PendingNav
}

/** 每 NavHostController 一个队列状态:待执行动作 + 是否在途(已取出未执行完)。 */
private class NavPendingState {
    val queue = ArrayDeque<PendingNav>()
    var inFlight = false
}

private val pendingStates: MutableMap<NavHostController, NavPendingState> =
    Collections.synchronizedMap(WeakHashMap())

/** 当前栈顶 entry 是否已完成进出转场(官方契约的「可导航」判据)。 */
fun NavHostController.isNavigationSettled(): Boolean = currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

/** 是否存在未执行完的导航(含在途动作)——有则一切新请求必须入队以保持时序。 */
fun NavHostController.hasPendingNavigation(): Boolean =
    synchronized(pendingStates) {
        pendingStates[this]?.let { state ->
            synchronized(state) { state.queue.isNotEmpty() || state.inFlight }
        } ?: false
    }

/** 门控导航:空闲即执行;忙碌(未就绪 / 队列非空 / 在途)则入队,由 drain 按序执行。 */
fun NavHostController.navigateGuarded(
    route: String,
    navOptions: NavOptions? = null,
) {
    if (!hasPendingNavigation() && isNavigationSettled()) {
        if (navOptions != null) navigate(route, navOptions) else navigate(route)
        return
    }
    enqueue(PendingNav.Navigate(route, navOptions))
}

/** 门控弹栈:空闲即执行(根页面返回 false,由调用方兜底);忙碌则入队并返回 true(已消费)。 */
fun NavHostController.popGuarded(): Boolean {
    if (!hasPendingNavigation() && isNavigationSettled()) {
        return popBackStack()
    }
    enqueue(PendingNav.Pop)
    return true
}

/** 挂起等待当前栈顶 RESUMED 后导航;超时后放弃。 */
suspend fun NavHostController.navigateWhenReady(
    route: String,
    navOptions: NavOptions? = null,
) {
    if (!waitForNavigationSettled()) return
    if (navOptions != null) navigate(route, navOptions) else navigate(route)
}

/** 挂起等待当前栈顶 RESUMED 后弹栈;超时后放弃。 */
suspend fun NavHostController.popWhenReady() {
    if (waitForNavigationSettled()) {
        popBackStack()
    }
}

/**
 * 常驻执行队列:由 ModevNavHost 的 `LaunchedEffect(navController)` 启动,
 * 随组合销毁自动取消(取消时清空队列)。每次转场完成(RESUMED)后取一个动作执行,
 * 天然把全部导航串行化在转场边界上,从构造上杜绝「转场中修改导航栈」的失步。
 */
suspend fun NavHostController.drainNavigationQueue() {
    try {
        while (true) {
            val state = synchronized(pendingStates) { pendingStates[this] }
            val action =
                state?.let { s ->
                    synchronized(s) {
                        if (s.queue.isNotEmpty()) {
                            s.inFlight = true
                            s.queue.removeFirst()
                        } else {
                            null
                        }
                    }
                }
            if (action == null) {
                delay(NAV_READY_POLL_INTERVAL_MS)
                continue
            }
            try {
                if (!waitForNavigationSettled()) continue
                execute(action)
            } finally {
                synchronized(state!!) { state.inFlight = false }
            }
        }
    } catch (e: CancellationException) {
        synchronized(pendingStates) {
            pendingStates[this]?.let { s ->
                synchronized(s) {
                    s.queue.clear()
                    s.inFlight = false
                }
            }
        }
        throw e
    }
}

private fun NavHostController.enqueue(action: PendingNav) {
    val state =
        synchronized(pendingStates) {
            pendingStates.getOrPut(this) { NavPendingState() }
        }
    synchronized(state) {
        val last = state.queue.lastOrNull()
        if (last is PendingNav.Navigate && action is PendingNav.Navigate) {
            // 相邻 navigate 合并(同页连点 / 快速切换只导航一次,防止叠栈)。
            state.queue[state.queue.size - 1] = action
        } else {
            state.queue.addLast(action)
        }
    }
}

private fun NavHostController.execute(action: PendingNav) {
    when (action) {
        is PendingNav.Navigate -> {
            if (action.navOptions != null) navigate(action.route, action.navOptions) else navigate(action.route)
        }

        PendingNav.Pop -> {
            popBackStack()
        }
    }
}

private suspend fun NavHostController.waitForNavigationSettled(): Boolean {
    val deadline = System.currentTimeMillis() + NAV_READY_TIMEOUT_MS
    while (!isNavigationSettled()) {
        if (System.currentTimeMillis() > deadline) return false
        delay(NAV_READY_POLL_INTERVAL_MS)
    }
    return true
}
