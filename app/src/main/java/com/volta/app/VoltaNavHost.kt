package com.volta.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.volta.app.ui.capture.CaptureScreen
import com.volta.app.ui.export.ExportScreen
import com.volta.app.ui.settings.SettingsScreen

private const val ROUTE_CAPTURE = "capture"
private const val ROUTE_EXPORT = "export"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun VoltaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_CAPTURE) {
        composable(ROUTE_CAPTURE) {
            CaptureScreen(
                onNavigateToExport = { navController.navigate(ROUTE_EXPORT) },
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_EXPORT) {
            ExportScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
