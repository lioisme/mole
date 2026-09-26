package com.lioisme.modev.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import com.lioisme.modev.R
import com.lioisme.modev.core.model.RecordType
import com.lioisme.modev.ui.apps.AppMonitorScreen
import com.lioisme.modev.ui.apps.AppMonitorViewModelFactory
import com.lioisme.modev.ui.apps.AppsScreen
import com.lioisme.modev.ui.apps.AppsViewModelFactory
import com.lioisme.modev.ui.apps.PerAppFeatureSettingsScreen
import com.lioisme.modev.ui.apps.PerAppProfileTuneScreen
import com.lioisme.modev.ui.apps.PerAppFeatureSettingsViewModelFactory
import com.lioisme.modev.ui.apps.PerAppListenSettingsScreen
import com.lioisme.modev.ui.apps.PerAppListenSettingsViewModelFactory
import com.lioisme.modev.ui.common.DoubleBackExit
import com.lioisme.modev.ui.detail.RecordDetailScreen
import com.lioisme.modev.ui.detail.RecordDetailViewModelFactory
import com.lioisme.modev.ui.navigation.ModevRoutes
import com.lioisme.modev.ui.navigation.NavTransitions
import com.lioisme.modev.ui.navigation.drainNavigationQueue
import com.lioisme.modev.ui.navigation.navigateGuarded
import com.lioisme.modev.ui.navigation.popGuarded
import com.lioisme.modev.ui.navigation.popWhenReady
import com.lioisme.modev.ui.navigation.slideComposable
import com.lioisme.modev.ui.records.AlgorithmRecordsScreen
import com.lioisme.modev.ui.records.GlobalTypeRecordsScreen
import com.lioisme.modev.ui.records.BehaviorRecordsScreen
import com.lioisme.modev.ui.records.DatabaseRecordsScreen
import com.lioisme.modev.ui.records.PrefsRecordsScreen
import com.lioisme.modev.ui.records.RecordsViewModelFactory
import com.lioisme.modev.ui.records.WebViewRecordsScreen
import com.lioisme.modev.ui.records.DeviceEnvRecordsScreen
import com.lioisme.modev.ui.records.NetworkRecordsScreen
import com.lioisme.modev.ui.records.UiInteractionRecordsScreen
import com.lioisme.modev.ui.records.SystemRecordsScreen
import com.lioisme.modev.ui.records.ProcessControlRecordsScreen
import com.lioisme.modev.ui.settings.AboutScreen
import com.lioisme.modev.ui.settings.DataSettingsScreen
import com.lioisme.modev.ui.settings.DataSettingsViewModelFactory
import com.lioisme.modev.ui.settings.DiagnosticsScreen
import com.lioisme.modev.ui.settings.DiagnosticsViewModelFactory
import com.lioisme.modev.ui.settings.LicenseDetailScreen
import com.lioisme.modev.ui.settings.LicensesScreen
import com.lioisme.modev.ui.settings.McpTokensScreen
import com.lioisme.modev.ui.settings.McpTokensViewModelFactory
import com.lioisme.modev.ui.settings.ServicesScreen
import com.lioisme.modev.ui.settings.ServicesViewModelFactory
import com.lioisme.modev.ui.settings.SettingsScreen
import com.lioisme.modev.ui.settings.SettingsViewModelFactory
import com.lioisme.modev.ui.settings.features.FeatureBlacklistScreen
import com.lioisme.modev.ui.settings.features.FeatureBlacklistViewModelFactory
import com.lioisme.modev.ui.settings.features.KeywordLibraryScreen
import com.lioisme.modev.ui.settings.features.KeywordLibraryViewModelFactory
import com.lioisme.modev.ui.settings.profiles.ProfileEditorScreen
import com.lioisme.modev.ui.settings.profiles.ProfileEditorViewModelFactory
import com.lioisme.modev.ui.settings.profiles.SpoofProfileLibraryScreen
import com.lioisme.modev.ui.settings.profiles.SpoofProfileLibraryViewModelFactory
import kotlinx.coroutines.launch

/**
 * 单栈导航宿主(2026-08-16 V4,五界面收敛;2026-08-17 V10 设置分级;V18 应用监听入口;
 * V19 T4 监听设置合并;V20 记录列表单应用语境化 + 「查看记录」界面退役;
 * V24 G2 records/all 退役,记录浏览收敛为每应用两列表):
 * 信息架构 = 应用列表(根页) → 应用监听入口(应用头部 + 算法记录 / 数据库记录)
 * → 记录详情;
 * 设置页 = 通用设置,入口 = 应用列表搜索条左上角设置按钮;二级设置页(算法监听 /
 * 数据库监听 / 数据管理 / 服务与诊断)经设置页进入(V21 T3 拆分还原,V19 合并单页退役)。
 * 全部目的地 slideComposable(编译器层杜绝 fade)+ 返回键统一过门控。
 * 根页(APPS)空闲返回抛回系统退出;次级页返回一律 popGuarded 回退上级。
 */
