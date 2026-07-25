package com.volta.app.domain.model

data class ArFrame(val luma: ByteArray, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArFrame) return false
        return width == other.width && height == other.height && luma.contentEquals(other.luma)
    }

    override fun hashCode(): Int = 31 * (31 * luma.contentHashCode() + width) + height
}
