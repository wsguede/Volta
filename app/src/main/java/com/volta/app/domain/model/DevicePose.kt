package com.volta.app.domain.model

import kotlin.math.asin
import kotlin.math.atan2

/**
 * Device orientation in 3D space, all values in radians.
 * @param yaw Rotation around the vertical axis. Any value accepted; consumers normalize as needed.
 * @param pitch Elevation angle in [-π/2, π/2], where -π/2 is straight down and π/2 is straight up.
 * @param roll Rotation around the forward axis in [-π, π]. Not used for coverage tracking.
 */
data class DevicePose(val yaw: Double, val pitch: Double, val roll: Double) {

    companion object {
        /**
         * Builds a [DevicePose] from a world-space rotation quaternion (ARCore's
         * `Pose.rotationQuaternion` convention: right-handed, Y-up, camera looks down local -Z).
         */
        fun fromQuaternion(x: Double, y: Double, z: Double, w: Double): DevicePose {
            val sinPitch = (2.0 * (w * x - y * z)).coerceIn(-1.0, 1.0)
            return DevicePose(
                yaw = atan2(2.0 * (x * z + w * y), 1.0 - 2.0 * (x * x + y * y)),
                pitch = asin(sinPitch),
                roll = atan2(2.0 * (x * y + w * z), 1.0 - 2.0 * (x * x + z * z))
            )
        }
    }
}
