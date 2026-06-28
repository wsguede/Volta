package com.volta.app.domain.coverage

import com.volta.app.domain.model.DevicePose
import kotlinx.coroutines.flow.StateFlow

interface CoverageTracker {
    val coveragePercent: StateFlow<Float>
    val coverageGrid: StateFlow<CoverageGrid>
    fun markCovered(pose: DevicePose)
    fun isBelowWarningThreshold(): Boolean
    fun reset()
}
