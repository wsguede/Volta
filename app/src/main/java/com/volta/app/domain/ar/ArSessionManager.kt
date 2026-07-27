package com.volta.app.domain.ar

import com.volta.app.domain.model.ArFrame
import com.volta.app.domain.model.DevicePose
import com.volta.app.domain.model.TrackingState
import kotlinx.coroutines.flow.Flow

interface ArSessionManager {
    val isAvailable: Flow<Boolean>
    val currentPose: Flow<DevicePose>
    val cameraFrames: Flow<ArFrame>
    val trackingState: Flow<TrackingState>
    fun resume()
    fun pause()

    /**
     * Guarantees a preceding [pause] has actually been applied before returning control, rather
     * than merely being requested. [pause] alone only signals intent; depending on how an
     * implementation drives its session, applying that intent may otherwise happen at some
     * later, implementation-defined point — see `ArCameraRepository`'s implementation and
     * ADR 0014 for why this is a separate operation from [pause] itself.
     */
    fun flushPendingPause()

    /**
     * Cancels any in-flight JPEG-compression work for frames approved before this call. A capture
     * approval's compression runs on a background dispatcher independent of this session's own
     * lifecycle (see ADR 0016), so without this, a session reset (`CaptureViewModel.startSession`)
     * can race a late-completing compression job from the *previous* session, which would
     * otherwise write a stray frame into the freshly-reset `FrameCaptureTrigger`/`CoverageTracker`
     * state. Callers must call this before resetting that state, not after.
     */
    fun cancelPendingCaptures()
}
