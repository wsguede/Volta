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
     * Forces an immediate session-state check (as if driven by a normal render tick) so a
     * preceding [pause] is guaranteed to actually take effect, rather than waiting for whatever
     * implementation-specific driving mechanism would otherwise pick it up next — see
     * `ArCameraRepository`'s implementation and ADR 0014 for why this exists separately from
     * [pause] itself. Implementations that drive their session from a rate-limited loop must call
     * through this method unconditionally, bypassing that rate limit — otherwise a caller relying
     * on this to guarantee the pause has landed gets false confidence.
     */
    fun flushPendingPause()
}
