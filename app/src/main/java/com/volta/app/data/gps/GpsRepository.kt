package com.volta.app.data.gps

import com.volta.app.domain.model.GpsCoordinates
import kotlinx.coroutines.flow.Flow

interface GpsRepository {
    val location: Flow<GpsCoordinates?>
}
