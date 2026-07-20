package com.volta.app.data.gps

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.volta.app.domain.model.GpsCoordinates
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

class FusedGpsRepository @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationRequest: LocationRequest,
    private val permissionChecker: LocationPermissionChecker
) : GpsRepository {

    @SuppressLint("MissingPermission")
    override val location: Flow<GpsCoordinates?> = callbackFlow {
        if (!permissionChecker.hasFineLocationPermission()) {
            Timber.w("Fine location permission not granted; GPS coordinates unavailable")
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                trySend(result.lastLocation?.toGpsCoordinates())
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    private fun Location.toGpsCoordinates(): GpsCoordinates? = GpsCoordinates(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracyMeters = accuracy
    ).takeIf { it.isAccurateEnough() }
}
