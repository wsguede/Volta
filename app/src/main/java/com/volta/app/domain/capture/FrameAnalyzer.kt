package com.volta.app.domain.capture

import com.volta.app.domain.coverage.SphereRegion

data class CapturedFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val region: SphereRegion,
    val timestampMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedFrame) return false
        return timestampMs == other.timestampMs && region == other.region
    }

    override fun hashCode(): Int = 31 * timestampMs.hashCode() + region.hashCode()
}

interface FrameAnalyzer {
    fun shouldCapture(
        currentOrientation: SphereRegion,
        lastCapturedOrientation: SphereRegion?,
        angularThresholdDegrees: Float
    ): Boolean
}
