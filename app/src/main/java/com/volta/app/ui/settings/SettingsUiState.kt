package com.volta.app.ui.settings

data class SettingsUiState(
    val outputResolution: OutputResolution = OutputResolution.STANDARD,
)

enum class OutputResolution(val width: Int, val height: Int, val label: String) {
    MINIMUM(4096, 2048, "Minimum (4096 × 2048)"),
    STANDARD(8192, 4096, "Standard (8192 × 4096)"),
}
