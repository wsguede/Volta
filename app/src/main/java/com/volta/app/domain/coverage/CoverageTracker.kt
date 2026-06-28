package com.volta.app.domain.coverage

import com.volta.app.domain.model.DevicePose
import kotlinx.coroutines.flow.StateFlow

interface CoverageTracker {
    val coveragePercent: StateFlow<Float>
    val coverageGrid: StateFlow<CoverageGrid>
    fun markCovered(pose: DevicePose)
    fun isBelowWarningThreshold(threshold: Float = DEFAULT_WARNING_THRESHOLD): Boolean
    fun reset()

    companion object {
        const val DEFAULT_WARNING_THRESHOLD = 0.8f
    }
}
