package com.volta.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.volta.app.ui.capture.CaptureScreen
import com.volta.app.ui.export.ExportScreen
import com.volta.app.ui.settings.SettingsScreen

object Routes {
    const val CAPTURE = "capture"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
}

@Composable
fun VoltaNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CAPTURE) {
        composable(Routes.CAPTURE) {
            CaptureScreen(
                onExport = { navController.navigate(Routes.EXPORT) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EXPORT) {
            ExportScreen(
                onFinished = {
                    navController.popBackStack(Routes.CAPTURE, inclusive = false)
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
