package com.volta.app.data.ar

import java.nio.ByteBuffer

/**
 * Copies a YUV_420_888 Y-plane out of [buffer] into a tightly packed [ByteArray], stripping any
 * row-stride padding ([rowStride] can exceed [width] for memory alignment).
 */
internal fun extractLuma(buffer: ByteBuffer, rowStride: Int, width: Int, height: Int): ByteArray {
    val luma = ByteArray(width * height)
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        buffer.get(luma, row * width, width)
    }
    return luma
}
