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

    private fun DefaultFrameCaptureTrigger.captureAt(yawDegrees: Double, jpeg: ByteArray) {
        val approval =
            evaluate(poseAt(yawDegrees), SHARP) ?: error("expected capture to be approved")
        record(approval, jpeg)
    }

    @Test
    fun `first evaluation approves regardless of angle when sharp`() {
        val approval = trigger().evaluate(ORIGIN, SHARP)

        assertThat(approval).isNotNull()
    }

    @Test
    fun `first evaluation is rejected when blurry`() {
        val approval = trigger().evaluate(ORIGIN, BLURRY)

        assertThat(approval).isNull()
    }

    @Test
    fun `evaluate does not mutate state, so repeated calls stay approvable`() {
        val trigger = trigger()

        trigger.evaluate(ORIGIN, SHARP)
        val secondApproval = trigger.evaluate(ORIGIN, SHARP)

        assertThat(secondApproval).isNotNull()
        assertThat(trigger.capturedFrameCount.value).isEqualTo(0)
    }

    @Test
    fun `does not approve again within angular threshold of the last recorded frame`() {
        val trigger = trigger(angularThresholdDegrees = 15f)
        trigger.captureAt(0.0, byteArrayOf(1))

        val approval = trigger.evaluate(poseAt(10.0), SHARP)

        assertThat(approval).isNull()
    }

    @Test
    fun `approves again once angular threshold from the last recorded frame is met`() {
        val trigger = trigger(angularThresholdDegrees = 15f)
        trigger.captureAt(0.0, byteArrayOf(1))

        val approval = trigger.evaluate(poseAt(15.0), SHARP)

        assertThat(approval).isNotNull()
    }

    @Test
    fun `does not approve past angular threshold when blurry`() {
        val trigger = trigger(angularThresholdDegrees = 15f)
        trigger.captureAt(0.0, byteArrayOf(1))

        val approval = trigger.evaluate(poseAt(90.0), BLURRY)

        assertThat(approval).isNull()
    }

    @Test
    fun `record returns a CaptureEvent carrying the approved pose`() {
        val trigger = trigger()
        val approval = trigger.evaluate(poseAt(30.0), SHARP)!!

        val event = trigger.record(approval, byteArrayOf(1))

        assertThat(event).isEqualTo(CaptureEvent(poseAt(30.0)))
    }

    @Test
    fun `record appends to capturedFrames`() {
        val trigger = trigger()
        val approval = trigger.evaluate(ORIGIN, SHARP)!!

        trigger.record(approval, byteArrayOf(1, 2, 3))

        assertThat(
            trigger.capturedFrames
        ).containsExactly(CapturedFrame(byteArrayOf(1, 2, 3), ORIGIN))
    }

    @Test
    fun `record updates capturedFrameCount`() {
        val trigger = trigger()

        trigger.captureAt(0.0, byteArrayOf(1))
        trigger.captureAt(20.0, byteArrayOf(2))

        assertThat(trigger.capturedFrameCount.value).isEqualTo(2)
    }

    @Test
    fun `frame store drops oldest frame once cap is exceeded`() {
        val trigger = trigger(maxStoredFrames = 2)

        trigger.captureAt(0.0, byteArrayOf(1))
        trigger.captureAt(20.0, byteArrayOf(2))
        trigger.captureAt(40.0, byteArrayOf(3))

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

        trigger.captureAt(0.0, byteArrayOf(1))
        trigger.captureAt(20.0, byteArrayOf(2))
        trigger.captureAt(40.0, byteArrayOf(3))

        assertThat(dropCount).isEqualTo(1)
    }

    @Test
    fun `staying within the cap does not invoke onFrameDropped`() {
        var dropCount = 0
        val trigger = trigger(maxStoredFrames = 2, onFrameDropped = { dropCount++ })

        trigger.captureAt(0.0, byteArrayOf(1))

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
