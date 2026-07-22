package com.volta.app.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.model.GpsCoordinates
import com.volta.app.domain.stitching.OutputResolution
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaStorePhotoExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val metadataWriter = mockk<PanoramaMetadataWriter>()
    private val uri = mockk<Uri>()
    private val jpegData = byteArrayOf(1, 2, 3)
    private val writtenBytes = byteArrayOf(9, 8, 7)

    private fun exporter() = MediaStorePhotoExporter(
        context = context,
        metadataWriter = metadataWriter,
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun stubMetadataWriter(result: ByteArray = writtenBytes) {
        every {
            metadataWriter.write(any(), any(), any(), any())
        } returns result
        every { context.cacheDir } returns tempFolder.root
    }

    @Test
    fun `writes metadata bytes to the inserted MediaStore Uri and returns its string form`() =
        runTest {
            stubMetadataWriter()
            every { context.contentResolver } returns resolver
            every { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, any()) } returns
                uri
            val outputStream = ByteArrayOutputStream()
            every { resolver.openOutputStream(uri) } returns outputStream
            every { resolver.update(uri, any(), null, null) } returns 1
            every { uri.toString() } returns "content://media/external/images/media/42"

            val result = exporter().saveToGallery(jpegData, OutputResolution.STANDARD, null)

            assertThat(result.getOrNull()).isEqualTo("content://media/external/images/media/42")
            assertThat(outputStream.toByteArray()).isEqualTo(writtenBytes)
            verify { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, any()) }
            verify { resolver.update(uri, any(), null, null) }
        }

    @Test
    fun `passes the jpeg bytes, resolution and coordinates through to the metadata writer`() =
        runTest {
            stubMetadataWriter()
            every { context.contentResolver } returns resolver
            every { resolver.insert(any(), any()) } returns uri
            every { resolver.openOutputStream(uri) } returns ByteArrayOutputStream()
            every { resolver.update(any(), any(), any(), any()) } returns 1
            every { uri.toString() } returns "content://media/external/images/media/42"
            val coordinates = GpsCoordinates(47.6062, -122.3321, 56.4, 5f)

            exporter().saveToGallery(jpegData, OutputResolution.MINIMUM, coordinates)

            verify { metadataWriter.write(jpegData, OutputResolution.MINIMUM, coordinates, any()) }
        }

    @Test
    fun `deletes the scratch file after writing metadata regardless of outcome`() = runTest {
        stubMetadataWriter()
        every { context.contentResolver } returns resolver
        every { resolver.insert(any(), any()) } returns uri
        every { resolver.openOutputStream(uri) } returns ByteArrayOutputStream()
        every { resolver.update(any(), any(), any(), any()) } returns 1
        every { uri.toString() } returns "content://media/external/images/media/42"

        exporter().saveToGallery(jpegData, OutputResolution.STANDARD, null)

        assertThat(tempFolder.root.listFiles()).isEmpty()
    }

    @Test
    fun `fails and does not touch MediaStore when the metadata writer throws`() = runTest {
        every { metadataWriter.write(any(), any(), any(), any()) } throws IOException("bad jpeg")
        every { context.cacheDir } returns tempFolder.root
        every { context.contentResolver } returns resolver

        val result = exporter().saveToGallery(jpegData, OutputResolution.STANDARD, null)

        assertThat(result.isFailure).isTrue()
        verify(exactly = 0) { resolver.insert(any(), any()) }
    }

    @Test
    fun `fails when MediaStore insert returns a null Uri`() = runTest {
        stubMetadataWriter()
        every { context.contentResolver } returns resolver
        every { resolver.insert(any(), any()) } returns null

        val result = exporter().saveToGallery(jpegData, OutputResolution.STANDARD, null)

        assertThat(result.isFailure).isTrue()
        verify(exactly = 0) { resolver.delete(any<Uri>(), any(), any()) }
    }

    @Test
    fun `deletes the inserted row when opening the output stream fails`() = runTest {
        stubMetadataWriter()
        every { context.contentResolver } returns resolver
        every { resolver.insert(any(), any()) } returns uri
        every { resolver.openOutputStream(uri) } returns null
        every { resolver.delete(uri, null, null) } returns 1

        val result = exporter().saveToGallery(jpegData, OutputResolution.STANDARD, null)

        assertThat(result.isFailure).isTrue()
        verify { resolver.delete(uri, null, null) }
    }

    @Test
    fun `deletes the inserted row when writing the output stream throws`() = runTest {
        stubMetadataWriter()
        every { context.contentResolver } returns resolver
        every { resolver.insert(any(), any()) } returns uri
        every { resolver.openOutputStream(uri) } throws IOException("disk full")
        every { resolver.delete(uri, null, null) } returns 1

        val result = exporter().saveToGallery(jpegData, OutputResolution.STANDARD, null)

        assertThat(result.isFailure).isTrue()
        verify { resolver.delete(uri, null, null) }
    }
}
