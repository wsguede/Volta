package com.volta.app.domain.capture

import com.volta.app.domain.model.DevicePose
import kotlinx.coroutines.flow.StateFlow

data class CapturedFrame(val jpeg: ByteArray, val pose: DevicePose) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedFrame) return false
        return pose == other.pose && jpeg.contentEquals(other.jpeg)
    }

    override fun hashCode(): Int = 31 * jpeg.contentHashCode() + pose.hashCode()
}

data class CaptureEvent(val pose: DevicePose)

interface FrameCaptureTrigger {
    val capturedFrames: List<CapturedFrame>
    val capturedFrameCount: StateFlow<Int>

    /**
     * Decides whether the frame at [pose] with the given [sharpnessScore] should be captured.
     * Pure decision — does not record anything; call [recordFrame] with the compressed JPEG once
     * the caller has acted on a non-null result.
     */
    fun evaluate(pose: DevicePose, sharpnessScore: Float): CaptureEvent?

    fun recordFrame(jpeg: ByteArray, pose: DevicePose)
}
