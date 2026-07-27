package com.volta.app.domain.capture

/**
 * Scores and classifies frame sharpness from a luma (Y) plane.
 *
 * [frameData] must be tightly packed — `size == width * height`, with no row-stride padding.
 * ARCore image planes commonly have stride > width; callers must strip that padding before
 * calling.
 */
interface BlurDetector {
    fun sharpnessScore(frameData: ByteArray, width: Int, height: Int): Float
    fun isSharp(score: Float): Boolean

    companion object {
        // Provisional default shared by every BlurDetector implementation, pending device
        // tuning — see #46. Lives here (not on a concrete implementation) so callers configuring
        // a threshold-dependent default — e.g. DefaultFrameCaptureTrigger's BlurDetector param —
        // don't have to depend on a specific implementation to find it.
        const val DEFAULT_SHARPNESS_THRESHOLD = 50f
    }
}
