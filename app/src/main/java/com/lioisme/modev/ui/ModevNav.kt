package com.lioisme.modev.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lioisme.modev.ui.apps.AppDetailScreen
import com.lioisme.modev.ui.apps.AppDetailViewModelFactory
import com.lioisme.modev.ui.apps.AppsScreen
import com.lioisme.modev.ui.apps.AppsViewModelFactory
import com.lioisme.modev.ui.logs.LogsScreen
import com.lioisme.modev.ui.logs.LogsViewModelFactory
import com.lioisme.modev.ui.settings.SettingsScreen
import com.lioisme.modev.ui.settings.SettingsViewModelFactory

/**
 * 根导航:底部三页(应用/日志/设置)+ 应用详情二级页。
 */
@Composable
fun ModevNav() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.lioisme.modev.ModevApp

    val tabs = listOf(
        NavTab("apps", "应用", Icons.Filled.ViewList),
        NavTab("logs", "日志", Icons.Filled.Terminal),
        NavTab("settings", "设置", Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "apps",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("apps") {
                AppsScreen(
                    viewModel = viewModel(factory = AppsViewModelFactory(app.container)),
                    onOpen = { pkg ->
                        navController.navigate("apps/$pkg") { launchSingleTop = true }
                    },
                )
            }
            composable(
                route = "apps/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
                val pkg = entry.arguments?.getString("packageName") ?: return@composable
                AppDetailScreen(
                    viewModel = viewModel(
                        key = "app-detail-$pkg",
                        factory = AppDetailViewModelFactory(pkg, app.container),
                    ),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("logs") {
                LogsScreen(viewModel = viewModel(factory = LogsViewModelFactory(app.container)))
            }
            composable("settings") {
                SettingsScreen(viewModel = viewModel(factory = SettingsViewModelFactory(app.container)))
            }
        }
    }
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)