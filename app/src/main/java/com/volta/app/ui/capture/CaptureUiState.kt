package com.volta.app.ui.capture

data class CaptureUiState(
    val isSessionActive: Boolean = false,
    val framesCaptured: Int = 0,
    val coveragePercent: Float = 0f,
    val isArReady: Boolean = false,
    val cameraPermission: CapturePermissionState = CapturePermissionState.NotRequested,
    val gpsStatus: CaptureGpsStatus = CaptureGpsStatus.Acquiring
)

sealed interface CaptureGpsStatus {
    data object Acquiring : CaptureGpsStatus
    data object Available : CaptureGpsStatus
    data object Unavailable : CaptureGpsStatus
}
