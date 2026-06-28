package com.volta.app.data.export

import com.volta.app.data.gps.GpsCoordinates

interface PhotoExporter {
    suspend fun saveToGallery(
        jpegData: ByteArray,
        gpsCoordinates: GpsCoordinates?,
    ): Result<String>
}
