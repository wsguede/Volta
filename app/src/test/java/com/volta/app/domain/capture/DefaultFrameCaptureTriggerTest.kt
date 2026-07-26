package com.volta.app.domain.capture

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.model.DevicePose
import kotlin.math.PI
import org.junit.Test

private val ORIGIN = DevicePose(yaw = 0.0, pitch = 0.0, roll = 0.0)
private const val SHARP = 100f
private const val BLURRY = 0f

class DefaultFrameCaptureTriggerTest {

    private fun trigger(
        angularThresholdDegrees: Float = 15f,
        blurThreshold: Float = 50f,
        maxStoredFrames: Int = 150,
        onFrameDropped: () -> Unit = {}
    ) = DefaultFrameCaptureTrigger(
        angularThresholdDegrees = angularThresholdDegrees,
        blurThreshold = blurThreshold,
        maxStoredFrames = maxStoredFrames,
        onFrameDropped = onFrameDropped
    )

    private fun poseAt(yawDegrees: Double) =
        DevicePose(yaw = yawDegrees * PI / 180.0, pitch = 0.0, roll = 0.0)

    private fun jpegOf(vararg bytes: Byte) = { byteArrayOf(*bytes) }

    @Test
    fun `first capture succeeds regardless of angle when sharp`() {
        val event = trigger().captureIfNeeded(ORIGIN, SHARP, jpegOf(1))

        assertThat(event).isEqualTo(CaptureEvent(ORIGIN))
    }

    @Test
    fun `first capture is rejected when blurry`() {
        val event = trigger().captureIfNeeded(ORIGIN, BLURRY, jpegOf(1))

        assertThat(event).isNull()
    }

    @Test
    fun `does not invoke jpeg lambda when the frame is rejected`() {
        var jpegCalls = 0
        val jpeg = {
            jpegCalls++
            byteArrayOf(1)
        }

        trigger().captureIfNeeded(ORIGIN, BLURRY, jpeg)

        assertThat(jpegCalls).isEqualTo(0)
    }

    @Test
    fun `does not capture again within angular threshold of the last captured frame`() {
        val trigger = trigger(angularThresholdDegrees = 15f)
        trigger.captureIfNeeded(poseAt(0.0), SHARP, jpegOf(1))

        val event = trigger.captureIfNeeded(poseAt(10.0), SHARP, jpegOf(2))

        assertThat(event).isNull()
    }

    @Test
    fun `captures again once angular threshold from the last captured frame is met`() {
        val trigger = trigger(angularThresholdDegrees = 15f)
        trigger.captureIfNeeded(poseAt(0.0), SHARP, jpegOf(1))

        val event = trigger.captureIfNeeded(poseAt(15.0), SHARP, jpegOf(2))

        assertThat(event).isEqualTo(CaptureEvent(poseAt(15.0)))
    }

    @Test
    fun `does not capture past angular threshold when blurry`() {
        val trigger = trigger(angularThresholdDegrees = 15f)
        trigger.captureIfNeeded(poseAt(0.0), SHARP, jpegOf(1))

        val event = trigger.captureIfNeeded(poseAt(90.0), BLURRY, jpegOf(2))

        assertThat(event).isNull()
    }

    @Test
    fun `a successful capture appends to capturedFrames`() {
        val trigger = trigger()

        trigger.captureIfNeeded(ORIGIN, SHARP, jpegOf(1, 2, 3))

        assertThat(
            trigger.capturedFrames
        ).containsExactly(CapturedFrame(byteArrayOf(1, 2, 3), ORIGIN))
    }

    @Test
    fun `a successful capture updates capturedFrameCount`() {
        val trigger = trigger()

        trigger.captureIfNeeded(poseAt(0.0), SHARP, jpegOf(1))
        trigger.captureIfNeeded(poseAt(20.0), SHARP, jpegOf(2))

        assertThat(trigger.capturedFrameCount.value).isEqualTo(2)
    }

    @Test
    fun `a rejected capture does not update capturedFrameCount`() {
        val trigger = trigger()

        trigger.captureIfNeeded(ORIGIN, BLURRY, jpegOf(1))

        assertThat(trigger.capturedFrameCount.value).isEqualTo(0)
    }

    @Test
    fun `frame store drops oldest frame once cap is exceeded`() {
        val trigger = trigger(maxStoredFrames = 2)

        trigger.captureIfNeeded(poseAt(0.0), SHARP, jpegOf(1))
        trigger.captureIfNeeded(poseAt(20.0), SHARP, jpegOf(2))
        trigger.captureIfNeeded(poseAt(40.0), SHARP, jpegOf(3))

        assertThat(trigger.capturedFrames).containsExactly(
            CapturedFrame(byteArrayOf(2), poseAt(20.0)),
            CapturedFrame(byteArrayOf(3), poseAt(40.0))
        ).inOrder()
        assertThat(trigger.capturedFrameCount.value).isEqualTo(2)
    }

    @Test
    fun `dropping a frame past the cap invokes onFrameDropped`() {
        var dropCount = 0
        val trigger = trigger(maxStoredFrames = 2, onFrameDropped = { dropCount++ })

        trigger.captureIfNeeded(poseAt(0.0), SHARP, jpegOf(1))
        trigger.captureIfNeeded(poseAt(20.0), SHARP, jpegOf(2))
        trigger.captureIfNeeded(poseAt(40.0), SHARP, jpegOf(3))

        assertThat(dropCount).isEqualTo(1)
    }

    @Test
    fun `staying within the cap does not invoke onFrameDropped`() {
        var dropCount = 0
        val trigger = trigger(maxStoredFrames = 2, onFrameDropped = { dropCount++ })

        trigger.captureIfNeeded(poseAt(0.0), SHARP, jpegOf(1))

        assertThat(dropCount).isEqualTo(0)
    }

    @Test
    fun `default angular threshold matches the documented 15 degree starting point`() {
        assertThat(DefaultFrameCaptureTrigger.DEFAULT_ANGULAR_THRESHOLD_DEGREES).isEqualTo(15f)
    }

    @Test
    fun `default max stored frames matches the documented 150 frame cap`() {
        assertThat(DefaultFrameCaptureTrigger.DEFAULT_MAX_STORED_FRAMES).isEqualTo(150)
    }

    @Test
    fun `frames with identical content and pose are equal`() {
        val a = CapturedFrame(byteArrayOf(1, 2, 3), ORIGIN)
        val b = CapturedFrame(byteArrayOf(1, 2, 3), ORIGIN)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `frames with different content are not equal`() {
        val a = CapturedFrame(byteArrayOf(1, 2, 3), ORIGIN)
        val b = CapturedFrame(byteArrayOf(1, 2, 4), ORIGIN)

        assertThat(a).isNotEqualTo(b)
    }
}