@Composable
fun ModevNavHost(navController: NavHostController) {
    val context = LocalContext.current
    // L4:防御性转型——预览/测试替身 Application 下渲染空壳,不崩溃
    val app = context.applicationContext as? com.lioisme.modev.ModevApp
    if (app == null) return

    // 一键启动作用域应用:getLaunchIntentForPackage(全 API 稳定,无弃用重载)取入口,加 NEW_TASK 从宿主前台启动。
    val launchApp: (String) -> Unit = { pkg ->
        runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    // V39 R8:根页双击返回退出——首次提示「再按一次退出程序」(Snackbar),窗口内再按 finish。
    // Snackbar 宿主上提至导航层,与 AppsScreen 消息流共用同一 SnackbarHost。
    val rootSnackbarHostState = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()
    val doubleBackExit = remember { DoubleBackExit() }

    // 导航队列常驻执行:随组合销毁自动取消;保证任何快速输入最终在转场边界上按序执行。
    LaunchedEffect(navController) {
        navController.drainNavigationQueue()
    }

    // 作用域前台同步(V10 稳态状态流):进入 RESUMED 立即刷新 + 低频静默兜底。
    // 刷新经内容判等:无变化不发射、不产生 Connecting 中间态 → UI 零闪烁零重组;
    // 退后台自动取消,无后台开销。同步策略封装于 ScopeRepository.syncForeground。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            app.container.scopeRepository.syncForeground()
        }
    }

    NavHost(navController = navController, startDestination = ModevRoutes.APPS) {
        slideComposable(
            route = ModevRoutes.APPS,
            transitions = NavTransitions.QUARTER,
        ) {
            // 根页面兜底:导航忙碌(转场/队列)时返回键入队弹栈,杜绝「排队窗口内误退出」;
            // 空闲时首次返回提示「再按一次退出程序」,窗口内再按才 finish。
            BackHandler(enabled = true) {
                if (!navController.popGuarded()) {
                    if (doubleBackExit.press()) {
                        (context as? Activity)?.finish()
                    } else {
                        rootScope.launch {
                            rootSnackbarHostState.showSnackbar(context.getString(R.string.exit_press_back_again))
                        }
                    }
                }
            }
            AppsScreen(
                viewModel = viewModel(factory = AppsViewModelFactory(app.container)),
                onOpen = { pkg ->
                    navController.navigateGuarded(ModevRoutes.appMonitor(pkg))
                },
                onOpenSettings = {
                    navController.navigateGuarded(ModevRoutes.SETTINGS)
                },
                snackbarHostState = rootSnackbarHostState,
            )
        }
        slideComposable(
            route = ModevRoutes.APP_MONITOR,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                AppMonitorScreen(
                    viewModel = viewModel(
                        key = "monitor-$pkg",
                        factory = AppMonitorViewModelFactory(pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenAlgorithm = {
                        navController.navigateGuarded(ModevRoutes.algorithmRecords(pkg))
                    },
                    onOpenDatabase = {
                        navController.navigateGuarded(ModevRoutes.databaseRecords(pkg))
                    },
                    onOpenBehavior = {
                        navController.navigateGuarded(ModevRoutes.behaviorRecords(pkg))
                    },
                    onOpenPrefs = {
                        navController.navigateGuarded(ModevRoutes.prefsRecords(pkg))
                    },
                    onOpenWebView = {
                        navController.navigateGuarded(ModevRoutes.webviewRecords(pkg))
                    },
                    onOpenNetwork = {
                        navController.navigateGuarded(ModevRoutes.networkRecords(pkg))
                    },
                    onOpenDeviceEnv = {
                        navController.navigateGuarded(ModevRoutes.deviceEnvRecords(pkg))
                    },
                    onOpenUiInteraction = {
                        navController.navigateGuarded(ModevRoutes.uiInteractionRecords(pkg))
                    },
                    onOpenSystem = {
                        navController.navigateGuarded(ModevRoutes.systemRecords(pkg))
                    },
                    onOpenProcessControl = {
                        navController.navigateGuarded(ModevRoutes.processControlRecords(pkg))
                    },
                    onOpenListenSettings = {
                        navController.navigateGuarded(ModevRoutes.appListenSettings(pkg))
                    },
                    onOpenFeatureSettings = {
                        navController.navigateGuarded(ModevRoutes.appFeatureSettings(pkg))
                    },
                    onLaunch = launchApp,
                )
            }
        }
        slideComposable(
            route = ModevRoutes.APP_FEATURE_SETTINGS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                PerAppFeatureSettingsScreen(
                    viewModel = viewModel(
                        key = "feature-settings-$pkg",
                        factory = PerAppFeatureSettingsViewModelFactory(pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenKeywordLibrary = {
                        navController.navigateGuarded(ModevRoutes.KEYWORD_LIBRARY)
                    },
                    onOpenProfileTune = {
                        navController.navigateGuarded(ModevRoutes.appProfileTune(pkg))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.APP_PROFILE_TUNE,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                PerAppProfileTuneScreen(
                    viewModel = viewModel(
                        key = "feature-settings-$pkg",
                        factory = PerAppFeatureSettingsViewModelFactory(pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.APP_LISTEN_SETTINGS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                PerAppListenSettingsScreen(
                    viewModel = viewModel(
                        key = "listen-settings-$pkg",
                        factory = PerAppListenSettingsViewModelFactory(pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.RECORD_DETAIL,
            arguments = listOf(navArgument("recordId") { type = NavType.LongType }),
        ) { entry ->
            // 参数缺失兜底:popWhenReady 在转场/队列空闲后出栈(与 APP_MONITOR 同款),
            // 杜绝「recordId 为 null → 返回空屏 + 返回键死循环」。
            val recordId = entry.arguments?.getLong("recordId") ?: run {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
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
        slideComposable(
            route = ModevRoutes.ALGORITHM_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                AlgorithmRecordsScreen(
                    viewModel = viewModel(
                        key = "records-algorithm-$pkg",
                        factory = RecordsViewModelFactory(RecordType.CRYPTO, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.DATABASE_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                DatabaseRecordsScreen(
                    viewModel = viewModel(
                        key = "records-database-$pkg",
                        factory = RecordsViewModelFactory(RecordType.DATABASE, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.BEHAVIOR_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                BehaviorRecordsScreen(
                    viewModel = viewModel(
                        key = "records-behavior-$pkg",
                        factory = RecordsViewModelFactory(RecordType.BEHAVIOR, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.PREFS_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                PrefsRecordsScreen(
                    viewModel = viewModel(
                        key = "records-prefs-$pkg",
                        factory = RecordsViewModelFactory(RecordType.PREFS, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.WEBVIEW_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                WebViewRecordsScreen(
                    viewModel = viewModel(
                        key = "records-webview-$pkg",
                        factory = RecordsViewModelFactory(RecordType.WEBVIEW, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.NETWORK_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                NetworkRecordsScreen(
                    viewModel = viewModel(
                        key = "records-network-$pkg",
                        factory = RecordsViewModelFactory(RecordType.NETWORK, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.DEVICE_ENV_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                DeviceEnvRecordsScreen(
                    viewModel = viewModel(
                        key = "records-device-env-$pkg",
                        factory = RecordsViewModelFactory(RecordType.DEVICE_ENV, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.UI_INTERACTION_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                UiInteractionRecordsScreen(
                    viewModel = viewModel(
                        key = "records-ui-interaction-$pkg",
                        factory = RecordsViewModelFactory(RecordType.UI_INTERACTION, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.SYSTEM_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                SystemRecordsScreen(
                    viewModel = viewModel(
                        key = "records-system-$pkg",
                        factory = RecordsViewModelFactory(RecordType.SYSTEM, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.PROCESS_CONTROL_RECORDS,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
        ) { entry ->
            val pkg = ModevRoutes.dec(entry.arguments?.getString("packageName"))
            if (pkg.isBlank()) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                ProcessControlRecordsScreen(
                    viewModel = viewModel(
                        key = "records-process-control-$pkg",
                        factory = RecordsViewModelFactory(RecordType.PROCESS_CONTROL, pkg, app.container),
                    ),
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(route = ModevRoutes.SETTINGS) {
            BackGuarded(navController) {
                SettingsScreen(
                    viewModel = viewModel(factory = SettingsViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                    onOpenDataSettings = {
                        navController.navigateGuarded(ModevRoutes.DATA_SETTINGS)
                    },
                    onOpenServices = {
                        navController.navigateGuarded(ModevRoutes.SERVICES_SETTINGS)
                    },
                    onOpenDiagnostics = {
                        navController.navigateGuarded(ModevRoutes.DIAGNOSTICS_SETTINGS)
                    },
                    onOpenKeywords = {
                        navController.navigateGuarded(ModevRoutes.KEYWORD_LIBRARY)
                    },
                    onOpenProfiles = {
                        navController.navigateGuarded(ModevRoutes.SPOOF_PROFILES)
                    },
                    onOpenBlacklist = {
                        navController.navigateGuarded(ModevRoutes.FEATURE_BLACKLIST)
                    },
                    onOpenAbout = {
                        navController.navigateGuarded(ModevRoutes.ABOUT)
                    },
                )
            }
        }
        slideComposable(route = ModevRoutes.KEYWORD_LIBRARY) {
            BackGuarded(navController) {
                KeywordLibraryScreen(
                    viewModel = viewModel(factory = KeywordLibraryViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = ModevRoutes.SPOOF_PROFILES) {
            BackGuarded(navController) {
                SpoofProfileLibraryScreen(
                    viewModel = viewModel(factory = SpoofProfileLibraryViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                    onOpenProfile = { profileId ->
                        navController.navigateGuarded(ModevRoutes.profileEditor(profileId))
                    },
                    onCreateProfile = { name ->
                        navController.navigateGuarded(ModevRoutes.profileDraft(name))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.PROFILE_EDITOR,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
        ) { entry ->
            val profileId = ModevRoutes.dec(entry.arguments?.getString("profileId"))
            BackGuarded(navController) {
                ProfileEditorScreen(
                    viewModel = viewModel(
                        key = "profile-editor-$profileId",
                        factory = ProfileEditorViewModelFactory(
                            profileId = profileId,
                            draftName = ModevRoutes.draftNameOf(profileId),
                            container = app.container,
                        ),
                    ),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = ModevRoutes.FEATURE_BLACKLIST) {
            BackGuarded(navController) {
                FeatureBlacklistScreen(
                    viewModel = viewModel(factory = FeatureBlacklistViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = ModevRoutes.ABOUT) {
            BackGuarded(navController) {
                AboutScreen(
                    onBack = { navController.popGuarded() },
                    onOpenLicenses = {
                        navController.navigateGuarded(ModevRoutes.LICENSES)
                    },
                )
            }
        }
        slideComposable(route = ModevRoutes.LICENSES) {
            BackGuarded(navController) {
                LicensesScreen(
                    onBack = { navController.popGuarded() },
                    onOpenDetail = { index ->
                        navController.navigateGuarded(ModevRoutes.licenseDetail(index))
                    },
                )
            }
        }
        slideComposable(
            route = ModevRoutes.LICENSE_DETAIL,
            arguments = listOf(navArgument("index") { type = NavType.IntType }),
        ) { entry ->
            val index = entry.arguments?.getInt("index") ?: run {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                LicenseDetailScreen(
                    index = index,
                    onBack = { navController.popGuarded() },
                    onInvalid = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = ModevRoutes.DATA_SETTINGS) {
            BackGuarded(navController) {
                DataSettingsScreen(
                    viewModel = viewModel(factory = DataSettingsViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                    onOpenTypeRecords = { type ->
                        navController.navigateGuarded(ModevRoutes.globalTypeRecords(type.wire))
                    },
                    // V41 T5:按应用浏览退役——onOpenApp 接线拆除,应用维度唯一入口 = 主列表。
                )
            }
        }
        // V40 T4:按类型全局记录列表(类型 × 全部应用;数据管理页类型计数行入口)。
        slideComposable(
            route = ModevRoutes.GLOBAL_TYPE_RECORDS,
            arguments = listOf(navArgument("recordType") { type = NavType.StringType }),
        ) { entry ->
            val type = ModevRoutes.dec(entry.arguments?.getString("recordType"))
                ?.let { wire -> RecordType.entries.firstOrNull { it.wire == wire } }
            if (type == null) {
                LaunchedEffect(Unit) { navController.popWhenReady() }
                return@slideComposable
            }
            BackGuarded(navController) {
                GlobalTypeRecordsScreen(
                    viewModel = viewModel(
                        key = "records-global-${type.wire}",
                        factory = RecordsViewModelFactory(type, null, app.container),
                    ),
                    type = type,
                    onBack = { navController.popGuarded() },
                    onOpenRecord = { id ->
                        navController.navigateGuarded(ModevRoutes.recordDetail(id))
                    },
                )
            }
        }
        slideComposable(route = ModevRoutes.SERVICES_SETTINGS) {
            BackGuarded(navController) {
                ServicesScreen(
                    viewModel = viewModel(factory = ServicesViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                    onOpenTokens = {
                        navController.navigateGuarded(ModevRoutes.MCP_TOKENS)
                    },
                )
            }
        }
        slideComposable(route = ModevRoutes.MCP_TOKENS) {
            BackGuarded(navController) {
                McpTokensScreen(
                    viewModel = viewModel(factory = McpTokensViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                )
            }
        }
        slideComposable(route = ModevRoutes.DIAGNOSTICS_SETTINGS) {
            BackGuarded(navController) {
                DiagnosticsScreen(
                    viewModel = viewModel(factory = DiagnosticsViewModelFactory(app.container)),
                    onBack = { navController.popGuarded() },
                )
            }
        }
    }
}

/**
 * 页面级返回键统一过门控:替代每目的地一行
 * `BackHandler(enabled = true) { navController.popGuarded }`,
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
