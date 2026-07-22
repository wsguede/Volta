package com.volta.app.domain.capture

import com.volta.app.domain.coverage.SphereRegion
import com.volta.app.domain.model.FrameData

data class CapturedFrame(val frame: FrameData, val region: SphereRegion, val timestampMs: Long)

interface FrameAnalyzer {
    fun shouldCapture(
        currentOrientation: SphereRegion,
        lastCapturedOrientation: SphereRegion?,
        angularThresholdDegrees: Float
    ): Boolean
}
