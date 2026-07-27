package com.volta.app.data.ar

import android.opengl.GLES20
import com.volta.app.domain.coverage.CoverageGrid
import com.volta.app.domain.coverage.buildUncoveredCellVertices
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws the sphere coverage overlay: a semi-transparent dark quad over every *uncovered* cell of
 * the coverage grid (24x12 by default — see [com.volta.app.domain.coverage.SphereCoverageTracker]),
 * world-locked via the same view/projection matrices ARCore's `Camera` exposes for the passthrough
 * feed, so the overlay appears fixed in place as the device rotates. Captured cells are left
 * undrawn entirely (see [buildUncoveredCellVertices]), matching issue #16's "clear, no fill or
 * bright outline" acceptance criterion for covered cells.
 *
 * A custom GLES20 renderer (rather than a scene-graph library like SceneView) was chosen to stay
 * consistent with [CameraQuadRenderer]'s existing pipeline and avoid a new dependency — see
 * ADR 0017.
 */
internal class SphereOverlayRenderer {

    private var program = 0
    private var positionHandle = 0
    private var mvpMatrixHandle = 0
    private var colorHandle = 0

    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0
    private var lastRenderedGrid: CoverageGrid? = null

    fun createOnGlThread() {
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
        GLES20.glAttachShader(program, compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        check(linkStatus[0] == GLES20.GL_TRUE) {
            "Unable to link the sphere overlay shader program: " +
                GLES20.glGetProgramInfoLog(program)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MvpMatrix")
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
    }

    /** Rebuilds the vertex buffer only when [grid] actually changed since the last draw. */
    fun updateGrid(grid: CoverageGrid) {
        if (grid == lastRenderedGrid) return
        lastRenderedGrid = grid
        val vertices = buildUncoveredCellVertices(grid)
        vertexCount = vertices.size / COMPONENTS_PER_VERTEX
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
    }

    fun draw(viewProjectionMatrix: FloatArray) {
        val buffer = vertexBuffer ?: return
        if (vertexCount == 0) return

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, viewProjectionMatrix, 0)
        GLES20.glUniform4fv(colorHandle, 1, UNCOVERED_CELL_COLOR, 0)

        buffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            COMPONENTS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            buffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        check(compileStatus[0] == GLES20.GL_TRUE) {
            "Unable to compile shader: " + GLES20.glGetShaderInfoLog(shader)
        }
        return shader
    }

    private companion object {
        const val COMPONENTS_PER_VERTEX = 3
        const val BYTES_PER_FLOAT = 4

        // Semi-transparent dark fill per issue #16's acceptance criteria for uncovered cells.
        val UNCOVERED_CELL_COLOR = floatArrayOf(0f, 0f, 0f, 0.5f)

        const val VERTEX_SHADER = """
            uniform mat4 u_MvpMatrix;
            attribute vec4 a_Position;
            void main() {
                gl_Position = u_MvpMatrix * a_Position;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """
    }
}
