package com.volta.app.domain.coverage

interface CoverageTracker {
    fun markCovered(region: SphereRegion)
    fun coveragePercent(): Float
    fun isCovered(region: SphereRegion): Boolean
    fun reset()
}
