package com.volta.app.data.ar

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import org.junit.Test

class ArFrameExtractorTest {

    @Test
    fun `strips row-stride padding and concatenates rows`() {
        // 2x2 image, rowStride 3 (1 padding byte per row): row0=[1,2,pad], row1=[3,4,pad]
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 9, 3, 4, 9))

        val luma = extractLuma(buffer = buffer, rowStride = 3, width = 2, height = 2)

        assertThat(luma).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun `copies straight through when rowStride equals width`() {
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))

        val luma = extractLuma(buffer = buffer, rowStride = 2, width = 2, height = 2)

        assertThat(luma).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun `handles a single row with no height beyond it`() {
        val buffer = ByteBuffer.wrap(byteArrayOf(5, 6, 7, 9))

        val luma = extractLuma(buffer = buffer, rowStride = 4, width = 3, height = 1)

        assertThat(luma).isEqualTo(byteArrayOf(5, 6, 7))
    }

    @Test
    fun `interleaves planar chroma planes into NV21 order behind the luma plane`() {
        val luma = byteArrayOf(1, 2, 3, 4)
        // 2x2 luma means a 1x1 chroma plane: one U sample, one V sample, pixelStride 1.
        val uBuffer = ByteBuffer.wrap(byteArrayOf(10))
        val vBuffer = ByteBuffer.wrap(byteArrayOf(20))

        val nv21 = extractNv21(
            luma = luma,
            uBuffer = uBuffer,
            uRowStride = 1,
            uPixelStride = 1,
            vBuffer = vBuffer,
            vRowStride = 1,
            vPixelStride = 1,
            width = 2,
            height = 2
        )

        assertThat(nv21).isEqualTo(byteArrayOf(1, 2, 3, 4, 20, 10))
    }

    @Test
    fun `strides over an interleaved semi-planar chroma layout`() {
        val luma = ByteArray(8) { (it + 1).toByte() }
        // 4x2 luma means a 2x1 chroma plane. Each plane's ByteBuffer already starts at that
        // plane's own first sample (Android's Image.Plane contract) - pixelStride 2 means
        // consecutive samples within a row are 2 bytes apart within that same buffer.
        val uBuffer = ByteBuffer.wrap(byteArrayOf(10, 0, 11, 0))
        val vBuffer = ByteBuffer.wrap(byteArrayOf(20, 0, 21, 0))

        val nv21 = extractNv21(
            luma = luma,
            uBuffer = uBuffer,
            uRowStride = 4,
            uPixelStride = 2,
            vBuffer = vBuffer,
            vRowStride = 4,
            vPixelStride = 2,
            width = 4,
            height = 2
        )

        assertThat(nv21).isEqualTo(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 20, 10, 21, 11))
    }

    @Test
    fun `strips chroma row-stride padding`() {
        val luma = ByteArray(4) { (it + 1).toByte() }
        // 2x2 luma means a 1x1 chroma plane, but each chroma row has 1 byte of padding.
        val uBuffer = ByteBuffer.wrap(byteArrayOf(10, 9))
        val vBuffer = ByteBuffer.wrap(byteArrayOf(20, 9))

        val nv21 = extractNv21(
            luma = luma,
            uBuffer = uBuffer,
            uRowStride = 2,
            uPixelStride = 1,
            vBuffer = vBuffer,
            vRowStride = 2,
            vPixelStride = 1,
            width = 2,
            height = 2
        )

        assertThat(nv21).isEqualTo(byteArrayOf(1, 2, 3, 4, 20, 10))
    }
}
