package com.volta.app.ui.capture

import android.opengl.GLSurfaceView
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volta.app.domain.ar.ArSessionManager
import com.volta.app.domain.capture.FrameCaptureTrigger
import com.volta.app.domain.coverage.CoverageTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val arSessionManager: ArSessionManager,
    private val frameCaptureTrigger: FrameCaptureTrigger,
    private val coverageTracker: CoverageTracker,
    val cameraRenderer: GLSurfaceView.Renderer
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                frameCaptureTrigger.capturedFrameCount,
                coverageTracker.coveragePercent
            ) { frameCount, coverageFraction -> frameCount to coverageFraction }
                .collect { (frameCount, coverageFraction) ->
                    _uiState.update {
                        it.copy(
                            framesCaptured = frameCount,
                            coveragePercent = coverageFraction * PERCENT_SCALE,
                            isCoverageBelowWarningThreshold =
                            coverageTracker.isBelowWarningThreshold()
                        )
                    }
                }
        }
    }

    /**
     * Called once per capture session start (see [com.volta.app.ui.capture.CaptureScreen]'s
     * `LaunchedEffect`, which fires both on first launch and whenever the screen re-enters
     * composition after returning from export — [arSessionManager], [frameCaptureTrigger], and
     * [coverageTracker] are all process-lifetime `@Singleton`s with no session lifecycle of their
     * own). Resetting the trigger and tracker here is required, not optional: without it, a second
     * session in the same app process would start already holding the first session's frame count
     * and covered cells, silently violating AGENTS.md's "session-focused, retains nothing"
     * constraint and letting stale coverage bypass the export-confirmation threshold.
     *
     * [ArSessionManager.cancelPendingCaptures] must run first: a frame approved right at the end of
     * the previous session compresses on a background dispatcher independent of this reset, so
     * without cancelling it first, that late completion could write a stray frame into the state
     * being reset here right after this call returns.
     */
    fun startSession() {
        arSessionManager.cancelPendingCaptures()
        frameCaptureTrigger.reset()
        coverageTracker.reset()
        _uiState.update { it.copy(isSessionActive = true) }
    }

    fun stopSession() {
        _uiState.update { it.copy(isSessionActive = false) }
    }

    /**
     * Called when the export button is tapped. Below the coverage warning threshold, shows the
     * confirmation dialog instead of exporting immediately; [onExport] is a navigation lambda the
     * Composable owns (this ViewModel does not perform navigation itself), so it is only invoked
     * directly when no confirmation is needed.
     */
    fun onExportClicked(onExport: () -> Unit) {
        if (_uiState.value.isCoverageBelowWarningThreshold) {
            _uiState.update { it.copy(showExportConfirmationDialog = true) }
        } else {
            onExport()
        }
    }

    fun onDismissExportConfirmation() {
        _uiState.update { it.copy(showExportConfirmationDialog = false) }
    }

    /** Called from [com.volta.app.ui.capture.CaptureScreen] on `ON_RESUME`. Only resumes the
     * ARCore session when camera permission is already granted; [onCameraPermissionResult]
     * covers the case where permission becomes granted while the screen is in the foreground. */
    fun onScreenResumed() {
        if (_uiState.value.cameraPermission == CapturePermissionState.Granted) {
            arSessionManager.resume()
        }
    }

    /** Called from [com.volta.app.ui.capture.CaptureScreen] on `ON_PAUSE`. */
    fun onScreenPaused() {
        arSessionManager.pause()
    }

    /** Must be called on the `GLSurfaceView`'s GL thread after [onScreenPaused] — see
     * [ArSessionManager.flushPendingPause]. */
    fun flushSessionPause() {
        arSessionManager.flushPendingPause()
    }

    fun onCameraPermissionResult(granted: Boolean, isPermanentlyDenied: Boolean) {
        val permission = when {
            granted -> CapturePermissionState.Granted
            isPermanentlyDenied -> CapturePermissionState.PermanentlyDenied
            else -> CapturePermissionState.Denied
        }
        _uiState.update { it.copy(cameraPermission = permission) }
        // Not currently reachable via the UI (permission revocation requires backgrounding
        // first, which already triggers onScreenPaused()), but pausing explicitly here rather
        // than relying on that lifecycle timing is cheap defense-in-depth.
        if (granted) {
            arSessionManager.resume()
        } else {
            arSessionManager.pause()
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val status = if (granted) CaptureGpsStatus.Acquiring else CaptureGpsStatus.Unavailable
        _uiState.update { it.copy(gpsStatus = status) }
    }

    /** Defense-in-depth: [arSessionManager] is a process-lifetime `@Singleton` with no lifecycle
     * of its own, so if this ViewModel is ever cleared while a session is active, pause it here
     * too. Currently unreachable via [com.volta.app.navigation.VoltaNavGraph]'s nav graph — it
     * never pops the capture destination, so its `ViewModelStore` (and this method) never clears
     * on ordinary navigation. The actual fix for the in-app-navigation camera leak lives in
     * `ArCameraPreview`'s own `onDispose`, in `CaptureScreen.kt`, which fires on every
     * composable's teardown regardless of the ViewModel's lifecycle.
     *
     * Visibility is widened from `protected` only so this can be unit tested directly, per
     * [VisibleForTesting] — not intended to be called outside tests. */
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun onCleared() {
        arSessionManager.pause()
    }

    private companion object {
        // CoverageTracker.coveragePercent is a 0..1 fraction; CaptureUiState.coveragePercent is
        // displayed as a whole percentage (e.g. "73%").
        const val PERCENT_SCALE = 100f
    }
}
