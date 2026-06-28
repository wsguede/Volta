package com.volta.app.domain.coverage

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.model.DevicePose
import kotlin.math.PI
import kotlin.math.ceil
import org.junit.Test

class CoverageTrackerTest {

    private fun tracker(columns: Int = 24, rows: Int = 12) =
        SphereCoverageTracker(columns = columns, rows = rows)

    @Test
    fun `initial coverage percent is zero`() {
        assertThat(tracker().coveragePercent.value).isEqualTo(0f)
    }

    @Test
    fun `initial coverage grid has correct dimensions`() {
        val grid = tracker().coverageGrid.value
        assertThat(grid.columns).isEqualTo(24)
        assertThat(grid.rows).isEqualTo(12)
        assertThat(grid.cells).hasSize(12)
        assertThat(grid.cells[0]).hasSize(24)
    }

    @Test
    fun `initial coverage grid has no covered cells`() {
        assertThat(tracker().coverageGrid.value.cells.flatten()).doesNotContain(true)
    }

    @Test
    fun `marking one cell increases coverage by one cell fraction`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = 0.0, roll = 0.0))
        assertThat(tracker.coveragePercent.value).isWithin(1e-5f).of(1f / 288f)
    }

    @Test
    fun `marking same cell twice does not count it twice`() {
        val tracker = tracker()
        val pose = DevicePose(yaw = 0.0, pitch = 0.0, roll = 0.0)
        tracker.markCovered(pose)
        tracker.markCovered(pose)
        assertThat(tracker.coveragePercent.value).isWithin(1e-5f).of(1f / 288f)
    }

    @Test
    fun `marking all cells results in full coverage`() {
        val tracker = tracker()
        for (col in 0 until 24) {
            for (row in 0 until 12) {
                val yaw = (col * 15 + 7.5) * PI / 180.0
                val pitch = (row * 15 - 90 + 7.5) * PI / 180.0
                tracker.markCovered(DevicePose(yaw = yaw, pitch = pitch, roll = 0.0))
            }
        }
        assertThat(tracker.coveragePercent.value).isWithin(1e-5f).of(1f)
    }

    @Test
    fun `reset clears coverage percent to zero`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = 0.0, roll = 0.0))
        tracker.reset()
        assertThat(tracker.coveragePercent.value).isEqualTo(0f)
    }

    @Test
    fun `reset clears coverage grid`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = 0.0, roll = 0.0))
        tracker.reset()
        assertThat(tracker.coverageGrid.value.cells.flatten()).doesNotContain(true)
    }

    @Test
    fun `isBelowWarningThreshold is true when coverage is zero`() {
        assertThat(tracker().isBelowWarningThreshold()).isTrue()
    }

    @Test
    fun `isBelowWarningThreshold is false when coverage reaches warning threshold`() {
        val tracker = tracker()
        val totalCells = 24 * 12
        val cellsNeeded = ceil(CoverageTracker.DEFAULT_WARNING_THRESHOLD * totalCells).toInt()
        var count = 0
        outer@ for (col in 0 until 24) {
            for (row in 0 until 12) {
                if (count >= cellsNeeded) break@outer
                val yaw = (col * 15 + 7.5) * PI / 180.0
                val pitch = (row * 15 - 90 + 7.5) * PI / 180.0
                tracker.markCovered(DevicePose(yaw = yaw, pitch = pitch, roll = 0.0))
                count++
            }
        }
        assertThat(tracker.isBelowWarningThreshold()).isFalse()
    }

    @Test
    fun `isBelowWarningThreshold respects custom threshold`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = 0.0, roll = 0.0))
        assertThat(tracker.isBelowWarningThreshold(threshold = 0.001f)).isFalse()
        assertThat(tracker.isBelowWarningThreshold(threshold = 0.5f)).isTrue()
    }

    @Test
    fun `coverageGrid shows one covered cell at expected position`() {
        val tracker = tracker()
        // yaw=0 → col 0, pitch=0 → row 6 (equator, (0+90)/15=6)
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = 0.0, roll = 0.0))
        val grid = tracker.coverageGrid.value
        val covered = grid.cells.sumOf { row -> row.count { it } }
        assertThat(covered).isEqualTo(1)
        assertThat(grid.cells[6][0]).isTrue()
    }

    @Test
    fun `extreme pitch down maps to row zero`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = -PI / 2, roll = 0.0))
        assertThat(tracker.coverageGrid.value.cells[0][0]).isTrue()
    }

    @Test
    fun `extreme pitch up maps to last row`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = PI / 2, roll = 0.0))
        assertThat(tracker.coverageGrid.value.cells[11][0]).isTrue()
    }

    @Test
    fun `out-of-range pitch is clamped to last row`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 0.0, pitch = PI, roll = 0.0))
        assertThat(tracker.coverageGrid.value.cells[11][0]).isTrue()
    }

    @Test
    fun `yaw wraps around from 2pi to column zero`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = 2 * PI, pitch = 0.0, roll = 0.0))
        assertThat(tracker.coveragePercent.value).isWithin(1e-5f).of(1f / 288f)
        assertThat(tracker.coverageGrid.value.cells[6][0]).isTrue()
    }

    @Test
    fun `negative yaw maps to a valid cell`() {
        val tracker = tracker()
        tracker.markCovered(DevicePose(yaw = -PI, pitch = 0.0, roll = 0.0))
        assertThat(tracker.coveragePercent.value).isWithin(1e-5f).of(1f / 288f)
    }

    @Test
    fun `custom grid dimensions are respected`() {
        val tracker = tracker(columns = 12, rows = 6)
        for (col in 0 until 12) {
            for (row in 0 until 6) {
                val yaw = (col * 30 + 15.0) * PI / 180.0
                val pitch = (row * 30 - 90 + 15.0) * PI / 180.0
                tracker.markCovered(DevicePose(yaw = yaw, pitch = pitch, roll = 0.0))
            }
        }
        assertThat(tracker.coveragePercent.value).isWithin(1e-5f).of(1f)
    }
}
