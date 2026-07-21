package com.volta.app.data.gps

import com.volta.app.domain.model.GpsCoordinates
import kotlinx.coroutines.flow.Flow

interface GpsRepository {
    /**
     * Hot, shared stream of the best available GPS fix (replay 1, one upstream GPS
     * subscription regardless of collector count).
     *
     * Emits `null` immediately on subscription, then the latest fix whose horizontal accuracy
     * is at most [GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS]. A degraded or lost
     * signal never erases the last acceptable fix while the stream stays active. While location
     * permission is denied the stream stays at `null` and recovers automatically once permission
     * is granted — no re-collection needed. A permission revoked mid-collection (via system
     * settings) is detected within about a second and handled the same way, without erasing the
     * last acceptable fix. Consumers embed the latest value at export time and proceed without
     * location rather than block waiting for a fix.
     */
    val location: Flow<GpsCoordinates?>
}
