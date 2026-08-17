package com.lioisme.modev.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlin.math.roundToInt

/**
 * 页面进出转场的唯一事实来源(single source of truth)。
 *
 * 产品规范:全部页面进出一律使用经典横向推入式(slide)动画,禁用渐变(fade)。
 * [NavGraphBuilder.composable] 的默认转场是交叉淡化(fadeIn/fadeOut),
 * 因此新页面一律经 [slideComposable] 注册——在编译器层杜绝 fade 回归。
 */
object NavTransitions {
    /** 转场时长:150ms(产品手感选择;快速进出由 NavigationGate 排队串行在转场边界执行)。 */
    const val ANIM_DURATION_MS = 150

    /** 全宽推入:新页从右推入、旧页向左推出;pop 反向。二级页面标准。 */
    val FULL: RouteTransitions = slide(offsetFraction = 1f)

    /** 1/4 宽度视差推入:根页面回退浏览场景,避免多层全宽推叠。 */
    val QUARTER: RouteTransitions = slide(offsetFraction = 0.25f)

    private fun slide(offsetFraction: Float): RouteTransitions =
        RouteTransitions(
            enter =
                slideInHorizontally(
                    initialOffsetX = { (it * offsetFraction).roundToInt() },
                    animationSpec = tween(ANIM_DURATION_MS),
                ),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { -(it * offsetFraction).roundToInt() },
                    animationSpec = tween(ANIM_DURATION_MS),
                ),
            popEnter =
                slideInHorizontally(
                    initialOffsetX = { -(it * offsetFraction).roundToInt() },
                    animationSpec = tween(ANIM_DURATION_MS),
                ),
            popExit =
                slideOutHorizontally(
                    targetOffsetX = { (it * offsetFraction).roundToInt() },
                    animationSpec = tween(ANIM_DURATION_MS),
                ),
        )
}

/** 单页面的四段转场(进入 / 退出 / pop 进入 / pop 退出),见 [NavTransitions]。 */
data class RouteTransitions(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
)

/**
 * 注册带经典 slide 转场的页面。
 *
 * 禁止绕过本函数直接调用 [composable]——未声明转场的路由会退回默认 fade。
 * 默认使用 [NavTransitions.FULL]。
 */
fun NavGraphBuilder.slideComposable(
    route: String,
    transitions: RouteTransitions = NavTransitions.FULL,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = { transitions.enter },
        exitTransition = { transitions.exit },
        popEnterTransition = { transitions.popEnter },
        popExitTransition = { transitions.popExit },
        content = content,
    )
}
