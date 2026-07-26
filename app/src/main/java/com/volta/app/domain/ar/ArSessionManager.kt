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
}
