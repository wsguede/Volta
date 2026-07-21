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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class FusedGpsRepository @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    private val locationRequest: LocationRequest,
    private val permissionChecker: LocationPermissionChecker,
    @ApplicationScope applicationScope: CoroutineScope
) : GpsRepository {

    override val location: Flow<GpsCoordinates?> = flow {
        while (true) {
            awaitFineLocationPermission()
            emitAll(acceptableFixesWhilePermitted())
        }
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
    private fun acceptableFixesWhilePermitted(): Flow<GpsCoordinates?> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                trySend(result.lastLocation?.toGpsCoordinates())
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                // A true (recovered) signal carries no fix of its own — recovery is reported
                // separately via onLocationResult, so this branch is a deliberate no-op.
                if (!availability.isLocationAvailable) {
                    trySend(null)
                }
            }
        }

        // requestLocationUpdates performs its own synchronous permission check and throws
        // SecurityException if permission isn't actually held at call time (a TOCTOU race with
        // awaitFineLocationPermission's check). Treat that exactly like a revoke: emit null, back
        // off, and let the outer loop re-enter permission recovery instead of crashing.
        val registered = try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            true
        } catch (permissionRevoked: SecurityException) {
            Timber.w(permissionRevoked, "Location permission revoked before registration")
            trySend(null)
            delay(PERMISSION_RECHECK_INTERVAL_MS)
            close()
            false
        }

        val permissionWatcher = if (registered) {
            launch {
                while (isActive) {
                    delay(PERMISSION_RECHECK_INTERVAL_MS)
                    if (!permissionChecker.hasFineLocationPermission()) {
                        Timber.w("Fine location permission revoked mid-collection")
                        close()
                        break
                    }
                }
            }
        } else {
            null
        }

        awaitClose {
            permissionWatcher?.cancel()
            if (registered) {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }
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
