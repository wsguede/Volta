package com.volta.app.ui.capture

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun startSession() {
        _uiState.value = _uiState.value.copy(isSessionActive = true)
    }

    fun stopSession() {
        _uiState.value = _uiState.value.copy(isSessionActive = false)
    }
}
