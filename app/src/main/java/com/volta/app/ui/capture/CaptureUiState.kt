package com.volta.app.ui.capture

data class CaptureUiState(
    val isSessionActive: Boolean = false,
    val framesCaptured: Int = 0,
    val coveragePercent: Float = 0f,
    val isArReady: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.Acquiring,
)

sealed interface GpsStatus {
    data object Acquiring : GpsStatus
    data object Available : GpsStatus
    data object Unavailable : GpsStatus
}
