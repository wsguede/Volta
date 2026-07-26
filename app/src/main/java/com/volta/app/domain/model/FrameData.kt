package com.volta.app.domain.model

// TODO(#40): No longer referenced now that CapturedFrame (domain/capture) holds a compressed
// JPEG ByteArray directly instead of a FrameData. Consolidate with ArFrame, which is structurally
// identical, or remove this type.
data class FrameData(val data: ByteArray, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FrameData) return false
        return width == other.width && height == other.height && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * (31 * data.contentHashCode() + width) + height
}
