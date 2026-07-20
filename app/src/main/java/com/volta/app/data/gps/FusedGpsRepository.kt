package com.volta.app.data.gps

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.volta.app.di.ApplicationScope
import com.volta.app.domain.model.GpsCoordinates
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber

class FusedGpsRepository @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationRequest: LocationRequest,
    private val permissionChecker: LocationPermissionChecker,
    @ApplicationScope applicationScope: CoroutineScope
) : GpsRepository {

    override val location: Flow<GpsCoordinates?> = flow {
        awaitFineLocationPermission()
        emitAll(acceptableFixes())
    }
        .scan(null as GpsCoordinates?) { lastAcceptableFix, newFix -> newFix ?: lastAcceptableFix }
        .distinctUntilChanged()
        .shareIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(SHARE_STOP_TIMEOUT_MS),
            replay = 1
        )

    private suspend fun FlowCollector<GpsCoordinates?>.awaitFineLocationPermission() {
        if (permissionChecker.hasFineLocationPermission()) return
        Timber.w("Fine location permission not granted; GPS coordinates unavailable until granted")
        while (!permissionChecker.hasFineLocationPermission()) {
            emit(null)
            delay(PERMISSION_RECHECK_INTERVAL_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun acceptableFixes(): Flow<GpsCoordinates?> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                trySend(result.lastLocation?.toGpsCoordinates())
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    trySend(null)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }.conflate()

    private fun Location.toGpsCoordinates(): GpsCoordinates? = GpsCoordinates(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracyMeters = accuracy
    ).takeIf { it.isAccurateEnough() }

    companion object {
        private const val PERMISSION_RECHECK_INTERVAL_MS = 1_000L
        private const val SHARE_STOP_TIMEOUT_MS = 5_000L
    }
}
