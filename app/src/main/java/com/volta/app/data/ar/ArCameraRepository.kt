package com.volta.app.data.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.volta.app.domain.ar.ArSessionManager
import com.volta.app.domain.model.ArFrame
import com.volta.app.domain.model.DevicePose
import com.volta.app.domain.model.TrackingState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Owns the ARCore [Session] end-to-end, driven by the GL thread that [GLSurfaceView] creates and
 * manages for the capture screen's camera preview — see ADR 0014 for why session ownership lives
 * on the on-screen render thread rather than a headless background thread (the earlier design).
 *
 * `@Singleton` at the class level (in addition to [com.volta.app.di.ArModule]'s `@Binds`) is
 * required: ARCore needs exclusive camera ownership (ADR 0013), so a second unscoped instance
 * would spin up a second native `Session` and camera texture.
 *
 * `onSurfaceCreated`/`onSurfaceChanged`/`onDrawFrame` are all invoked on the single GL thread
 * [GLSurfaceView] owns, so the fields they touch need no synchronization between each other.
 * [resume]/[pause] are the only entry points called from another thread (the ViewModel, on the
 * main thread), hence [resumed] alone is an [AtomicBoolean].
 */
@Singleton
class ArCameraRepository @Inject constructor(@ApplicationContext private val context: Context) :
    ArSessionManager,
    GLSurfaceView.Renderer {

    private val resumed = AtomicBoolean(false)
    private val tickScheduler = TickScheduler()
    private val cameraQuadRenderer = CameraQuadRenderer()

    private var cameraTextureId = 0
    private var activeSession: Session? = null
    private var hasRenderableFrame = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayGeometryDirty = false

    private val _isAvailable = MutableStateFlow(false)

    /** True once an ARCore [Session] has been constructed — not a guarantee the camera is
     * actively streaming; consult [trackingState] for live streaming/tracking status. */
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

    private val orchestrator = ArSessionOrchestrator(
        createSession = ::createRealSession,
        resumeSession = ::resumeRealSession,
        pauseSession = { it.pause() },
        pumpSession = ::pumpRealSession,
        onAvailabilityChanged = { available -> _isAvailable.value = available },
        onTrackingLost = { _trackingState.value = TrackingState.NotTracking }
    )

    override fun resume() {
        resumed.set(true)
    }

    override fun pause() {
        resumed.set(false)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        cameraTextureId = createExternalTexture()
        cameraQuadRenderer.createOnGlThread()
        hasRenderableFrame = false
        // The Session (if one already exists from before this surface was torn down, e.g. after
        // GLSurfaceView.onPause()/onResume()) must be rebound to the newly created texture — the
        // old one belonged to a now-destroyed GL context.
        activeSession?.setCameraTextureName(cameraTextureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        displayWidth = width
        displayHeight = height
        displayGeometryDirty = true
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onDrawFrame(gl: GL10?) {
        if (tickScheduler.shouldTick()) {
            val delayMs = runCatching { orchestrator.tick(resumed.get()) }
                .onFailure { unexpected ->
                    Timber.e(unexpected, "Unexpected error in the ARCore session loop")
                }
                .getOrDefault(ArSessionOrchestrator.PAUSED_POLL_INTERVAL_MS)
            tickScheduler.scheduleNextTick(delayMs)
        }
        if (hasRenderableFrame) {
            cameraQuadRenderer.draw(cameraTextureId)
        } else {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        }
    }

    private fun createExternalTexture(): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0])
        return textureIds[0]
    }

    private fun createRealSession(): Session? = try {
        Session(context).also {
            it.setCameraTextureName(cameraTextureId)
            activeSession = it
        }
    } catch (unavailable: UnavailableException) {
        Timber.e(unavailable, "ARCore session unavailable on this device")
        null
    }

    private fun resumeRealSession(activeSession: Session): Boolean = try {
        activeSession.resume()
        true
    } catch (cameraUnavailable: CameraNotAvailableException) {
        Timber.w(cameraUnavailable, "Camera unavailable while resuming ARCore session")
        false
    }

    private fun pumpRealSession(activeSession: Session): ArSessionOrchestrator.PumpResult = try {
        if (displayGeometryDirty && displayWidth > 0 && displayHeight > 0) {
            activeSession.setDisplayGeometry(DISPLAY_ROTATION, displayWidth, displayHeight)
            displayGeometryDirty = false
        }
        processFrame(activeSession.update())
        ArSessionOrchestrator.PumpResult.PROCESSED
    } catch (expected: NotYetAvailableException) {
        // No new frame since the last update() call — expected; ARCore paces this
        // internally to the camera's actual frame rate.
        ArSessionOrchestrator.PumpResult.NO_NEW_FRAME
    } catch (cameraUnavailable: CameraNotAvailableException) {
        Timber.w(cameraUnavailable, "Camera unavailable during ARCore session update")
        ArSessionOrchestrator.PumpResult.CAMERA_UNAVAILABLE
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
        if (frame.hasDisplayGeometryChanged()) {
            cameraQuadRenderer.updateTransform(frame)
        }
        hasRenderableFrame = true
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

    private companion object {
        // MainActivity is locked to android:screenOrientation="portrait" (AndroidManifest.xml),
        // so the display rotation relative to the natural orientation is fixed for the app's
        // entire lifetime — no DisplayListener/rotation-change tracking is needed.
        const val DISPLAY_ROTATION = android.view.Surface.ROTATION_0
    }
}
