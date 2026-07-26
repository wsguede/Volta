package com.volta.app.domain.capture

/**
 * Scores sharpness via Laplacian variance on a luma (Y) plane: the variance of the second
 * derivative of pixel intensity. A flat/blurry image has near-zero variance; a sharp, detailed
 * image has high variance. Border pixels are excluded — only interior pixels have a full
 * 4-neighbor Laplacian. [threshold] is a provisional default pending device tuning — see #46.
 */
class LaplacianBlurDetector(private val threshold: Float = DEFAULT_SHARPNESS_THRESHOLD) :
    BlurDetector {

    override fun sharpnessScore(frameData: ByteArray, width: Int, height: Int): Float {
        require(frameData.size == width * height) {
            "frameData.size (${frameData.size}) must equal width * height ($width * $height); " +
                "the Y plane must be tightly packed with no row-stride padding"
        }

        // Welford's online algorithm: single pass, no full-array allocation. This runs per
        // frame in the live AR capture tick loop (see DefaultFrameCaptureTrigger), so avoiding
        // ~(width*height) allocations and lookups per evaluation matters.
        var count = 0
        var mean = 0.0
        var m2 = 0.0
        for (y in 1 until height - 1) {
            val rowOffset = y * width
            val upRowOffset = rowOffset - width
            val downRowOffset = rowOffset + width
            for (x in 1 until width - 1) {
                val idx = rowOffset + x
                val center = luma(frameData[idx])
                val left = luma(frameData[idx - 1])
                val right = luma(frameData[idx + 1])
                val up = luma(frameData[upRowOffset + x])
                val down = luma(frameData[downRowOffset + x])
                val laplacian = (left + right + up + down - 4 * center).toDouble()

                count++
                val delta = laplacian - mean
                mean += delta / count
                val delta2 = laplacian - mean
                m2 += delta * delta2
            }
        }

        return (if (count == 0) 0.0 else m2 / count).toFloat()
    }

    override fun isSharp(score: Float): Boolean = score >= threshold

    private fun luma(byte: Byte): Int = byte.toInt() and 0xFF

    companion object {
        // Starting point only — needs tuning against real device frames (see issue #46).
        const val DEFAULT_SHARPNESS_THRESHOLD = 50f
    }
}
