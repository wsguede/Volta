package com.volta.app.data.export

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.stitching.OutputResolution
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertThrows
import org.junit.Test

class GPanoXmpInjectorTest {

    private fun sampleJpeg(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().use { stream ->
            ImageIO.write(image, "jpg", stream)
            stream.toByteArray()
        }
    }

    private fun xmpPacketOf(jpegWithXmp: ByteArray): String {
        val identifier = "http://ns.adobe.com/xap/1.0/\u0000"
        val text = jpegWithXmp.toString(Charsets.ISO_8859_1)
        val start = text.indexOf(identifier)
        check(start >= 0) { "XMP APP1 segment not found" }
        val packetStart = start + identifier.length
        val endTagStart = text.indexOf("<?xpacket end", packetStart)
        check(endTagStart >= 0) { "XMP packet end tag not found" }
        val packetEnd = text.indexOf("?>", endTagStart) + 2
        return text.substring(packetStart, packetEnd)
    }

    @Test
    fun `throws when input is not a valid JPEG`() {
        assertThrows(IllegalArgumentException::class.java) {
            GPanoXmpInjector.inject(byteArrayOf(0x00, 0x01), OutputResolution.STANDARD)
        }
    }

    @Test
    fun `preserves the SOI marker as the first two bytes`() {
        val result = GPanoXmpInjector.inject(sampleJpeg(), OutputResolution.STANDARD)

        assertThat(result[0]).isEqualTo(0xFF.toByte())
        assertThat(result[1]).isEqualTo(0xD8.toByte())
    }

    @Test
    fun `inserts an APP1 segment immediately after an existing JFIF APP0 segment`() {
        val original = sampleJpeg()
        val expectedOffset = insertionOffsetOf(original)
        check(expectedOffset > 2) { "Fixture JPEG unexpectedly has no leading APP0 segment" }

        val result = GPanoXmpInjector.inject(original, OutputResolution.STANDARD)

        assertThat(result[expectedOffset]).isEqualTo(0xFF.toByte())
        assertThat(result[expectedOffset + 1]).isEqualTo(0xE1.toByte())
    }

    @Test
    fun `inserts an APP1 segment immediately after SOI when there is no APP0 segment`() {
        val noApp0Jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte())

        val result = GPanoXmpInjector.inject(noApp0Jpeg, OutputResolution.STANDARD)

        assertThat(result[2]).isEqualTo(0xFF.toByte())
        assertThat(result[3]).isEqualTo(0xE1.toByte())
    }

    @Test
    fun `embeds GPano fields matching the standard output resolution`() {
        val result = GPanoXmpInjector.inject(sampleJpeg(), OutputResolution.STANDARD)
        val packet = xmpPacketOf(result)

        assertThat(packet).contains("GPano:UsePanoramaViewer=\"True\"")
        assertThat(packet).contains("GPano:ProjectionType=\"equirectangular\"")
        assertThat(packet).contains("GPano:FullPanoWidthPixels=\"8192\"")
        assertThat(packet).contains("GPano:FullPanoHeightPixels=\"4096\"")
        assertThat(packet).contains("GPano:CroppedAreaImageWidthPixels=\"8192\"")
        assertThat(packet).contains("GPano:CroppedAreaImageHeightPixels=\"4096\"")
        assertThat(packet).contains("GPano:CroppedAreaLeftPixels=\"0\"")
        assertThat(packet).contains("GPano:CroppedAreaTopPixels=\"0\"")
    }

    @Test
    fun `embeds GPano fields matching the minimum output resolution`() {
        val result = GPanoXmpInjector.inject(sampleJpeg(), OutputResolution.MINIMUM)
        val packet = xmpPacketOf(result)

        assertThat(packet).contains("GPano:FullPanoWidthPixels=\"4096\"")
        assertThat(packet).contains("GPano:FullPanoHeightPixels=\"2048\"")
    }

    @Test
    fun `original JPEG bytes follow the injected segment unmodified`() {
        val original = sampleJpeg()
        val insertionOffset = insertionOffsetOf(original)

        val result = GPanoXmpInjector.inject(original, OutputResolution.STANDARD)
        val segmentLength = ((result[insertionOffset + 2].toInt() and 0xFF) shl 8) or
            (result[insertionOffset + 3].toInt() and 0xFF)
        val tailOffset = insertionOffset + 2 + segmentLength

        val expectedTail = original.copyOfRange(insertionOffset, original.size)
        val actualTail = result.copyOfRange(tailOffset, result.size)
        assertThat(actualTail).isEqualTo(expectedTail)
    }

    // Mirrors GPanoXmpInjector's own APP0-skip rule so tests don't hardcode offset 2.
    private fun insertionOffsetOf(jpegData: ByteArray): Int {
        val hasApp0 = jpegData[2] == 0xFF.toByte() && jpegData[3] == 0xE0.toByte()
        if (!hasApp0) return 2
        val app0Length = ((jpegData[4].toInt() and 0xFF) shl 8) or (jpegData[5].toInt() and 0xFF)
        return 2 + 2 + app0Length
    }
}
