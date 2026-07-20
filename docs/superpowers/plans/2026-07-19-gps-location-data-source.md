# GPS Location Data Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement GitHub issue #8 — acquire device GPS coordinates via Play Services Location and expose them as `Flow<GpsCoordinates?>` to the rest of the app.

**Architecture:** A `GpsRepository` interface in `data/gps/` exposes a cold `Flow<GpsCoordinates?>`. The `FusedGpsRepository` implementation wraps `FusedLocationProviderClient` in a `callbackFlow` that registers for location updates on collection and unregisters on cancellation. Permission checking is abstracted behind a `LocationPermissionChecker` fun interface so the repository is unit-testable without Android framework statics. The domain model `GpsCoordinates` (no Android imports) owns the accuracy-threshold rule.

**Tech Stack:** Kotlin, Play Services Location (`FusedLocationProviderClient`, already in version catalog), Hilt, Kotlin Coroutines `callbackFlow`, Timber. Tests: JUnit4, MockK, Truth, Turbine, kotlinx-coroutines-test (all already wired in `app/build.gradle.kts`).

## Global Constraints

- `GpsCoordinates` lives in `domain/model/` with fields (latitude, longitude, altitude, accuracyMeters) — **no Android imports** in that class (AGENTS.md: domain layer is Kotlin stdlib + kotlinx-coroutines only)
- The flow emits `null` when permission is denied or GPS is unavailable — the app must never block on GPS
- Emits a non-null value only when horizontal accuracy is ≤ 50 m — threshold must be a **named constant**, not a magic number
- `GpsRepository` interface lives in `data/gps/` and wraps `FusedLocationProviderClient`
- Logging via Timber only — never `android.util.Log`; no `TAG` constants
- No new dependencies — everything needed is already in `gradle/libs.versions.toml`
- All quality gates must pass before each commit: `./gradlew ktlintCheck detekt testDebugUnitTest` (run `./gradlew lint` at least once before final commit)
- Conventional commit format with valid scope, e.g. `feat(gps): …`, `test(gps): …`
- Always use the Gradle wrapper `./gradlew`, never bare `gradle`
- Test style: backtick function names, Truth assertions (match `BlurDetectorTest.kt`)

