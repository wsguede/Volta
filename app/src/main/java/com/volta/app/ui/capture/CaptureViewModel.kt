package com.volta.app.ui.capture

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class CaptureViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun startSession() {
        _uiState.update { it.copy(isSessionActive = true) }
    }

    fun stopSession() {
        _uiState.update { it.copy(isSessionActive = false) }
    }

    fun onCameraPermissionResult(granted: Boolean, isPermanentlyDenied: Boolean) {
        val permission = when {
            granted -> CameraPermissionState.Granted
            isPermanentlyDenied -> CameraPermissionState.PermanentlyDenied
            else -> CameraPermissionState.Denied
        }
        _uiState.update { it.copy(cameraPermission = permission) }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val status = if (granted) CaptureGpsStatus.Acquiring else CaptureGpsStatus.Unavailable
        _uiState.update { it.copy(gpsStatus = status) }
    }
}
