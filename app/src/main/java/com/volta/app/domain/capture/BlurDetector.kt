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
}