**Pre-existing scaffold notes (context for all tasks):**
- `app/src/main/java/com/volta/app/data/gps/LocationRepository.kt` is an unused scaffold interface — issue #8's acceptance criteria names the interface `GpsRepository`, so the scaffold file is **replaced** (deleted) in Task 2. Nothing else references `LocationRepository` (verified by grep).
- `app/src/main/java/com/volta/app/di/LocationModule.kt` is an empty Hilt module — filled in by Task 3.
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are already declared in `AndroidManifest.xml`. The runtime permission *request* UI is a separate issue (#18, already merged) — this feature only *checks* the permission.
- `play-services-location` is already a dependency (`app/build.gradle.kts:107`).

---

### Task 1: Extend `GpsCoordinates` domain model with altitude and accuracy rule

**Files:**
- Modify: `app/src/main/java/com/volta/app/domain/model/GpsCoordinates.kt`
- Create: `app/src/test/java/com/volta/app/domain/model/GpsCoordinatesTest.kt`

**Interfaces:**
- Consumes: nothing (pure domain model).
- Produces: `data class GpsCoordinates(latitude: Double, longitude: Double, altitude: Double?, accuracyMeters: Float)` with `fun isAccurateEnough(): Boolean` and `companion object { const val MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS = 50f }`. Task 2's repository calls `isAccurateEnough()` and constructs this exact shape. (Note: `data/export/PhotoExporter.kt` references the type but only as a parameter type — no construction sites break.)

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/volta/app/domain/model/GpsCoordinatesTest.kt`:

```kotlin
package com.volta.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GpsCoordinatesTest {

    @Test
    fun `holds altitude when provided`() {
        val coordinates = GpsCoordinates(
            latitude = 47.6062,
            longitude = -122.3321,
            altitude = 56.4,
            accuracyMeters = 10f
        )

        assertThat(coordinates.altitude).isEqualTo(56.4)
    }

    @Test
    fun `altitude may be absent`() {
        val coordinates = GpsCoordinates(
            latitude = 47.6062,
            longitude = -122.3321,
            altitude = null,
            accuracyMeters = 10f
        )

        assertThat(coordinates.altitude).isNull()
    }

    @Test
    fun `is accurate enough at exactly the threshold`() {
        val coordinates = GpsCoordinates(
            latitude = 0.0,
            longitude = 0.0,
            altitude = null,
            accuracyMeters = GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS
        )

        assertThat(coordinates.isAccurateEnough()).isTrue()
    }

    @Test
    fun `is not accurate enough above the threshold`() {
        val coordinates = GpsCoordinates(
            latitude = 0.0,
            longitude = 0.0,
            altitude = null,
            accuracyMeters = GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS + 0.1f
        )

        assertThat(coordinates.isAccurateEnough()).isFalse()
    }

    @Test
    fun `threshold constant is fifty meters`() {
        assertThat(GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS).isEqualTo(50f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.volta.app.domain.model.GpsCoordinatesTest"`
Expected: FAIL — compilation error (`altitude` parameter and `isAccurateEnough` do not exist yet).

- [ ] **Step 3: Write minimal implementation**

Replace the contents of `app/src/main/java/com/volta/app/domain/model/GpsCoordinates.kt` with:

```kotlin
package com.volta.app.domain.model

data class GpsCoordinates(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float
) {
    fun isAccurateEnough(): Boolean = accuracyMeters <= MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS

    companion object {
        const val MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS = 50f
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.volta.app.domain.model.GpsCoordinatesTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Run quality gates**

Run: `./gradlew ktlintCheck detekt testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no violations, all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/volta/app/domain/model/GpsCoordinates.kt app/src/test/java/com/volta/app/domain/model/GpsCoordinatesTest.kt
git commit -m "feat(gps): add altitude and accuracy threshold to GpsCoordinates"
```

---

### Task 2: `GpsRepository` interface and `FusedGpsRepository` implementation

**Files:**
- Delete: `app/src/main/java/com/volta/app/data/gps/LocationRepository.kt` (unused scaffold; issue #8 names the interface `GpsRepository`)
- Create: `app/src/main/java/com/volta/app/data/gps/GpsRepository.kt`
- Create: `app/src/main/java/com/volta/app/data/gps/LocationPermissionChecker.kt`
- Create: `app/src/main/java/com/volta/app/data/gps/FusedGpsRepository.kt`
- Test: `app/src/test/java/com/volta/app/data/gps/FusedGpsRepositoryTest.kt`

**Interfaces:**
- Consumes (from Task 1): `GpsCoordinates(latitude: Double, longitude: Double, altitude: Double?, accuracyMeters: Float)` with `isAccurateEnough(): Boolean`.
- Produces (Task 3 binds these in Hilt):
  - `interface GpsRepository { val location: Flow<GpsCoordinates?> }`
  - `fun interface LocationPermissionChecker { fun hasFineLocationPermission(): Boolean }`
  - `class FusedGpsRepository @Inject constructor(fusedLocationClient: FusedLocationProviderClient, locationRequest: LocationRequest, permissionChecker: LocationPermissionChecker) : GpsRepository`

**Design notes for the implementer:**
- The scaffold interface had `startUpdates()`/`stopUpdates()` — do **not** carry these over. The cold `callbackFlow` registers updates when collected and unregisters in `awaitClose` (YAGNI: lifecycle comes free from Flow collection).
- `LocationRequest` is injected (not built inside the repository) so unit tests never touch the GMS builder; Task 3 provides the real one.
- Fixes with horizontal accuracy > 50 m map to `null` emissions ("GPS unavailable" semantics), as do null `lastLocation` results.
- When permission is denied, emit a single `null` and keep the flow open with an empty `awaitClose { }` (no updates will ever arrive; collectors are not forced to handle completion).

- [ ] **Step 1: Delete the scaffold interface and write the new interfaces**

```bash
git rm app/src/main/java/com/volta/app/data/gps/LocationRepository.kt
```

Create `app/src/main/java/com/volta/app/data/gps/GpsRepository.kt`:

```kotlin
package com.volta.app.data.gps

import com.volta.app.domain.model.GpsCoordinates
import kotlinx.coroutines.flow.Flow

interface GpsRepository {
    val location: Flow<GpsCoordinates?>
}
```

Create `app/src/main/java/com/volta/app/data/gps/LocationPermissionChecker.kt`:

```kotlin
package com.volta.app.data.gps

fun interface LocationPermissionChecker {
    fun hasFineLocationPermission(): Boolean
}
```

- [ ] **Step 2: Write the failing tests**

Create `app/src/test/java/com/volta/app/data/gps/FusedGpsRepositoryTest.kt`:

```kotlin
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
            fusedClient.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any())
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.volta.app.data.gps.FusedGpsRepositoryTest"`
Expected: FAIL — compilation error (`FusedGpsRepository` does not exist yet).

- [ ] **Step 4: Write the implementation**

Create `app/src/main/java/com/volta/app/data/gps/FusedGpsRepository.kt`:

```kotlin
package com.volta.app.data.gps

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.volta.app.domain.model.GpsCoordinates
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject

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

        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

        awaitClose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    private fun Location.toGpsCoordinates(): GpsCoordinates? =
        GpsCoordinates(
            latitude = latitude,
            longitude = longitude,
            altitude = if (hasAltitude()) altitude else null,
            accuracyMeters = accuracy
        ).takeIf { it.isAccurateEnough() }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.volta.app.data.gps.FusedGpsRepositoryTest"`
Expected: PASS (7 tests).

- [ ] **Step 6: Run quality gates**

Run: `./gradlew ktlintCheck detekt testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no violations, all unit tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A app/src/main/java/com/volta/app/data/gps/ app/src/test/java/com/volta/app/data/gps/
git commit -m "feat(gps): implement FusedGpsRepository with accuracy-filtered location flow"
```

---

### Task 3: Hilt wiring in `LocationModule`

**Files:**
- Modify: `app/src/main/java/com/volta/app/di/LocationModule.kt` (currently an empty `@Module`)

**Interfaces:**
- Consumes (from Task 2): `GpsRepository`, `FusedGpsRepository`, `LocationPermissionChecker` — all in `com.volta.app.data.gps`.
- Produces: Hilt bindings so any `@Inject constructor(gpsRepository: GpsRepository)` resolves. Future issue #9 (export) will inject `GpsRepository`.

**Design notes for the implementer:**
- Hilt module wiring has no unit test — verification is compilation (`assembleDebug` runs KSP/Hilt validation, which fails on missing or duplicate bindings) plus the full quality gate.
- The 2-second update interval is a named constant (`UPDATE_INTERVAL_MS`) — Detekt's MagicNumber rule and the issue's "no magic numbers" note both apply.

- [ ] **Step 1: Fill in the module**

Replace the contents of `app/src/main/java/com/volta/app/di/LocationModule.kt` with:

```kotlin
package com.volta.app.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.volta.app.data.gps.FusedGpsRepository
import com.volta.app.data.gps.GpsRepository
import com.volta.app.data.gps.LocationPermissionChecker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindGpsRepository(impl: FusedGpsRepository): GpsRepository

    companion object {
        private const val UPDATE_INTERVAL_MS = 2_000L

        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(
            @ApplicationContext context: Context
        ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        @Provides
        fun provideLocationRequest(): LocationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS).build()

        @Provides
        fun provideLocationPermissionChecker(
            @ApplicationContext context: Context
        ): LocationPermissionChecker = LocationPermissionChecker {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
```

- [ ] **Step 2: Verify the Hilt graph compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (KSP/Hilt validates the dependency graph at compile time).

- [ ] **Step 3: Run all quality gates including Android lint**

Run: `./gradlew ktlintCheck detekt lint testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no violations, all unit tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/volta/app/di/LocationModule.kt
git commit -m "feat(gps): wire GpsRepository into Hilt location module"
```

---

## Spec Coverage Map (issue #8 acceptance criteria → tasks)

| Acceptance criterion | Task |
|---|---|
| Uses `FusedLocationProviderClient` | Task 2 (implementation), Task 3 (provider) |
| Exposes `Flow<GpsCoordinates?>`, null on denied/unavailable | Task 2 |
| `GpsCoordinates` (lat, lon, altitude, accuracyMeters) in `domain/model/`, no Android imports | Task 1 |
| Non-null only when accuracy ≤ 50 m, named constant | Task 1 (constant + rule), Task 2 (applied) |
| `GpsRepository` in `data/gps/` wraps the fused client | Task 2, Task 3 |
| `ACCESS_FINE_LOCATION` requested | Already in manifest; runtime UI is issue #18 (merged) |
| Never block on GPS — null means export proceeds without EXIF | Task 2 (null semantics; consumption is issue #9) |
