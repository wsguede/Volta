package com.volta.app.data.export

import com.volta.app.domain.model.GpsCoordinates

interface PhotoExporter {
    suspend fun saveToGallery(jpegData: ByteArray, gpsCoordinates: GpsCoordinates?): Result<String>
}
