package com.volta.app.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.volta.app.di.IoDispatcher
import com.volta.app.domain.model.GpsCoordinates
import com.volta.app.domain.stitching.OutputResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

class MediaStorePhotoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataWriter: PanoramaMetadataWriter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PhotoExporter {

    override suspend fun saveToGallery(
        jpegData: ByteArray,
        outputResolution: OutputResolution,
        gpsCoordinates: GpsCoordinates?
    ): Result<String> = withContext(ioDispatcher) {
        val resolver = context.contentResolver
        var insertedUri: Uri? = null

        runCatching {
            val scratchFile = File.createTempFile(
                SCRATCH_FILE_PREFIX,
                SCRATCH_FILE_SUFFIX,
                context.cacheDir
            )
            val finalJpegData = try {
                metadataWriter.write(jpegData, outputResolution, gpsCoordinates, scratchFile)
            } finally {
                scratchFile.delete()
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "volta_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Volta"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore returned a null Uri for the photosphere export")
            insertedUri = uri

            resolver.openOutputStream(uri)?.use { it.write(finalJpegData) }
                ?: error("Unable to open an output stream for $uri")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )

            uri.toString()
        }.onFailure { failure ->
            Timber.e(failure, "Failed to export photosphere to the gallery")
            insertedUri?.let { resolver.delete(it, null, null) }
        }
    }

    private companion object {
        const val SCRATCH_FILE_PREFIX = "volta_export_"
        const val SCRATCH_FILE_SUFFIX = ".jpg"
    }
}
