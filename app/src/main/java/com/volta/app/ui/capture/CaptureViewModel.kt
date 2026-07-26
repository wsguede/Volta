package com.volta.app.ui.capture

import android.opengl.GLSurfaceView
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import com.volta.app.domain.ar.ArSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val arSessionManager: ArSessionManager,
    val cameraRenderer: GLSurfaceView.Renderer
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun startSession() {
        _uiState.update { it.copy(isSessionActive = true) }
    }

    fun stopSession() {
        _uiState.update { it.copy(isSessionActive = false) }
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
}
