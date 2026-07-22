package com.volta.app.data.export

import androidx.exifinterface.media.ExifInterface
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.model.GpsCoordinates
import com.volta.app.domain.stitching.OutputResolution
import io.mockk.mockk
import io.mockk.verify
import java.awt.image.BufferedImage
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
