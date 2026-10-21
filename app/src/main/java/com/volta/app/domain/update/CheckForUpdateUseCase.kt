package com.volta.app.domain.update

import com.volta.app.domain.settings.AppVersionDataSource
import com.volta.app.domain.settings.SettingsRepository
import com.volta.app.domain.time.Clock
import com.volta.app.domain.update.CheckForUpdateResult.SkipReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Decides whether to check for an update and compares the latest release manifest with the
 * installed build (ADR 0018). Debug builds never update: they are signed with the debug key and
 * their date-derived version would compare as newer than older releases.
 */
class CheckForUpdateUseCase(
    private val updateRepository: UpdateRepository,
    private val settingsRepository: SettingsRepository,
    private val appVersionDataSource: AppVersionDataSource,
    private val deviceSdkDataSource: DeviceSdkDataSource,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(mode: UpdateCheckMode): CheckForUpdateResult =
        withContext(dispatcher) {
            skipReason(mode)?.let { return@withContext CheckForUpdateResult.Skipped(it) }

            updateRepository.fetchLatestManifest().fold(
                onSuccess = { manifest ->
                    settingsRepository.setLastUpdateCheckMillis(clock.nowMillis())
                    if (isInstallable(manifest)) {
                        CheckForUpdateResult.UpdateAvailable(manifest)
                    } else {
                        CheckForUpdateResult.UpToDate
                    }
                },
                onFailure = { CheckForUpdateResult.Failed(it) }
            )
        }

    private suspend fun skipReason(mode: UpdateCheckMode): SkipReason? = when {
        appVersionDataSource.isDebugBuild -> SkipReason.DEBUG_BUILD
        mode == UpdateCheckMode.MANUAL -> null
        !settingsRepository.autoCheckUpdates.first() -> SkipReason.AUTO_CHECK_DISABLED
        checkedRecently() -> SkipReason.CHECKED_RECENTLY
        else -> null
    }

    private suspend fun checkedRecently(): Boolean {
        val lastCheck = settingsRepository.lastUpdateCheckMillis.first() ?: return false
        val elapsed = clock.nowMillis() - lastCheck
        // A negative value means the wall clock moved backwards; check rather than wait it out.
        return elapsed in 0 until CHECK_INTERVAL_MILLIS
    }

    private fun isInstallable(manifest: UpdateManifest): Boolean =
        manifest.versionCode > appVersionDataSource.versionCode &&
            deviceSdkDataSource.sdkInt >= manifest.minSdk

    private companion object {
        const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
    }
}
