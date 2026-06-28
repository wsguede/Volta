package com.volta.app.ui.settings

import com.volta.app.domain.stitching.OutputResolution

data class SettingsUiState(
    val outputResolution: OutputResolution = OutputResolution.STANDARD
)
