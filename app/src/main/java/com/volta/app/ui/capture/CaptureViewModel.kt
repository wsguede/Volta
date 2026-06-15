package com.volta.app.ui.capture

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class CaptureUiState(
    val coveragePercent: Float = 0f,
    val isGpsAvailable: Boolean = true,
    val framesCaptured: Int = 0,
    val isCapturing: Boolean = false,
)

@HiltViewModel
class CaptureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()
}
