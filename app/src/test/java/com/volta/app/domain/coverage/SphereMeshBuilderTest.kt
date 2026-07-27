package com.volta.app.domain.coverage

import com.google.common.truth.Truth.assertThat
import kotlin.math.sqrt
import org.junit.Test

class SphereMeshBuilderTest {

    private fun gridOf(columns: Int, rows: Int, covered: (row: Int, col: Int) -> Boolean) =
        CoverageGrid(
            columns = columns,
            rows = rows,
            cells = (0 until rows).map { row -> (0 until columns).map { col -> covered(row, col) } }
        )

    @Test
    fun `fully covered grid produces no vertices`() {
        val grid = gridOf(columns = 4, rows = 4) { _, _ -> true }

        val vertices = buildUncoveredCellVertices(grid)

        assertThat(vertices).isEmpty()
    }

    @Test
    fun `each uncovered cell contributes exactly two triangles`() {
        val grid = gridOf(columns = 4, rows = 4) { row, col -> !(row == 1 && col == 2) }

        val vertices = buildUncoveredCellVertices(grid)

        // 6 vertices * 3 floats (x, y, z) per quad.
        assertThat(vertices).hasLength(18)
    }

    @Test
    fun `vertex count scales with the number of uncovered cells`() {
        val grid = gridOf(columns = 4, rows = 4) { row, _ -> row == 0 }

        val vertices = buildUncoveredCellVertices(grid)

        // Row 0 (4 cells) is covered; the other 12 cells are uncovered.
        assertThat(vertices).hasLength(12 * 18)
    }

    @Test
    fun `every vertex lies on the sphere at the given radius`() {
        val grid = gridOf(columns = 6, rows = 6) { _, _ -> false }
        val radius = 3f

        val vertices = buildUncoveredCellVertices(grid, radius)

        for (i in vertices.indices step 3) {
            val x = vertices[i]
            val y = vertices[i + 1]
            val z = vertices[i + 2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble())
            assertThat(magnitude).isWithin(1e-3).of(radius.toDouble())
        }
    }

    @Test
    fun `a cell in the northern hemisphere has non-negative y for every vertex`() {
        // rows=4 -> pitch step 45deg; row=2 spans pitch [0, 45] degrees (northern hemisphere).
        val grid = gridOf(columns = 4, rows = 4) { row, col -> !(row == 2 && col == 0) }

        val vertices = buildUncoveredCellVertices(grid)

        for (i in vertices.indices step 3) {
            assertThat(vertices[i + 1]).isAtLeast(-1e-4f)
        }
    }
}
