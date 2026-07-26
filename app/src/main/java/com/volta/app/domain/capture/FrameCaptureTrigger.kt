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
     * Returns a [CaptureApproval] only when both the angular-spacing and sharpness thresholds are
     * met. Deliberately does not accept or return a JPEG: compressing the frame is CPU-bound work
     * that belongs on the caller's own dispatcher (see AGENTS.md), not inside this call or [record].
     */
    fun evaluate(pose: DevicePose, sharpnessScore: Float): CaptureApproval?

    /**
     * Stores [jpeg] for the frame [approval] was granted for. [CaptureApproval] can only be
     * constructed by [evaluate], so this cannot be called with a frame the trigger never approved.
     */
    fun record(approval: CaptureApproval, jpeg: ByteArray): CaptureEvent
}
