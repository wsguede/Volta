package com.volta.app.domain.capture

import com.volta.app.domain.model.DevicePose
import kotlinx.coroutines.flow.StateFlow

interface FrameCaptureTrigger {
    val capturedFrames: List<CapturedFrame>
    val capturedFrameCount: StateFlow<Int>

    /**
     * Pull-based rather than `Flow<DevicePose>`-driven: pairing a pose update with the sharpness
     * score of the same frame across two independently-ticking flows would be awkward, so the
     * caller pushes one (pose, sharpnessScore) pair per evaluated frame instead.
     *
     * Combines the capture decision and the store write atomically, so a caller can't record a
     * frame this trigger never approved: [jpeg] is only invoked — and its result stored — when
     * both the angular-spacing and sharpness thresholds are met.
     */
    fun captureIfNeeded(
        pose: DevicePose,
        sharpnessScore: Float,
        jpeg: () -> ByteArray
    ): CaptureEvent?
}
