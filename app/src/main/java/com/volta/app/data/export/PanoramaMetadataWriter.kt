package com.volta.app.data.export

import androidx.exifinterface.media.ExifInterface
import com.volta.app.domain.model.GpsCoordinates
import com.volta.app.domain.stitching.OutputResolution
import java.io.File
import javax.inject.Inject

class PanoramaMetadataWriter @Inject constructor() {

    fun write(
        jpegData: ByteArray,
        outputResolution: OutputResolution,
        gpsCoordinates: GpsCoordinates?,
        scratchFile: File
    ): ByteArray {
        scratchFile.writeBytes(GPanoXmpInjector.inject(jpegData, outputResolution))

        val exif = ExifInterface(scratchFile.absolutePath)
        applyGpsTags(exif, gpsCoordinates)
        exif.saveAttributes()

        return scratchFile.readBytes()
    }

    // Package-visible so tests can verify the tagging logic against a mocked ExifInterface
    // instead of exercising its real setAttribute path, which requires a live Android runtime.
    internal fun applyGpsTags(exif: ExifInterface, gpsCoordinates: GpsCoordinates?) {
        gpsCoordinates?.let { coordinates ->
            exif.setLatLong(coordinates.latitude, coordinates.longitude)
            coordinates.altitude?.let(exif::setAltitude)
        }
    }
}
