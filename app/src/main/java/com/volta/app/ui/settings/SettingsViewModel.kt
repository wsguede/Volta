package com.volta.app.ui.settings

import androidx.lifecycle.ViewModel
import com.volta.app.domain.stitching.OutputResolution
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setResolution(resolution: OutputResolution) {
        _uiState.value = _uiState.value.copy(outputResolution = resolution)
    }
}
