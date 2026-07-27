package com.volta.app.data.ar

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
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

/**
 * Builds an NV21 byte array (the layout [android.graphics.YuvImage] requires) from [luma] plus a
 * YUV_420_888 image's U/V planes, handling both fully-planar (pixelStride 1, separate buffers) and
 * semi-planar/interleaved (pixelStride 2) chroma layouts via absolute [ByteBuffer.get] reads, which
 * don't disturb either buffer's position. NV21 orders chroma as V,U (not U,V) per sample.
 */
internal fun extractNv21(
    luma: ByteArray,
    uBuffer: ByteBuffer,
    uRowStride: Int,
    uPixelStride: Int,
    vBuffer: ByteBuffer,
    vRowStride: Int,
    vPixelStride: Int,
    width: Int,
    height: Int
): ByteArray {
    require(width % 2 == 0 && height % 2 == 0) {
        "width ($width) and height ($height) must both be even; YUV_420_888 chroma planes are " +
            "subsampled 2x2 and a non-even dimension would silently truncate the last row/column"
    }
    val chromaWidth = width / 2
    val chromaHeight = height / 2
    val nv21 = ByteArray(luma.size + 2 * chromaWidth * chromaHeight)
    luma.copyInto(nv21)
    var offset = luma.size
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            nv21[offset++] = vBuffer.get(row * vRowStride + col * vPixelStride)
            nv21[offset++] = uBuffer.get(row * uRowStride + col * uPixelStride)
        }
    }
    return nv21
}

/**
 * Compresses [nv21] to a JPEG. Thin by design: [android.graphics.YuvImage.compressToJpeg] can't be
 * exercised for real against Volta's stub `android.jar` in local unit tests (see
 * `android-stub-unit-test-limits`), so the actual pixel-format logic worth testing lives in
 * [extractNv21] instead — this wrapper is intentionally left uncovered, the same way
 * `PanoramaMetadataWriter.write` leaves the real `ExifInterface.saveAttributes()` call uncovered.
 */
internal fun encodeNv21ToJpeg(nv21: ByteArray, width: Int, height: Int, quality: Int): ByteArray {
    val output = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, width, height, null)
        .compressToJpeg(Rect(0, 0, width, height), quality, output)
    return output.toByteArray()
}
