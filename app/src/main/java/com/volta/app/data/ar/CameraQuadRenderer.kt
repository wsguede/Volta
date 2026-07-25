package com.volta.app.data.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws the ARCore camera feed as a full-screen quad sampling an external OES texture. Texture
 * coordinates are recomputed via [Frame.transformCoordinates2d] whenever ARCore reports the
 * display geometry has changed, which corrects for device/display rotation without any manual
 * rotation-matrix bookkeeping.
 */
internal class CameraQuadRenderer {

    private var program = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureUniformHandle = 0

    private val quadCoords = directFloatBuffer(QUAD_COORDS)
    private val quadTexCoords = directFloatBuffer(FloatArray(QUAD_COORDS.size))

    fun createOnGlThread() {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
        GLES20.glAttachShader(program, compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        check(linkStatus[0] == GLES20.GL_TRUE) {
            "Unable to link the camera background shader program: " +
                GLES20.glGetProgramInfoLog(program)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "u_Texture")
    }

    fun updateTransform(frame: Frame) {
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quadCoords,
            Coordinates2d.TEXTURE_NORMALIZED,
            quadTexCoords
        )
    }

    fun draw(cameraTextureId: Int) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(textureUniformHandle, 0)

        quadCoords.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadCoords
        )
        quadTexCoords.position(0)
        GLES20.glVertexAttribPointer(
            texCoordHandle,
            TEXCOORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadTexCoords
        )

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTEX_COUNT)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
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
        const val COORDS_PER_VERTEX = 2
        const val TEXCOORDS_PER_VERTEX = 2
        const val QUAD_VERTEX_COUNT = 4
        const val BYTES_PER_FLOAT = 4

        val QUAD_COORDS = floatArrayOf(
            -1.0f,
            -1.0f,
            +1.0f,
            -1.0f,
            -1.0f,
            +1.0f,
            +1.0f,
            +1.0f
        )

        const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """

        fun directFloatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }
    }
}
