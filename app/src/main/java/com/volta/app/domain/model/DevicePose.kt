package com.volta.app.domain.model

/**
 * Device orientation in 3D space, all values in radians.
 * @param yaw Rotation around the vertical axis. Any value accepted; consumers normalize as needed.
 * @param pitch Elevation angle in [-π/2, π/2], where -π/2 is straight down and π/2 is straight up.
 * @param roll Rotation around the forward axis in [-π, π]. Not used for coverage tracking.
 */
data class DevicePose(val yaw: Double, val pitch: Double, val roll: Double)
