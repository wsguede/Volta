package com.volta.app.domain.coverage

import com.volta.app.domain.model.DevicePose
import kotlin.math.PI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Uses uniform cell weighting — cells near the poles are treated the same as equatorial
// cells despite covering less solid angle. This is a known simplification.
class SphereCoverageTracker(
    private val columns: Int = DEFAULT_COLUMNS,
    private val rows: Int = DEFAULT_ROWS
) : CoverageTracker {

    private val coveredCells = Array(rows) { BooleanArray(columns) }
    private var coveredCount = 0
    private val totalCells = columns * rows

    private val _coveragePercent = MutableStateFlow(0f)
    override val coveragePercent: StateFlow<Float> = _coveragePercent.asStateFlow()

    private val _coverageGrid = MutableStateFlow(buildGrid())
    override val coverageGrid: StateFlow<CoverageGrid> = _coverageGrid.asStateFlow()

    override fun markCovered(pose: DevicePose) {
        val (col, row) = poseToCell(pose)
        if (!coveredCells[row][col]) {
            coveredCells[row][col] = true
            coveredCount++
            _coveragePercent.value = coveredCount.toFloat() / totalCells
            _coverageGrid.value = buildGrid()
        }
    }

    override fun isBelowWarningThreshold(): Boolean = _coveragePercent.value < WARNING_THRESHOLD

    override fun reset() {
        for (row in coveredCells) row.fill(false)
        coveredCount = 0
        _coveragePercent.value = 0f
        _coverageGrid.value = buildGrid()
    }

    private fun poseToCell(pose: DevicePose): Pair<Int, Int> {
        val yawDeg = pose.yaw * (180.0 / PI)
        val pitchDeg = pose.pitch * (180.0 / PI)
        val normalizedYaw = ((yawDeg % 360.0) + 360.0) % 360.0
        val col = (normalizedYaw / (360.0 / columns)).toInt().coerceIn(0, columns - 1)
        val row = ((pitchDeg + 90.0) / (180.0 / rows)).toInt().coerceIn(0, rows - 1)
        return col to row
    }

    private fun buildGrid() = CoverageGrid(
        columns = columns,
        rows = rows,
        cells = coveredCells.map { it.toList() }
    )

    companion object {
        const val DEFAULT_COLUMNS = 24
        const val DEFAULT_ROWS = 12
        const val WARNING_THRESHOLD = 0.8f
    }
}
