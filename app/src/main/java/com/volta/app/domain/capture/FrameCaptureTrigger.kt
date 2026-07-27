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

    /**
     * Cheap pre-check: true if [pose] is far enough from the last recorded frame's pose to be
     * worth scoring for sharpness at all. Lets a caller skip [BlurDetector.sharpnessScore]'s
     * O(width×height) computation on frames [evaluate] would reject on angular-spacing grounds
     * regardless, without duplicating that check's logic.
     */
    fun isFarEnoughToCapture(pose: DevicePose): Boolean

    /**
     * Clears all captured frames and forgets the last recorded pose, so the next [evaluate] call
     * behaves as if this were a fresh session. Must be called whenever a new capture session
     * starts (see [com.volta.app.ui.capture.CaptureViewModel.startSession]) — this is a
     * `@Singleton`, so without an explicit reset, a second session in the same app process would
     * start from the first session's leftover frame count, which violates the "session-focused,
     * retains nothing" constraint in AGENTS.md.
     */
    fun reset()
}
