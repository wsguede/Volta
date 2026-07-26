package com.volta.app.domain.capture

/**
 * Scores sharpness via Laplacian variance on a luma (Y) plane: the variance of the second
 * derivative of pixel intensity. A flat/blurry image has near-zero variance; a sharp, detailed
 * image has high variance. [threshold] is a provisional default pending device tuning — see #11.
 */
class LaplacianBlurDetector(private val threshold: Float = DEFAULT_SHARPNESS_THRESHOLD) :
    BlurDetector {

    override fun sharpnessScore(frameData: ByteArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f

        val interiorCount = (width - 2) * (height - 2)
        val laplacians = DoubleArray(interiorCount)
        var index = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = luma(frameData, x, y, width)
                val left = luma(frameData, x - 1, y, width)
                val right = luma(frameData, x + 1, y, width)
                val up = luma(frameData, x, y - 1, width)
                val down = luma(frameData, x, y + 1, width)
                laplacians[index++] = (left + right + up + down - 4 * center).toDouble()
            }
        }

        val mean = laplacians.average()
        val variance = laplacians.sumOf { (it - mean) * (it - mean) } / laplacians.size
        return variance.toFloat()
    }

    override fun isSharp(score: Float): Boolean = score >= threshold

    private fun luma(frameData: ByteArray, x: Int, y: Int, width: Int): Int =
        frameData[y * width + x].toInt() and 0xFF

    companion object {
        // Starting point only — needs tuning against real device frames (see issue #11).
        const val DEFAULT_SHARPNESS_THRESHOLD = 50f
    }
}
