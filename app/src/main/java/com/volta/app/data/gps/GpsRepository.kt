package com.volta.app.data.gps

import android.location.Location
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GpsRepository @Inject constructor() {
    // TODO: Emit current Location via Flow; emit null when unavailable
    fun locationUpdates(): Flow<Location?> {
        TODO("Implement GPS location flow")
    }
}
