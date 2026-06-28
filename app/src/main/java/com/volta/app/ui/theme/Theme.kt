package com.volta.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VoltaColorScheme = darkColorScheme(
    primary = VoltaPrimary,
    onPrimary = VoltaOnPrimary,
    secondary = VoltaSecondary,
    onSecondary = VoltaOnSecondary,
    background = VoltaBackground,
    onBackground = VoltaOnBackground,
    surface = VoltaSurface,
    onSurface = VoltaOnSurface,
    error = VoltaError
)

@Composable
fun VoltaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VoltaColorScheme,
        typography = VoltaTypography,
        content = content
    )
}
