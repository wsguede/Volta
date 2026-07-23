package com.volta.app.domain.model

import com.google.common.truth.Truth.assertThat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Test

class DevicePoseTest {

    private val tolerance = 1e-4

    @Test
    fun `identity quaternion yields zero yaw, pitch and roll`() {
        val pose = DevicePose.fromQuaternion(x = 0.0, y = 0.0, z = 0.0, w = 1.0)

        assertThat(pose.yaw).isWithin(tolerance).of(0.0)
        assertThat(pose.pitch).isWithin(tolerance).of(0.0)
        assertThat(pose.roll).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `a thirty degree rotation about the world Y axis yields thirty degrees of yaw only`() {
        val halfAngle = (PI / 6) / 2
        val pose = DevicePose.fromQuaternion(x = 0.0, y = sin(halfAngle), z = 0.0, w = cos(halfAngle))

        assertThat(pose.yaw).isWithin(tolerance).of(PI / 6)
        assertThat(pose.pitch).isWithin(tolerance).of(0.0)
        assertThat(pose.roll).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `a thirty degree rotation about the local X axis yields thirty degrees of pitch only`() {
        val halfAngle = (PI / 6) / 2
        val pose = DevicePose.fromQuaternion(x = sin(halfAngle), y = 0.0, z = 0.0, w = cos(halfAngle))

        assertThat(pose.yaw).isWithin(tolerance).of(0.0)
        assertThat(pose.pitch).isWithin(tolerance).of(PI / 6)
        assertThat(pose.roll).isWithin(tolerance).of(0.0)
    }

    @Test
    fun `a thirty degree rotation about the local Z axis yields thirty degrees of roll only`() {
        val halfAngle = (PI / 6) / 2
        val pose = DevicePose.fromQuaternion(x = 0.0, y = 0.0, z = sin(halfAngle), w = cos(halfAngle))

        assertThat(pose.yaw).isWithin(tolerance).of(0.0)
        assertThat(pose.pitch).isWithin(tolerance).of(0.0)
        assertThat(pose.roll).isWithin(tolerance).of(PI / 6)
    }
}
