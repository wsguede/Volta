package com.volta.app.data.gps

import android.location.Location
import android.os.Looper
import app.cash.turbine.test
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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

    private fun TestScope.repository(permissionChecker: LocationPermissionChecker) =
        FusedGpsRepository(
            fusedLocationClient = fusedClient,
            locationRequest = locationRequest,
            permissionChecker = permissionChecker,
            applicationScope = backgroundScope
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

    private fun availability(available: Boolean): LocationAvailability {
        val availability = mockk<LocationAvailability>()
        every { availability.isLocationAvailable } returns available
        return availability
    }

    @Test
    fun `emits null immediately when permission is denied`() = runTest {
        repository { false }.location.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not register for updates while permission is denied`() = runTest {
        repository { false }.location.test {
            awaitItem()
            advanceTimeBy(3_000)
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
    fun `recovers and emits fixes after permission is granted mid-collection`() = runTest {
        var granted = false
        repository { granted }.location.test {
            assertThat(awaitItem()).isNull()

            granted = true
            advanceTimeBy(1_001)
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(horizontalAccuracy = 10f))
            )

            assertThat(awaitItem()).isNotNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits null immediately then mapped coordinates for an accurate fix with altitude`() =
        runTest {
            repository { true }.location.test {
                assertThat(awaitItem()).isNull()
                runCurrent()
                callbackSlot.captured.onLocationResult(
                    locationResult(mockLocation(horizontalAccuracy = 10f, altitudeMeters = 56.4))
                )

                assertThat(awaitItem()).isEqualTo(
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
        repository { true }.location.test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(altitudeMeters = null))
            )

            val coordinates = awaitItem()
            assertThat(coordinates).isNotNull()
            assertThat(coordinates?.altitude).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retains last acceptable fix when a degraded fix arrives`() = runTest {
        repository { true }.location.test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(horizontalAccuracy = 10f))
            )
            val goodFix = awaitItem()
            assertThat(goodFix).isNotNull()

            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(lat = 1.0, lon = 2.0, horizontalAccuracy = 51f))
            )

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retains last acceptable fix when location availability is lost`() = runTest {
        repository { true }.location.test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(horizontalAccuracy = 10f))
            )
            assertThat(awaitItem()).isNotNull()

            callbackSlot.captured.onLocationAvailability(availability(available = false))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stays null when availability is lost before any fix`() = runTest {
        repository { true }.location.test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            callbackSlot.captured.onLocationAvailability(availability(available = false))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stays null when the result has no location`() = runTest {
        repository { true }.location.test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            callbackSlot.captured.onLocationResult(locationResult(null))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `late subscriber immediately receives the latest fix without a new registration`() =
        runTest {
            val repo = repository { true }
            repo.location.test {
                assertThat(awaitItem()).isNull()
                runCurrent()
                callbackSlot.captured.onLocationResult(
                    locationResult(mockLocation(horizontalAccuracy = 10f))
                )
                assertThat(awaitItem()).isNotNull()
                cancelAndIgnoreRemainingEvents()
            }

            repo.location.test {
                assertThat(awaitItem()).isEqualTo(
                    GpsCoordinates(
                        latitude = 47.6062,
                        longitude = -122.3321,
                        altitude = null,
                        accuracyMeters = 10f
                    )
                )
                cancelAndIgnoreRemainingEvents()
            }
            verify(exactly = 1) {
                fusedClient.requestLocationUpdates(
                    any<LocationRequest>(),
                    any<LocationCallback>(),
                    any()
                )
            }
        }

    @Test
    fun `stops updates and re-registers when permission is revoked then re-granted`() = runTest {
        var granted = true
        repository { granted }.location.test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(horizontalAccuracy = 10f))
            )
            val goodFix = awaitItem()
            assertThat(goodFix).isNotNull()

            granted = false
            advanceTimeBy(1_001)
            runCurrent()

            verify { fusedClient.removeLocationUpdates(any<LocationCallback>()) }
            expectNoEvents()

            granted = true
            advanceTimeBy(1_001)
            runCurrent()

            callbackSlot.captured.onLocationResult(
                locationResult(mockLocation(lat = 1.0, lon = 2.0, horizontalAccuracy = 10f))
            )
            val newFix = awaitItem()
            assertThat(newFix).isNotEqualTo(goodFix)

            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 2) {
            fusedClient.requestLocationUpdates(
                any<LocationRequest>(),
                any<LocationCallback>(),
                any()
            )
        }
    }

    @Test
    fun `emits null and does not crash when requestLocationUpdates throws SecurityException`() =
        runTest {
            every {
                fusedClient.requestLocationUpdates(
                    any<LocationRequest>(),
                    any<LocationCallback>(),
                    any()
                )
            } throws SecurityException("permission revoked before registration")

            repository { true }.location.test {
                assertThat(awaitItem()).isNull()
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `unregisters the callback after subscribers leave and the stop timeout elapses`() =
        runTest {
            repository { true }.location.test {
                awaitItem()
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }

            advanceTimeBy(5_001)
            runCurrent()

            verify { fusedClient.removeLocationUpdates(callbackSlot.captured) }
        }
}
