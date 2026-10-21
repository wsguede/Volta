package com.volta.app.domain.update

/** Launch checks follow the auto-check toggle and 24h throttle; manual checks ignore both. */
enum class UpdateCheckMode { LAUNCH, MANUAL }

sealed interface CheckForUpdateResult {
    data class UpdateAvailable(val manifest: UpdateManifest) : CheckForUpdateResult

    data object UpToDate : CheckForUpdateResult

    data class Skipped(val reason: SkipReason) : CheckForUpdateResult

    data class Failed(val cause: Throwable) : CheckForUpdateResult

    enum class SkipReason { DEBUG_BUILD, AUTO_CHECK_DISABLED, CHECKED_RECENTLY }
}
