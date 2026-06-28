package com.volta.app.ui.capture

sealed interface CameraPermissionState {
    data object NotRequested : CameraPermissionState
    data object Granted : CameraPermissionState
    data object Denied : CameraPermissionState
    data object PermanentlyDenied : CameraPermissionState
}
