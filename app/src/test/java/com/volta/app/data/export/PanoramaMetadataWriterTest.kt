package com.volta.app.data.export

import androidx.exifinterface.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.model.GpsCoordinates
import com.volta.app.domain.stitching.OutputResolution
import io.mockk.mockk
import io.mockk.verify
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PanoramaMetadataWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val writer = PanoramaMetadataWriter()

    private fun sampleJpeg(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        return ByteArrayOutputStream().use { stream ->
            ImageIO.write(image, "jpg", stream)
            stream.toByteArray()
        }
    }

    @Test
    fun `embeds the GPano XMP packet in the returned bytes`() {
        val result = writer.write(
            jpegData = sampleJpeg(),
            outputResolution = OutputResolution.STANDARD,
            gpsCoordinates = null,
            scratchFile = tempFolder.newFile("scratch.jpg")
        )

        assertThat(
            result.toString(Charsets.ISO_8859_1)
        ).contains("GPano:FullPanoWidthPixels=\"8192\"")
    }

    @Test
    fun `the GPano XMP segment stays correctly framed and the file stays a decodable JPEG`() {
        val result = writer.write(
            jpegData = sampleJpeg(),
            outputResolution = OutputResolution.STANDARD,
            gpsCoordinates = null,
            scratchFile = tempFolder.newFile("scratch.jpg")
        )

        assertThat(xmpSegmentIsCorrectlyFramed(result)).isTrue()
        assertThat(ImageIO.read(ByteArrayInputStream(result))).isNotNull()
    }

    // Walks the JPEG marker chain from SOI looking for a length-correct APP1 segment carrying
    // the XMP identifier, rather than trusting a raw substring match against the byte array —
    // ExifInterface.saveAttributes() rewrites the file after GPano injection, so this guards
    // against that rewrite corrupting the segment's length framing or position.
    private fun xmpSegmentIsCorrectlyFramed(jpegData: ByteArray): Boolean {
        val xmpIdentifier = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        var offset = 2
        while (offset + 4 <= jpegData.size && jpegData[offset] == 0xFF.toByte()) {
            val marker = jpegData[offset + 1].toInt() and 0xFF
            if (marker == 0xD9 || marker == 0xDA) return false
            val length = ((jpegData[offset + 2].toInt() and 0xFF) shl 8) or
                (jpegData[offset + 3].toInt() and 0xFF)
            val segmentEnd = offset + 2 + length
            if (segmentEnd > jpegData.size) return false
            if (marker == 0xE1 && startsWith(jpegData, offset + 4, xmpIdentifier)) {
                return true
            }
            offset = segmentEnd
        }
        return false
    }

    private fun startsWith(data: ByteArray, start: Int, prefix: ByteArray): Boolean {
        if (start + prefix.size > data.size) return false
        return (prefix.indices).all { data[start + it] == prefix[it] }
    }

    @Test
    fun `writes no GPS EXIF tags when coordinates are null`() {
        val scratchFile = tempFolder.newFile("scratch.jpg")

        writer.write(
            jpegData = sampleJpeg(),
            outputResolution = OutputResolution.STANDARD,
            gpsCoordinates = null,
            scratchFile = scratchFile
        )

        val exif = ExifInterface(scratchFile.absolutePath)
        assertThat(exif.getLatLong(FloatArray(2))).isFalse()
    }

    @Test
    fun `applyGpsTags sets latitude, longitude and altitude when coordinates are present`() {
        val exif = mockk<ExifInterface>(relaxed = true)

        writer.applyGpsTags(
            exif,
            GpsCoordinates(
                latitude = 47.6062,
                longitude = -122.3321,
                altitude = 56.4,
                accuracyMeters = 5f
            )
        )

        verify { exif.setLatLong(47.6062, -122.3321) }
        verify { exif.setAltitude(56.4) }
    }

    @Test
    fun `applyGpsTags omits altitude when coordinates have no altitude`() {
        val exif = mockk<ExifInterface>(relaxed = true)

        writer.applyGpsTags(
            exif,
            GpsCoordinates(
                latitude = 47.6062,
                longitude = -122.3321,
                altitude = null,
                accuracyMeters = 5f
            )
        )

        verify { exif.setLatLong(47.6062, -122.3321) }
        verify(exactly = 0) { exif.setAltitude(any()) }
    }

    @Test
    fun `applyGpsTags does nothing when coordinates are null`() {
        val exif = mockk<ExifInterface>(relaxed = true)

        writer.applyGpsTags(exif, null)

        verify(exactly = 0) { exif.setLatLong(any(), any()) }
        verify(exactly = 0) { exif.setAltitude(any()) }
    }
}
