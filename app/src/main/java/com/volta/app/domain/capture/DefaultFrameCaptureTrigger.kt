package com.volta.app.domain.capture

import com.volta.app.domain.model.DevicePose
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Frame store caps at [maxStoredFrames]: worst case ~150 x 4 MB compressed JPEGs, ~600 MB — a
 * known memory constraint, not a bug. See ADR 0015.
 *
 * [evaluate] and [record] run on the AR processing thread that pumps `ArSessionManager` poses (the
 * `GLSurfaceView` render thread per ADR 0014); [capturedFrames]/[capturedFrameCount] are read
 * later from a different dispatcher (e.g. stitching on `Dispatchers.Default`). Methods are
 * [Synchronized] for that cross-thread handoff, not because concurrent writers are expected.
 * JPEG compression happens in the caller, between [evaluate] and [record] — keep it there; putting
 * it inside either method would block whichever thread drives them with CPU-bound work.
 */
class DefaultFrameCaptureTrigger(
    private val angularThresholdDegrees: Float = DEFAULT_ANGULAR_THRESHOLD_DEGREES,
    private val blurThreshold: Float = DEFAULT_BLUR_THRESHOLD,
    private val maxStoredFrames: Int = DEFAULT_MAX_STORED_FRAMES,
    private val onFrameDropped: () -> Unit = {}
) : FrameCaptureTrigger {

    private val frames = mutableListOf<CapturedFrame>()
    private var lastCapturedPose: DevicePose? = null

    private val _capturedFrameCount = MutableStateFlow(0)
    override val capturedFrameCount: StateFlow<Int> = _capturedFrameCount.asStateFlow()

    override val capturedFrames: List<CapturedFrame>
        get() = synchronized(this) { frames.toList() }

    @Synchronized
    override fun evaluate(pose: DevicePose, sharpnessScore: Float): CaptureApproval? {
        if (sharpnessScore < blurThreshold) return null
        val last = lastCapturedPose
        if (last != null &&
            angularDistanceDegrees(pose, last) + ANGULAR_EPSILON_DEGREES < angularThresholdDegrees
        ) {
            return null
        }
        return CaptureApproval(pose)
    }

    @Synchronized
    override fun record(approval: CaptureApproval, jpeg: ByteArray): CaptureEvent {
        val pose = approval.pose
        frames.add(CapturedFrame(jpeg, pose))
        lastCapturedPose = pose
        if (frames.size > maxStoredFrames) {
            frames.removeAt(0)
            onFrameDropped()
        }
        _capturedFrameCount.value = frames.size
        return CaptureEvent(pose)
    }

    // Great-circle angular distance treating yaw as longitude and pitch as latitude; roll is
    // ignored, matching DevicePose's own convention for coverage tracking.
    private fun angularDistanceDegrees(a: DevicePose, b: DevicePose): Double {
        val cosAngle =
            sin(a.pitch) * sin(b.pitch) + cos(a.pitch) * cos(b.pitch) * cos(a.yaw - b.yaw)
        return acos(cosAngle.coerceIn(-1.0, 1.0)) * (180.0 / PI)
    }

    companion object {
        const val DEFAULT_ANGULAR_THRESHOLD_DEGREES = 15f

        // Single source of truth with LaplacianBlurDetector.DEFAULT_SHARPNESS_THRESHOLD — both
        // describe the same "is this frame sharp enough" cutoff. Keeping one constant prevents
        // silent drift once either gets tuned against real device frames (#46).
        const val DEFAULT_BLUR_THRESHOLD = LaplacianBlurDetector.DEFAULT_SHARPNESS_THRESHOLD
        const val DEFAULT_MAX_STORED_FRAMES = 150

        // Tolerance for trigonometric rounding error, so an exact-threshold pose (e.g. precisely
        // 15° away) isn't spuriously rejected by floating-point noise in angularDistanceDegrees.
        private const val ANGULAR_EPSILON_DEGREES = 1e-4
    }
}
