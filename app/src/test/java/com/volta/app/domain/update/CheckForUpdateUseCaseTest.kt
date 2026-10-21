package com.volta.app.domain.update

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.settings.AppVersionDataSource
import com.volta.app.domain.settings.SettingsRepository
import com.volta.app.domain.update.CheckForUpdateResult.SkipReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CheckForUpdateUseCaseTest {

    private val updateRepository = mockk<UpdateRepository>()
    private val settingsRepository = mockk<SettingsRepository>(relaxUnitFun = true)

    private fun TestScope.useCase(fixture: TestFixture) = CheckForUpdateUseCase(
        updateRepository = updateRepository,
        settingsRepository = settingsRepository,
        appVersionDataSource = object : AppVersionDataSource {
            override val versionName = "2026.10.1"
            override val versionCode = fixture.installedVersionCode
            override val isDebugBuild = fixture.debugBuild
        },
        deviceSdkDataSource = object : DeviceSdkDataSource {
            override val sdkInt = fixture.deviceSdk
        },
        clock = { fixture.now },
        dispatcher = fixture.dispatcher ?: StandardTestDispatcher(testScheduler)
    ).also {
        every { settingsRepository.autoCheckUpdates } returns flowOf(fixture.autoCheck)
        every { settingsRepository.lastUpdateCheckMillis } returns flowOf(fixture.lastCheck)
        coEvery { updateRepository.fetchLatestManifest() } returns fixture.fetchResult
    }

    private class TestFixture(
        val installedVersionCode: Int = INSTALLED_VERSION_CODE,
        val debugBuild: Boolean = false,
        val deviceSdk: Int = 34,
        val autoCheck: Boolean = true,
        val lastCheck: Long? = null,
        val now: Long = NOW,
        val fetchResult: Result<UpdateManifest> = Result.success(manifest()),
        /** Null runs the check on a dispatcher backed by the test's own scheduler. */
        val dispatcher: CoroutineDispatcher? = null
    )

    private suspend fun TestScope.check(
        fixture: TestFixture = TestFixture(),
        mode: UpdateCheckMode = UpdateCheckMode.LAUNCH
    ): CheckForUpdateResult = useCase(fixture)(mode)

    // --- Debug guard ---

    @Test
    fun `launch check is skipped in debug builds without fetching`() = runTest {
        val result = check(TestFixture(debugBuild = true))

        assertThat(result).isEqualTo(CheckForUpdateResult.Skipped(SkipReason.DEBUG_BUILD))
        coVerify(exactly = 0) { updateRepository.fetchLatestManifest() }
    }

    @Test
    fun `manual check is also skipped in debug builds`() = runTest {
        val result = check(TestFixture(debugBuild = true), UpdateCheckMode.MANUAL)

        assertThat(result).isEqualTo(CheckForUpdateResult.Skipped(SkipReason.DEBUG_BUILD))
        coVerify(exactly = 0) { updateRepository.fetchLatestManifest() }
    }

    // --- Auto-check toggle ---

    @Test
    fun `launch check is skipped when auto-check is off`() = runTest {
        val result = check(TestFixture(autoCheck = false))

        assertThat(result).isEqualTo(CheckForUpdateResult.Skipped(SkipReason.AUTO_CHECK_DISABLED))
        coVerify(exactly = 0) { updateRepository.fetchLatestManifest() }
    }

    @Test
    fun `manual check ignores the auto-check toggle`() = runTest {
        val result = check(TestFixture(autoCheck = false), UpdateCheckMode.MANUAL)

        assertThat(result).isInstanceOf(CheckForUpdateResult.UpdateAvailable::class.java)
    }

    // --- 24h throttle ---

    @Test
    fun `launch check is skipped when the last check was under 24h ago`() = runTest {
        val result = check(TestFixture(lastCheck = NOW - DAY_MILLIS + 1))

        assertThat(result).isEqualTo(CheckForUpdateResult.Skipped(SkipReason.CHECKED_RECENTLY))
        coVerify(exactly = 0) { updateRepository.fetchLatestManifest() }
    }

    @Test
    fun `launch check is skipped when the last check was just now`() = runTest {
        val result = check(TestFixture(lastCheck = NOW))

        assertThat(result).isEqualTo(CheckForUpdateResult.Skipped(SkipReason.CHECKED_RECENTLY))
    }

    @Test
    fun `launch check runs when exactly 24h have passed`() = runTest {
        val result = check(TestFixture(lastCheck = NOW - DAY_MILLIS))

        assertThat(result).isInstanceOf(CheckForUpdateResult.UpdateAvailable::class.java)
    }

    @Test
    fun `launch check runs when there has never been a check`() = runTest {
        val result = check(TestFixture(lastCheck = null))

        assertThat(result).isInstanceOf(CheckForUpdateResult.UpdateAvailable::class.java)
    }

    @Test
    fun `launch check runs when the last check is in the future`() = runTest {
        // The wall clock moved backwards; without this the throttle could block checks for days.
        val result = check(TestFixture(lastCheck = NOW + DAY_MILLIS))

        assertThat(result).isInstanceOf(CheckForUpdateResult.UpdateAvailable::class.java)
    }

    @Test
    fun `manual check ignores the 24h throttle`() = runTest {
        val result = check(TestFixture(lastCheck = NOW - 1), UpdateCheckMode.MANUAL)

        assertThat(result).isInstanceOf(CheckForUpdateResult.UpdateAvailable::class.java)
    }

    // --- Version and SDK comparison ---

    @Test
    fun `reports the manifest when its versionCode is newer`() = runTest {
        val newer = manifest(versionCode = INSTALLED_VERSION_CODE + 1)

        val result = check(TestFixture(fetchResult = Result.success(newer)))

        assertThat(result).isEqualTo(CheckForUpdateResult.UpdateAvailable(newer))
    }

    @Test
    fun `is up to date when the manifest versionCode equals the installed one`() = runTest {
        val same = manifest(versionCode = INSTALLED_VERSION_CODE)

        val result = check(TestFixture(fetchResult = Result.success(same)))

        assertThat(result).isEqualTo(CheckForUpdateResult.UpToDate)
    }

    @Test
    fun `is up to date when the manifest versionCode is older`() = runTest {
        val older = manifest(versionCode = INSTALLED_VERSION_CODE - 1)

        val result = check(TestFixture(fetchResult = Result.success(older)))

        assertThat(result).isEqualTo(CheckForUpdateResult.UpToDate)
    }

    @Test
    fun `is up to date when the device is below the manifest minSdk`() = runTest {
        val needsNewerAndroid = manifest(minSdk = 35)

        val result =
            check(TestFixture(deviceSdk = 34, fetchResult = Result.success(needsNewerAndroid)))

        assertThat(result).isEqualTo(CheckForUpdateResult.UpToDate)
    }

    @Test
    fun `reports the update when the device is exactly at the manifest minSdk`() = runTest {
        val atMinSdk = manifest(minSdk = 34)

        val result = check(TestFixture(deviceSdk = 34, fetchResult = Result.success(atMinSdk)))

        assertThat(result).isEqualTo(CheckForUpdateResult.UpdateAvailable(atMinSdk))
    }

    // --- Fetch failures and the last-check timestamp ---

    @Test
    fun `returns Failed when the fetch fails`() = runTest {
        val error = IOException("offline")

        val result = check(TestFixture(fetchResult = Result.failure(error)))

        assertThat(result).isEqualTo(CheckForUpdateResult.Failed(error))
    }

    @Test
    fun `propagates cancellation from the fetch instead of reporting Failed`() = runTest {
        val cancellation = CancellationException("check cancelled")

        val thrown = runCatching {
            check(TestFixture(fetchResult = Result.failure(cancellation)))
        }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `still reports the update when storing the check time fails`() = runTest {
        val newer = manifest()
        val useCase = useCase(TestFixture(fetchResult = Result.success(newer)))
        coEvery { settingsRepository.setLastUpdateCheckMillis(any()) } throws
            IllegalStateException("store failed")

        val result = useCase(UpdateCheckMode.LAUNCH)

        assertThat(result).isEqualTo(CheckForUpdateResult.UpdateAvailable(newer))
    }

    @Test
    fun `propagates cancellation while storing the check time`() = runTest {
        val useCase = useCase(TestFixture())
        coEvery { settingsRepository.setLastUpdateCheckMillis(any()) } throws
            CancellationException("check cancelled")

        val thrown = runCatching { useCase(UpdateCheckMode.LAUNCH) }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun `stores the check time after a successful fetch`() = runTest {
        check(TestFixture(fetchResult = Result.success(manifest(versionCode = 1))))

        coVerify(exactly = 1) { settingsRepository.setLastUpdateCheckMillis(NOW) }
    }

    @Test
    fun `stores the check time after a successful manual fetch`() = runTest {
        check(TestFixture(), UpdateCheckMode.MANUAL)

        coVerify(exactly = 1) { settingsRepository.setLastUpdateCheckMillis(NOW) }
    }

    @Test
    fun `does not store the check time when the fetch fails`() = runTest {
        check(TestFixture(fetchResult = Result.failure(IOException("offline"))))

        coVerify(exactly = 0) { settingsRepository.setLastUpdateCheckMillis(any()) }
    }

    @Test
    fun `does not store the check time when the check is skipped`() = runTest {
        check(TestFixture(autoCheck = false))

        coVerify(exactly = 0) { settingsRepository.setLastUpdateCheckMillis(any()) }
    }

    // --- Dispatcher ---

    @Test
    fun `fetches on the injected dispatcher`() = runTest {
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val useCase = useCase(TestFixture(dispatcher = ioDispatcher))
        var fetchDispatcher: ContinuationInterceptor? = null
        coEvery { updateRepository.fetchLatestManifest() } coAnswers {
            fetchDispatcher = currentCoroutineContext()[ContinuationInterceptor]
            Result.success(manifest())
        }

        useCase(UpdateCheckMode.LAUNCH)

        assertThat(fetchDispatcher).isSameInstanceAs(ioDispatcher)
    }

    private companion object {
        const val INSTALLED_VERSION_CODE = 202610001
        const val NOW = 1_800_000_000_000L
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        fun manifest(versionCode: Int = INSTALLED_VERSION_CODE + 1, minSdk: Int = 30) =
            UpdateManifest(
                schemaVersion = 1,
                versionCode = versionCode,
                versionName = "2026.10.2",
                minSdk = minSdk,
                sha256 = "ab".repeat(32),
                apkUrl = "https://example.com/app-release.apk",
                notes = "notes"
            )
    }
}
