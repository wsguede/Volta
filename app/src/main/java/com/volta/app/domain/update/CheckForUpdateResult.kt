package com.volta.app.domain.update

sealed interface CheckForUpdateResult {
    data class UpdateAvailable(val manifest: UpdateManifest) : CheckForUpdateResult

    data object UpToDate : CheckForUpdateResult

    data class Skipped(val reason: SkipReason) : CheckForUpdateResult

    data class Failed(val cause: Throwable) : CheckForUpdateResult

    enum class SkipReason { DEBUG_BUILD, AUTO_CHECK_DISABLED, CHECKED_RECENTLY }
}
