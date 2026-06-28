package com.volta.app.ui.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureViewModelTest {

    @Test
    fun `initial state has session inactive`() {
        val viewModel = CaptureViewModel()
        assertThat(viewModel.uiState.value.isSessionActive).isFalse()
    }

    @Test
    fun `startSession sets session active`() {
        val viewModel = CaptureViewModel()
        viewModel.startSession()
        assertThat(viewModel.uiState.value.isSessionActive).isTrue()
    }

    @Test
    fun `stopSession sets session inactive`() {
        val viewModel = CaptureViewModel()
        viewModel.startSession()
        viewModel.stopSession()
        assertThat(viewModel.uiState.value.isSessionActive).isFalse()
    }

    // Camera permission state machine

    @Test
    fun `initial camera permission is NotRequested`() {
        val viewModel = CaptureViewModel()
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CameraPermissionState.NotRequested)
    }

    @Test
    fun `camera permission granted transitions to Granted`() {
        val viewModel = CaptureViewModel()
        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = false)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CameraPermissionState.Granted)
    }

    @Test
    fun `camera permission denied transitions to Denied`() {
        val viewModel = CaptureViewModel()
        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = false)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CameraPermissionState.Denied)
    }

    @Test
    fun `camera permission permanently denied transitions to PermanentlyDenied`() {
        val viewModel = CaptureViewModel()
        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = true)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CameraPermissionState.PermanentlyDenied)
    }

    @Test
    fun `PermanentlyDenied recovers to Granted when granted flag is true`() {
        val viewModel = CaptureViewModel()
        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = true)
        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = false)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CameraPermissionState.Granted)
    }

    @Test
    fun `granted flag takes priority over isPermanentlyDenied when both are true`() {
        val viewModel = CaptureViewModel()
        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = true)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CameraPermissionState.Granted)
    }

    // Location permission state machine

    @Test
    fun `initial gps status is Acquiring`() {
        val viewModel = CaptureViewModel()
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Acquiring)
    }

    @Test
    fun `location denied sets gpsStatus to Unavailable`() {
        val viewModel = CaptureViewModel()
        viewModel.onLocationPermissionResult(granted = false)
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Unavailable)
    }

    @Test
    fun `location granted keeps gpsStatus as Acquiring`() {
        val viewModel = CaptureViewModel()
        viewModel.onLocationPermissionResult(granted = true)
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Acquiring)
    }

    @Test
    fun `location granted after denial restores gpsStatus to Acquiring`() {
        val viewModel = CaptureViewModel()
        viewModel.onLocationPermissionResult(granted = false)
        viewModel.onLocationPermissionResult(granted = true)
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Acquiring)
    }
}
