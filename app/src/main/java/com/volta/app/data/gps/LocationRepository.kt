package com.volta.app.data.gps

import com.volta.app.domain.model.GpsCoordinates
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    val location: Flow<GpsCoordinates?>
    fun startUpdates()
    fun stopUpdates()
}
