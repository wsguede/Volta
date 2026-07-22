package com.volta.app.data.export

import com.volta.app.domain.model.GpsCoordinates
import com.volta.app.domain.stitching.OutputResolution

interface PhotoExporter {
    suspend fun saveToGallery(
        jpegData: ByteArray,
        outputResolution: OutputResolution,
        gpsCoordinates: GpsCoordinates?
    ): Result<String>
}
