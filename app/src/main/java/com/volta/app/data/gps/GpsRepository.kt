package com.volta.app.data.gps

import com.volta.app.domain.model.GpsCoordinates
import kotlinx.coroutines.flow.Flow

interface GpsRepository {
    /**
     * Emits `null` when permission is denied, no fix is available, location services are
     * unavailable, or the fix's horizontal accuracy is worse than
     * [GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS]. Consumers should proceed
     * without location rather than block on this flow.
     */
    val location: Flow<GpsCoordinates?>
}
