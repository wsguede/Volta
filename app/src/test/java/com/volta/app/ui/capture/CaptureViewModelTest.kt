package com.volta.app.ui.capture

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.ar.ArSessionManager
import com.volta.app.domain.model.ArFrame
import com.volta.app.domain.model.DevicePose
import com.volta.app.domain.model.TrackingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

private class FakeArSessionManager : ArSessionManager {
    override val isAvailable: Flow<Boolean> = MutableStateFlow(false)
    override val currentPose: Flow<DevicePose> = MutableStateFlow(DevicePose(0.0, 0.0, 0.0))
    override val cameraFrames: Flow<ArFrame> = MutableStateFlow(ArFrame(ByteArray(0), 0, 0))
    override val trackingState: Flow<TrackingState> = MutableStateFlow(TrackingState.NotTracking)

    var resumeCalls = 0
        private set
    var pauseCalls = 0
        private set

    override fun resume() {
        resumeCalls++
    }

    override fun pause() {
        pauseCalls++
    }
}

class CaptureViewModelTest {

    private fun viewModel(arSessionManager: ArSessionManager = FakeArSessionManager()) =
        CaptureViewModel(arSessionManager)

    @Test
    fun `initial state has session inactive`() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.isSessionActive).isFalse()
    }

    @Test
    fun `startSession sets session active`() {
        val viewModel = viewModel()
        viewModel.startSession()
        assertThat(viewModel.uiState.value.isSessionActive).isTrue()
    }

    @Test
    fun `stopSession sets session inactive`() {
        val viewModel = viewModel()
        viewModel.startSession()
        viewModel.stopSession()
        assertThat(viewModel.uiState.value.isSessionActive).isFalse()
    }

    // Camera permission state machine

    @Test
    fun `initial camera permission is NotRequested`() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CapturePermissionState.NotRequested)
    }

    @Test
    fun `camera permission granted transitions to Granted`() {
        val viewModel = viewModel()
        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = false)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CapturePermissionState.Granted)
    }

    @Test
    fun `camera permission denied transitions to Denied`() {
        val viewModel = viewModel()
        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = false)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CapturePermissionState.Denied)
    }

    @Test
    fun `camera permission permanently denied transitions to PermanentlyDenied`() {
        val viewModel = viewModel()
        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = true)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CapturePermissionState.PermanentlyDenied)
    }

    @Test
    fun `PermanentlyDenied recovers to Granted when granted flag is true`() {
        val viewModel = viewModel()
        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = true)
        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = false)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CapturePermissionState.Granted)
    }

    @Test
    fun `granted flag takes priority over isPermanentlyDenied when both are true`() {
        val viewModel = viewModel()
        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = true)
        assertThat(viewModel.uiState.value.cameraPermission)
            .isEqualTo(CapturePermissionState.Granted)
    }

    // Location permission state machine

    @Test
    fun `initial gps status is Acquiring`() {
        val viewModel = viewModel()
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Acquiring)
    }

    @Test
    fun `location denied sets gpsStatus to Unavailable`() {
        val viewModel = viewModel()
        viewModel.onLocationPermissionResult(granted = false)
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Unavailable)
    }

    @Test
    fun `location granted keeps gpsStatus as Acquiring`() {
        val viewModel = viewModel()
        viewModel.onLocationPermissionResult(granted = true)
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Acquiring)
    }

    @Test
    fun `location granted after denial restores gpsStatus to Acquiring`() {
        val viewModel = viewModel()
        viewModel.onLocationPermissionResult(granted = false)
        viewModel.onLocationPermissionResult(granted = true)
        assertThat(viewModel.uiState.value.gpsStatus).isEqualTo(CaptureGpsStatus.Acquiring)
    }

    // ARCore session lifecycle wiring

    @Test
    fun `onScreenResumed resumes the ARCore session when camera permission is granted`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)
        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = false)

        viewModel.onScreenResumed()

        assertThat(arSessionManager.resumeCalls).isEqualTo(2)
    }

    @Test
    fun `onScreenResumed does not resume the ARCore session without camera permission`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.onScreenResumed()

        assertThat(arSessionManager.resumeCalls).isEqualTo(0)
    }

    @Test
    fun `onScreenPaused pauses the ARCore session`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.onScreenPaused()

        assertThat(arSessionManager.pauseCalls).isEqualTo(1)
    }

    @Test
    fun `granting camera permission resumes the ARCore session immediately`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = false)

        assertThat(arSessionManager.resumeCalls).isEqualTo(1)
    }

    @Test
    fun `denying camera permission does not resume the ARCore session`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = false)

        assertThat(arSessionManager.resumeCalls).isEqualTo(0)
    }
}
