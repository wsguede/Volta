package com.volta.app.data.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import android.view.WindowManager
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.volta.app.di.ApplicationScope
import com.volta.app.di.DefaultDispatcher
import com.volta.app.domain.ar.ArSessionManager
import com.volta.app.domain.capture.BlurDetector
import com.volta.app.domain.capture.FrameCaptureTrigger
import com.volta.app.domain.coverage.CoverageTracker
import com.volta.app.domain.model.ArFrame
import com.volta.app.domain.model.DevicePose
import com.volta.app.domain.model.TrackingState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * `onSurfaceCreated`/`onSurfaceChanged`/`onDrawFrame`/[flushPendingPause] are all invoked on the
 * single GL thread [GLSurfaceView] owns ([flushPendingPause] via `GLSurfaceView.queueEvent`, per
 * its own doc), so the fields they touch need no synchronization between each other. [resume]/
 * [pause] are the only entry points called from another thread (the ViewModel, on the main
 * thread), hence [resumed] alone is an [AtomicBoolean].
 */
@Singleton
class ArCameraRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blurDetector: BlurDetector,
    private val frameCaptureTrigger: FrameCaptureTrigger,
    private val coverageTracker: CoverageTracker,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ArSessionManager,
    GLSurfaceView.Renderer {

    private val resumed = AtomicBoolean(false)
    private val cameraQuadRenderer = CameraQuadRenderer()
    private val sphereOverlayRenderer = SphereOverlayRenderer()

    // Parent job for in-flight JPEG-compression coroutines only (not all of applicationScope), so
    // cancelPendingCaptures() can cancel just those without touching unrelated application-scoped
    // work. @Volatile for visibility across the GL thread (launches) and the caller thread of
    // cancelPendingCaptures() (the ViewModel, on the main thread) — see its own doc for why this
    // exists. Once cancelled a Job can't be reused, so cancelPendingCaptures() replaces it.
    @Volatile
    private var captureJobs: Job = Job()

    private var cameraTextureId = 0
    private var activeSession: Session? = null
    private var hasRenderableFrame = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayRotation = Surface.ROTATION_0
    private var displayGeometryDirty = false

    private val viewMatrix = FloatArray(MATRIX_SIZE)
    private val projectionMatrix = FloatArray(MATRIX_SIZE)
    private val viewProjectionMatrix = FloatArray(MATRIX_SIZE)

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
        onSessionStopped = {
            _trackingState.value = TrackingState.NotTracking
            // Otherwise the last successfully drawn frame stays frozen on screen with no cue
            // that the camera/tracking was actually lost.
            hasRenderableFrame = false
        }
    )
    private val ticker = GatedSessionTicker(orchestrator, TickScheduler())

    override fun resume() {
        resumed.set(true)
    }

    override fun pause() {
        resumed.set(false)
    }

    /**
     * Must be called on the GL thread (e.g. via `GLSurfaceView.queueEvent`) — uses
     * [GatedSessionTicker.forceTick] rather than [onDrawFrame]'s normal (rate-limited)
     * [GatedSessionTicker.tickIfScheduled] path, since this exists specifically to guarantee
     * `Session.pause()` has actually run after [pause]: mid-backoff (a scheduled retry still
     * pending, up to [ArSessionOrchestrator.UNAVAILABLE_RETRY_INTERVAL_MS] away), the rate-limited
     * path would silently no-op while a caller believed the pause had landed.
     */
    override fun flushPendingPause() {
        ticker.forceTick(resumed.get()) { unexpected ->
            Timber.e(unexpected, "Unexpected error flushing a pending ARCore session pause")
        }
    }

    override fun cancelPendingCaptures() {
        captureJobs.cancel()
        captureJobs = Job()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        cameraTextureId = createExternalTexture()
        cameraQuadRenderer.createOnGlThread()
        sphereOverlayRenderer.createOnGlThread()
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
        displayRotation = currentDisplayRotation()
        displayGeometryDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        ticker.tickIfScheduled(resumed.get()) { unexpected ->
            Timber.e(unexpected, "Unexpected error in the ARCore session loop")
        }
        if (hasRenderableFrame) {
            cameraQuadRenderer.draw(cameraTextureId)
            sphereOverlayRenderer.updateGrid(coverageTracker.coverageGrid.value)
            sphereOverlayRenderer.draw(viewProjectionMatrix)
        } else {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        }
    }

    // WindowManager.getDefaultDisplay() is deprecated in favor of a Display obtained from a
    // UI-associated Context, which @ApplicationContext is not (Context.getDisplay() throws
    // UnsupportedOperationException on it). The deprecated API still returns the correct rotation
    // for Volta's single-display, single-window use case.
    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager.defaultDisplay.rotation
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

    private fun resumeRealSession(session: Session): Boolean = try {
        session.resume()
        true
    } catch (cameraUnavailable: CameraNotAvailableException) {
        Timber.w(cameraUnavailable, "Camera unavailable while resuming ARCore session")
        false
    }

    private fun pumpRealSession(session: Session): ArSessionOrchestrator.PumpResult = try {
        if (displayGeometryDirty && displayWidth > 0 && displayHeight > 0) {
            session.setDisplayGeometry(displayRotation, displayWidth, displayHeight)
            displayGeometryDirty = false
        }
        processFrame(session.update())
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
        val pose = DevicePose.fromQuaternion(
            x = quaternion[0].toDouble(),
            y = quaternion[1].toDouble(),
            z = quaternion[2].toDouble(),
            w = quaternion[3].toDouble()
        )
        _currentPose.tryEmit(pose)
        if (frame.hasDisplayGeometryChanged()) {
            cameraQuadRenderer.updateTransform(frame)
        }
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, CAMERA_NEAR_PLANE, CAMERA_FAR_PLANE)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        hasRenderableFrame = true
        emitCameraFrame(frame, pose)
    }

    /**
     * Extracts the Y plane (for the [cameraFrames] flow and blur scoring) and, only when
     * [FrameCaptureTrigger.evaluate] approves the frame, the full NV21 image. JPEG compression is
     * genuinely CPU-bound, so per [FrameCaptureTrigger.evaluate]'s contract it must not run on this
     * (GL) thread — the NV21 bytes are copied out synchronously here, while the image is still
     * open, then handed to a [defaultDispatcher] coroutine that compresses and calls
     * [FrameCaptureTrigger.record] and [CoverageTracker.markCovered].
     *
     * [BlurDetector.sharpnessScore] is itself a non-trivial O(width×height) pass, run once per
     * frame at up to the display's refresh rate — [FrameCaptureTrigger.isFarEnoughToCapture] is
     * checked first so it's skipped entirely on frames [FrameCaptureTrigger.evaluate] would reject
     * on angular-spacing grounds regardless (the common case between two capture points).
     */
    private fun emitCameraFrame(frame: Frame, pose: DevicePose) {
        val image = try {
            frame.acquireCameraImage()
        } catch (expected: NotYetAvailableException) {
            return
        }
        try {
            val yPlane = image.planes[0]
            val luma = extractLuma(
                buffer = yPlane.buffer,
                rowStride = yPlane.rowStride,
                width = image.width,
                height = image.height
            )
            _cameraFrames.tryEmit(ArFrame(luma = luma, width = image.width, height = image.height))

            if (!frameCaptureTrigger.isFarEnoughToCapture(pose)) return
            val sharpness = blurDetector.sharpnessScore(luma, image.width, image.height)
            val approval = frameCaptureTrigger.evaluate(pose, sharpness) ?: return
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val nv21 = try {
                extractNv21(
                    luma = luma,
                    uBuffer = uPlane.buffer,
                    uRowStride = uPlane.rowStride,
                    uPixelStride = uPlane.pixelStride,
                    vBuffer = vPlane.buffer,
                    vRowStride = vPlane.rowStride,
                    vPixelStride = vPlane.pixelStride,
                    width = image.width,
                    height = image.height
                )
            } catch (unexpectedDimensions: IllegalArgumentException) {
                // Camera resolutions are effectively always even, but this is real ARCore/OEM
                // camera-HAL data, not something we control — drop this one frame rather than
                // crashing the GL thread if that assumption is ever wrong.
                Timber.w(
                    unexpectedDimensions,
                    "Dropping an approved frame with odd image dimensions (%dx%d)",
                    image.width,
                    image.height
                )
                return
            }
            // Each approved frame gets its own launch with no coalescing/backpressure. Not a
            // problem today: the angular-spacing threshold this same call was just approved
            // against keeps approvals naturally spaced out. Revisit if that threshold is ever
            // tuned loose enough for approvals to cluster faster than compression drains them.
            // Scoped under captureJobs (not applicationScope's own Job) so cancelPendingCaptures()
            // can cancel exactly this in-flight work — see its doc and ArSessionManager's.
            applicationScope.launch(defaultDispatcher + captureJobs) {
                val jpeg = encodeNv21ToJpeg(nv21, image.width, image.height, JPEG_QUALITY)
                // Cooperative cancellation only takes effect at a check like this one — without
                // it, a job already past this point when cancelPendingCaptures() runs would still
                // go on to write a stale frame into the freshly-reset session state.
                ensureActive()
                frameCaptureTrigger.record(approval, jpeg)
                coverageTracker.markCovered(pose)
            }
        } finally {
            image.close()
        }
    }

    private companion object {
        const val JPEG_QUALITY = 90
        const val MATRIX_SIZE = 16

        // Clipping planes for the sphere overlay's view/projection matrices (SphereOverlayRenderer
        // draws its mesh at SphereOverlayRadius, comfortably inside this range).
        const val CAMERA_NEAR_PLANE = 0.1f
        const val CAMERA_FAR_PLANE = 100f
    }
}
