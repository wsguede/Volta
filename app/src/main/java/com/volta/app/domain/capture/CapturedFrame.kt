package com.volta.app.domain.capture

import com.volta.app.domain.model.DevicePose

data class CapturedFrame(val jpeg: ByteArray, val pose: DevicePose) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedFrame) return false
        return pose == other.pose && jpeg.contentEquals(other.jpeg)
    }

    override fun hashCode(): Int = 31 * jpeg.contentHashCode() + pose.hashCode()
}
