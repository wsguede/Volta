package com.volta.app.ui.capture

sealed interface CapturePermissionState {
    data object NotRequested : CapturePermissionState
    data object Granted : CapturePermissionState
    data object Denied : CapturePermissionState
    data object PermanentlyDenied : CapturePermissionState
}
