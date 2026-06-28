package com.volta.app.data.gps

import kotlinx.coroutines.flow.Flow

data class GpsCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

interface LocationRepository {
    val location: Flow<GpsCoordinates?>
    fun startUpdates()
    fun stopUpdates()
}
