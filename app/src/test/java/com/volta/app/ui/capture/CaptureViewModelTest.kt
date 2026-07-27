package com.volta.app.ui.capture

import android.opengl.GLSurfaceView
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.ar.ArSessionManager
import com.volta.app.domain.capture.CaptureApproval
import com.volta.app.domain.capture.CaptureEvent
import com.volta.app.domain.capture.CapturedFrame
import com.volta.app.domain.capture.FrameCaptureTrigger
import com.volta.app.domain.coverage.CoverageGrid
import com.volta.app.domain.coverage.CoverageTracker
import com.volta.app.domain.model.ArFrame
import com.volta.app.domain.model.DevicePose
import com.volta.app.domain.model.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private class FakeGlRenderer : GLSurfaceView.Renderer {
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) = Unit
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = Unit
    override fun onDrawFrame(gl: GL10?) = Unit
}

private class FakeArSessionManager : ArSessionManager {
    override val isAvailable: Flow<Boolean> = MutableStateFlow(false)
    override val currentPose: Flow<DevicePose> = MutableStateFlow(DevicePose(0.0, 0.0, 0.0))
    override val cameraFrames: Flow<ArFrame> = MutableStateFlow(ArFrame(ByteArray(0), 0, 0))
    override val trackingState: Flow<TrackingState> = MutableStateFlow(TrackingState.NotTracking)

    var resumeCalls = 0
        private set
    var pauseCalls = 0
        private set
    var flushPendingPauseCalls = 0
        private set

    override fun resume() {
        resumeCalls++
    }

    override fun pause() {
        pauseCalls++
    }

    override fun flushPendingPause() {
        flushPendingPauseCalls++
    }
}

private class FakeFrameCaptureTrigger : FrameCaptureTrigger {
    private val _capturedFrameCount = MutableStateFlow(0)
    override val capturedFrameCount: MutableStateFlow<Int> = _capturedFrameCount
    override val capturedFrames: List<CapturedFrame> = emptyList()

    var resetCalls = 0
        private set

    override fun evaluate(pose: DevicePose, sharpnessScore: Float): CaptureApproval? = null

    override fun record(approval: CaptureApproval, jpeg: ByteArray): CaptureEvent {
        error("not used by CaptureViewModelTest")
    }

    override fun isFarEnoughToCapture(pose: DevicePose): Boolean = true

    override fun reset() {
        resetCalls++
        _capturedFrameCount.value = 0
    }
}

private class FakeCoverageTracker : CoverageTracker {
    private val _coveragePercent = MutableStateFlow(0f)
    override val coveragePercent: MutableStateFlow<Float> = _coveragePercent
    override val coverageGrid: MutableStateFlow<CoverageGrid> =
        MutableStateFlow(CoverageGrid(columns = 1, rows = 1, cells = listOf(listOf(false))))

    var belowThreshold = true
    var resetCalls = 0
        private set

    override fun markCovered(pose: DevicePose) = Unit

    override fun isBelowWarningThreshold(threshold: Float): Boolean = belowThreshold

    override fun reset() {
        resetCalls++
        _coveragePercent.value = 0f
    }
}

class CaptureViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        arSessionManager: ArSessionManager = FakeArSessionManager(),
        frameCaptureTrigger: FrameCaptureTrigger = FakeFrameCaptureTrigger(),
        coverageTracker: CoverageTracker = FakeCoverageTracker(),
        cameraRenderer: GLSurfaceView.Renderer = FakeGlRenderer()
    ) = CaptureViewModel(arSessionManager, frameCaptureTrigger, coverageTracker, cameraRenderer)

    @Test
    fun `exposes the injected camera renderer for the capture screen to embed`() {
        val renderer = FakeGlRenderer()
        val viewModel = viewModel(cameraRenderer = renderer)

        assertThat(viewModel.cameraRenderer).isSameInstanceAs(renderer)
    }

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

    @Test
    fun `denying camera permission pauses the ARCore session`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = false)

        assertThat(arSessionManager.pauseCalls).isEqualTo(1)
    }

    @Test
    fun `permanently denying camera permission pauses the ARCore session`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.onCameraPermissionResult(granted = false, isPermanentlyDenied = true)

        assertThat(arSessionManager.pauseCalls).isEqualTo(1)
    }

    @Test
    fun `flushSessionPause flushes the pending pause`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.flushSessionPause()

        assertThat(arSessionManager.flushPendingPauseCalls).isEqualTo(1)
    }

    @Test
    fun `onCleared pauses the ARCore session`() {
        val arSessionManager = FakeArSessionManager()
        val viewModel = viewModel(arSessionManager)

        viewModel.onCleared()

        assertThat(arSessionManager.pauseCalls).isEqualTo(1)
    }

    // Frame count / coverage wiring

    @Test
    fun `initial framesCaptured mirrors the frame capture trigger's count`() {
        val frameCaptureTrigger = FakeFrameCaptureTrigger().apply { capturedFrameCount.value = 3 }
        val viewModel = viewModel(frameCaptureTrigger = frameCaptureTrigger)

        assertThat(viewModel.uiState.value.framesCaptured).isEqualTo(3)
    }

    @Test
    fun `framesCaptured updates when the trigger's count changes`() {
        val frameCaptureTrigger = FakeFrameCaptureTrigger()
        val viewModel = viewModel(frameCaptureTrigger = frameCaptureTrigger)

        frameCaptureTrigger.capturedFrameCount.value = 7

        assertThat(viewModel.uiState.value.framesCaptured).isEqualTo(7)
    }

    @Test
    fun `coveragePercent scales the tracker's 0 to 1 fraction to a whole percentage`() {
        val coverageTracker = FakeCoverageTracker().apply { coveragePercent.value = 0.735f }
        val viewModel = viewModel(coverageTracker = coverageTracker)

        assertThat(viewModel.uiState.value.coveragePercent).isWithin(0.01f).of(73.5f)
    }

    @Test
    fun `isCoverageBelowWarningThreshold mirrors the tracker`() {
        val coverageTracker = FakeCoverageTracker().apply { belowThreshold = false }
        val viewModel = viewModel(coverageTracker = coverageTracker)
        coverageTracker.coveragePercent.value = 0.9f

        assertThat(viewModel.uiState.value.isCoverageBelowWarningThreshold).isFalse()
    }

    // Session start resets per-session singletons

    @Test
    fun `startSession resets the frame capture trigger`() {
        val frameCaptureTrigger = FakeFrameCaptureTrigger()
        val viewModel = viewModel(frameCaptureTrigger = frameCaptureTrigger)

        viewModel.startSession()

        assertThat(frameCaptureTrigger.resetCalls).isEqualTo(1)
    }

    @Test
    fun `startSession resets the coverage tracker`() {
        val coverageTracker = FakeCoverageTracker()
        val viewModel = viewModel(coverageTracker = coverageTracker)

        viewModel.startSession()

        assertThat(coverageTracker.resetCalls).isEqualTo(1)
    }

    // Export confirmation gating

    @Test
    fun `onExportClicked shows the confirmation dialog when coverage is below threshold`() {
        val coverageTracker = FakeCoverageTracker().apply { belowThreshold = true }
        val viewModel = viewModel(coverageTracker = coverageTracker)
        var exportCalls = 0

        viewModel.onExportClicked { exportCalls++ }

        assertThat(viewModel.uiState.value.showExportConfirmationDialog).isTrue()
        assertThat(exportCalls).isEqualTo(0)
    }

    @Test
    fun `onExportClicked exports directly when coverage is at or above threshold`() {
        val coverageTracker = FakeCoverageTracker().apply { belowThreshold = false }
        val viewModel = viewModel(coverageTracker = coverageTracker)
        var exportCalls = 0

        viewModel.onExportClicked { exportCalls++ }

        assertThat(viewModel.uiState.value.showExportConfirmationDialog).isFalse()
        assertThat(exportCalls).isEqualTo(1)
    }

    @Test
    fun `onDismissExportConfirmation hides the confirmation dialog`() {
        val coverageTracker = FakeCoverageTracker().apply { belowThreshold = true }
        val viewModel = viewModel(coverageTracker = coverageTracker)
        viewModel.onExportClicked {}

        viewModel.onDismissExportConfirmation()

        assertThat(viewModel.uiState.value.showExportConfirmationDialog).isFalse()
    }
}
