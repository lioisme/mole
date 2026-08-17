package com.lioisme.modev.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.lioisme.modev.ui.apps.AlgorithmListScreen
import com.lioisme.modev.ui.apps.AlgorithmListViewModelFactory
import com.lioisme.modev.ui.apps.AppsScreen
import com.lioisme.modev.ui.apps.AppsViewModelFactory
import com.lioisme.modev.ui.apps.CaptureSettingsScreen
import com.lioisme.modev.ui.logs.RecordDetailScreen
import com.lioisme.modev.ui.logs.RecordDetailViewModelFactory
import com.lioisme.modev.ui.navigation.ModevRoutes
import com.lioisme.modev.ui.navigation.NavTransitions
import com.lioisme.modev.ui.navigation.drainNavigationQueue
import com.lioisme.modev.ui.navigation.navigateGuarded
import com.lioisme.modev.ui.navigation.popGuarded
import com.lioisme.modev.ui.navigation.popWhenReady
import com.lioisme.modev.ui.navigation.slideComposable
import com.lioisme.modev.ui.settings.SettingsScreen
import com.lioisme.modev.ui.settings.SettingsViewModelFactory
import kotlinx.coroutines.delay

/**
 * 单栈导航宿主(2026-08-16 V4,五界面收敛):
 * 信息架构 = 应用列表(根页) → 算法记录列表 → 算法详情 + 捕获设置(每应用,
 * 记录列表入口进入)+ 全局设置(设置入口 = 应用列表筛选菜单底部,见 AppsScreen)。
 * 全部目的地 slideComposable(编译器层杜绝 fade)+ 返回键统一过门控。
 * 根页(APPS)空闲返回抛回系统退出;次级页返回一律 popGuarded 回退上级。
 */
@Composable
fun ModevNavHost(navController: NavHostController) {
    val context = LocalContext.current
    val app = context.applicationContext as com.lioisme.modev.ModevApp

    // 一键启动作用域应用:getLaunchIntentForPackage 取入口,加 NEW_TASK 从宿主前台启动。
    val launchApp: (String) -> Unit = { pkg ->
        runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    // 导航队列常驻执行:随组合销毁自动取消;保证任何快速输入最终在转场边界上按序执行。
    LaunchedEffect(navController) {
        navController.drainNavigationQueue()
    }

    // 作用域前台同步:进入 RESUMED 立即刷新 + 每 5s 轮询,
    // 覆盖框架侧(LSPosed/Vector 管理器)勾选变化,UI 勾选态始终与框架一致;
    // 退后台自动取消,无后台开销。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                app.container.scopeRepository.refresh()
                delay(SCOPE_POLL_INTERVAL_MS)
            }
        }
    }

    NavHost(navController = navController, startDestination = ModevRoutes.APPS) {
        slideComposable(
            route = ModevRoutes.APPS,
            transitions = NavTransitions.QUARTER,
        ) {
            // 根页面兜底:导航忙碌(转场/队列)时返回键入队弹栈,杜绝「排队窗口内误退出」;
            // 空闲时返回 false → finish(退出 App)。
            BackHandler(enabled = true) {
                if (!navController.popGuarded()) {
                    (context as? Activity)?.finish()
                }
            }
            AppsScreen(
                viewModel = viewModel(factory = AppsViewModelFactory(app.container)),
                onOpen = { pkg ->
                    navController.navigateGuarded(ModevRoutes.algorithmList(pkg))
                },
                onOpenSettings = {
                    navController.navigateGuarded(ModevRoutes.SETTINGS)
                },
            )
        }
        slideComposable(
            route = ModevRoutes.ALGORITHM_LIST,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                AlgorithmListScreen(
                    viewModel = viewModel(
                        key = "algo-list-$pkg",
                        factory = AlgorithmListViewModelFactory(pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenCaptureSettings = {
                        navController.navigateGuarded(ModevRoutes.captureSettings(pkg))
                    },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                    onLaunch = launchApp,
                )
            }
        }
        slideComposable(
            route = ModevRoutes.CAPTURE_SETTINGS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                CaptureSettingsScreen(
                    // 与算法记录列表页共享同一 ViewModel 实例(同 key):
                    // 开关状态/作用域/操作消息单一数据源,两页同步。
                    viewModel = viewModel(
                        key = "algo-list-$pkg",
                        factory = AlgorithmListViewModelFactory(pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.RECORD_DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
        ) { entry ->
            val recordId = entry.arguments?.getLong("recordId") ?: return@slideComposable
            BackGuarded(navController) {
                RecordDetailScreen(
                    viewModel = viewModel(
                        key = "record-$recordId",
                        factory = RecordDetailViewModelFactory(recordId, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = ModevRoutes.SETTINGS) {
            BackGuarded(navController) {
                SettingsScreen(
                    viewModel = viewModel(factory = SettingsViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                )
            }
        }
    }
}

/**
 * 页面级返回键统一过门控:替代每目的地一行
 * `BackHandler(enabled = true) { navController.popGuarded() }`,
 * 转场未完成时入队、完成后执行(杜绝「转场中弹栈」失步)。
 */
@Composable
private fun BackGuarded(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = true) { navController.popGuarded() }
    content()
}

/** 作用域前台轮询间隔:5s(与框架侧变更感知的时效性折中,退后台即停)。 */
private const val SCOPE_POLL_INTERVAL_MS = 5_000L
