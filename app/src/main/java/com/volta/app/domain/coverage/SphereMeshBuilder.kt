package com.volta.app.domain.coverage

import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds a flat (x, y, z, x, y, z, ...) triangle-list vertex array — two triangles per cell quad
 * — for [grid]'s *uncovered* cells only. Captured cells contribute no vertices at all, matching
 * issue #16's acceptance criteria that captured cells render "clear (no fill or bright outline)".
 * Pure Kotlin geometry with no GLES/Android dependency — see ADR 0017 — so it lives alongside
 * [SphereCoverageTracker] rather than the GLES-specific renderer that consumes it
 * (`com.volta.app.data.ar.SphereOverlayRenderer`).
 *
 * Cell boundaries follow [SphereCoverageTracker.poseToCell]'s convention exactly (column
 * 0..columns spans yaw 0..360°, row 0..rows spans pitch -90..+90°), and the spherical-to-Cartesian
 * mapping matches [com.volta.app.domain.model.DevicePose]'s convention (right-handed, Y-up,
 * yaw=pitch=0 facing -Z) so the overlay lines up with ARCore's own camera pose when both are
 * transformed by the same view/projection matrices.
 */
internal fun buildUncoveredCellVertices(
    grid: CoverageGrid,
    radius: Float = Constants.SPHERE_OVERLAY_RADIUS
): FloatArray {
    val yawStepDeg = Constants.FULL_TURN_DEGREES / grid.columns
    val pitchStepDeg = Constants.HALF_TURN_DEGREES / grid.rows
    val vertices = ArrayList<Float>(grid.columns * grid.rows * Constants.FLOATS_PER_QUAD)

    for (row in 0 until grid.rows) {
        for (col in 0 until grid.columns) {
            if (grid.cells[row][col]) continue

            val yaw0 = Math.toRadians(col * yawStepDeg)
            val yaw1 = Math.toRadians((col + 1) * yawStepDeg)
            val pitch0 = Math.toRadians(row * pitchStepDeg - Constants.QUARTER_TURN_DEGREES)
            val pitch1 = Math.toRadians((row + 1) * pitchStepDeg - Constants.QUARTER_TURN_DEGREES)

            val bottomLeft = sphericalToCartesian(yaw0, pitch0, radius)
            val bottomRight = sphericalToCartesian(yaw1, pitch0, radius)
            val topLeft = sphericalToCartesian(yaw0, pitch1, radius)
            val topRight = sphericalToCartesian(yaw1, pitch1, radius)

            vertices.addQuad(bottomLeft, bottomRight, topLeft, topRight)
        }
    }
    return vertices.toFloatArray()
}

private fun MutableList<Float>.addQuad(
    bottomLeft: FloatArray,
    bottomRight: FloatArray,
    topLeft: FloatArray,
    topRight: FloatArray
) {
    addVertex(bottomLeft)
    addVertex(bottomRight)
    addVertex(topLeft)
    addVertex(topLeft)
    addVertex(bottomRight)
    addVertex(topRight)
}

private fun MutableList<Float>.addVertex(vertex: FloatArray) {
    add(vertex[0])
    add(vertex[1])
    add(vertex[2])
}

private fun sphericalToCartesian(
    yawRadians: Double,
    pitchRadians: Double,
    radius: Float
): FloatArray = floatArrayOf(
    (radius * cos(pitchRadians) * sin(yawRadians)).toFloat(),
    (radius * sin(pitchRadians)).toFloat(),
    (-radius * cos(pitchRadians) * cos(yawRadians)).toFloat()
)

private object Constants {
    // World-space distance from the origin at which the coverage overlay is drawn.
    const val SPHERE_OVERLAY_RADIUS = 5f
    const val FULL_TURN_DEGREES = 360.0
    const val HALF_TURN_DEGREES = 180.0
    const val QUARTER_TURN_DEGREES = 90.0
    private const val VERTICES_PER_QUAD = 6
    private const val COMPONENTS_PER_VERTEX = 3
    const val FLOATS_PER_QUAD = VERTICES_PER_QUAD * COMPONENTS_PER_VERTEX
}
