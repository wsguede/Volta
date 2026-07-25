package com.volta.app.data.ar

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.volta.app.domain.model.ArFrame
import com.volta.app.domain.model.DevicePose
import com.volta.app.domain.model.TrackingState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Owns the ARCore [Session] end-to-end via a headless (non-rendering) update loop on a dedicated
 * background thread. Does not depend on a UI-owned GLSurfaceView — see ADR 0013 and the note on
 * issue #16 about camera-texture ownership once the on-screen renderer is built.
 */
class ArCameraRepository @Inject constructor(@ApplicationContext private val context: Context) :
    ArSessionManager {

    private val resumed = AtomicBoolean(false)
    private var session: Session? = null

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: Flow<Boolean> = _isAvailable.asStateFlow()

    private val _currentPose = MutableSharedFlow<DevicePose>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val currentPose: Flow<DevicePose> = _currentPose.asSharedFlow()

    private val _cameraFrames = MutableSharedFlow<ArFrame>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val cameraFrames: Flow<ArFrame> = _cameraFrames.asSharedFlow()

    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState.NotTracking)
    override val trackingState: Flow<TrackingState> = _trackingState.asStateFlow()

    init {
        ArSessionThread().start()
    }

    override fun resume() {
        resumed.set(true)
    }

    override fun pause() {
        resumed.set(false)
    }

    private fun createExternalTexture(): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0])
        return textureIds[0]
    }

    private fun initializeGl() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to obtain an EGL display" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL" }

        val configAttributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, numConfigs, 0)
        ) { "Unable to choose an EGL config" }
        val config = configs[0] ?: error("No matching EGL config found")

        val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val eglContext =
            EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create an EGL context" }

        val surfaceAttributes = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttributes, 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "Unable to create an EGL pbuffer surface" }

        check(
            EGL14.eglMakeCurrent(display, surface, surface, eglContext)
        ) { "Unable to make the EGL context current" }
    }

    private inner class ArSessionThread : Thread(THREAD_NAME) {
        private var wasResumed = false

        override fun run() {
            initializeGl()
            while (true) {
                val isResumed = resumed.get()
                if (isResumed && !wasResumed) startSession()
                if (!isResumed && wasResumed) stopSession()
                wasResumed = isResumed

                if (isResumed) pumpSession() else Thread.sleep(PAUSED_POLL_INTERVAL_MS)
            }
        }

        private fun startSession() {
            val activeSession = session ?: createSession() ?: return
            try {
                activeSession.resume()
                session = activeSession
            } catch (cameraUnavailable: CameraNotAvailableException) {
                Timber.w(cameraUnavailable, "Camera unavailable while resuming ARCore session")
            }
        }

        private fun stopSession() {
            session?.pause()
            _trackingState.value = TrackingState.NotTracking
        }

        private fun createSession(): Session? = try {
            Session(context).also { it.setCameraTextureName(createExternalTexture()) }
                .also { _isAvailable.value = true }
        } catch (unavailable: UnavailableException) {
            Timber.e(unavailable, "ARCore session unavailable on this device")
            _isAvailable.value = false
            null
        }

        private fun pumpSession() {
            val activeSession = session ?: return
            try {
                processFrame(activeSession.update())
            } catch (expected: NotYetAvailableException) {
                // No new frame since the last update() call — expected; ARCore paces this
                // internally to the camera's actual frame rate.
            } catch (cameraUnavailable: CameraNotAvailableException) {
                Timber.w(cameraUnavailable, "Camera unavailable during ARCore session update")
                _trackingState.value = TrackingState.NotTracking
            }
        }

        private fun processFrame(frame: Frame) {
            val camera = frame.camera
            _trackingState.value = camera.trackingState.toDomain()

            val quaternion = camera.pose.rotationQuaternion
            _currentPose.tryEmit(
                DevicePose.fromQuaternion(
                    x = quaternion[0].toDouble(),
                    y = quaternion[1].toDouble(),
                    z = quaternion[2].toDouble(),
                    w = quaternion[3].toDouble()
                )
            )
            emitCameraFrame(frame)
        }

        private fun emitCameraFrame(frame: Frame) {
            val image = try {
                frame.acquireCameraImage()
            } catch (expected: NotYetAvailableException) {
                return
            }
            try {
                val yPlane = image.planes[0]
                _cameraFrames.tryEmit(
                    ArFrame(
                        luma = extractLuma(
                            buffer = yPlane.buffer,
                            rowStride = yPlane.rowStride,
                            width = image.width,
                            height = image.height
                        ),
                        width = image.width,
                        height = image.height
                    )
                )
            } finally {
                image.close()
            }
        }
    }

    companion object {
        private const val THREAD_NAME = "ArCameraRepository-GLThread"
        private const val PAUSED_POLL_INTERVAL_MS = 100L
    }
}
