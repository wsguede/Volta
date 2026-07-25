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
}
