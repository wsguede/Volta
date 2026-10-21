package com.volta.app.domain.settings

import com.volta.app.domain.stitching.OutputResolution
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val outputResolution: Flow<OutputResolution>

    /**
     * Whether to check for updates on launch. Defaults to `true`, including when the read fails.
     * Never throws except for cancellation.
     */
    val autoCheckUpdates: Flow<Boolean>

    /**
     * Wall-clock millis of the last successful update check, or null if there has been none or the
     * read fails. Never throws except for cancellation.
     */
    val lastUpdateCheckMillis: Flow<Long?>

    suspend fun setOutputResolution(resolution: OutputResolution)

    suspend fun setAutoCheckUpdates(enabled: Boolean)

    /** Best effort: logs and drops write failures. Never throws except for cancellation. */
    suspend fun setLastUpdateCheckMillis(millis: Long)
}
