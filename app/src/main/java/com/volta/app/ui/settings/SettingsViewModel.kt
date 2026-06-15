package com.volta.app.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class OutputResolution(val width: Int, val height: Int, val label: String) {
    MINIMUM(4096, 2048, "Minimum (4096×2048)"),
    STANDARD(8192, 4096, "Standard (8192×4096)"),
}

data class SettingsUiState(
    val outputResolution: OutputResolution = OutputResolution.STANDARD,
)

@HiltViewModel
class SettingsViewModel
@Inject
constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setOutputResolution(resolution: OutputResolution) {
        _uiState.value = _uiState.value.copy(outputResolution = resolution)
    }
}
