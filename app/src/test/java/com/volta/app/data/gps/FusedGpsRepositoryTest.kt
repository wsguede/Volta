package com.volta.app.data.gps

import android.location.Location
import android.os.Looper
import app.cash.turbine.test
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.model.GpsCoordinates
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class FusedGpsRepositoryTest {

    private val fusedClient = mockk<FusedLocationProviderClient>()
    private val locationRequest = mockk<LocationRequest>()
    private val callbackSlot = slot<LocationCallback>()

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk()
        every {
            fusedClient.requestLocationUpdates(any<LocationRequest>(), capture(callbackSlot), any())
        } returns mockk()
        every { fusedClient.removeLocationUpdates(any<LocationCallback>()) } returns mockk()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
    }

    private fun repository(permissionGranted: Boolean) = FusedGpsRepository(
        fusedLocationClient = fusedClient,
        locationRequest = locationRequest,
        permissionChecker = { permissionGranted }
    )

    private fun mockLocation(
        lat: Double = 47.6062,
        lon: Double = -122.3321,
        horizontalAccuracy: Float = 10f,
        altitudeMeters: Double? = null
    ): Location {
        val location = mockk<Location>()
        every { location.latitude } returns lat
        every { location.longitude } returns lon
        every { location.accuracy } returns horizontalAccuracy
        every { location.hasAltitude() } returns (altitudeMeters != null)
        every { location.altitude } returns (altitudeMeters ?: 0.0)
        return location
    }

    private fun locationResult(location: Location?): LocationResult {
        val result = mockk<LocationResult>()
        every { result.lastLocation } returns location
        return result
    }

    @Test
    fun `emits null when permission is denied`() = runTest {
        repository(permissionGranted = false).location.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not register for updates when permission is denied`() = runTest {
        repository(permissionGranted = false).location.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 0) {
            fusedClient.requestLocationUpdates(
                any<LocationRequest>(),
                any<LocationCallback>(),
                any()
            )
        }
    }

    @Test
    fun `emits mapped coordinates for an accurate fix with altitude`() = runTest {
        repository(permissionGranted = true).location.test {
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(horizontalAccuracy = 10f, altitudeMeters = 56.4))
            )

            val coordinates = awaitItem()

            assertThat(coordinates).isEqualTo(
                GpsCoordinates(
                    latitude = 47.6062,
                    longitude = -122.3321,
                    altitude = 56.4,
                    accuracyMeters = 10f
                )
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits coordinates with null altitude when fix has no altitude`() = runTest {
        repository(permissionGranted = true).location.test {
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(altitudeMeters = null))
            )

            assertThat(awaitItem()?.altitude).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits null for a fix with accuracy worse than the threshold`() = runTest {
        repository(permissionGranted = true).location.test {
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(horizontalAccuracy = 51f))
            )

            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits null when the result has no location`() = runTest {
        repository(permissionGranted = true).location.test {
            runCurrent()
            callbackSlot.captured.onLocationResult(locationResult(null))

            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unregisters the callback when collection stops`() = runTest {
        repository(permissionGranted = true).location.test {
            runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        verify { fusedClient.removeLocationUpdates(callbackSlot.captured) }
    }
}
