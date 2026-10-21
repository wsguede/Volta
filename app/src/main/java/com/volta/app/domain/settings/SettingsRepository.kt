package com.volta.app.domain.settings

import com.volta.app.domain.stitching.OutputResolution
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val outputResolution: Flow<OutputResolution>

    /** Whether to check for updates on launch. Defaults to `true`. */
    val autoCheckUpdates: Flow<Boolean>

    /** Wall-clock millis of the last successful update check, or null if there has been none. */
    val lastUpdateCheckMillis: Flow<Long?>

    suspend fun setOutputResolution(resolution: OutputResolution)

    suspend fun setAutoCheckUpdates(enabled: Boolean)

    suspend fun setLastUpdateCheckMillis(millis: Long)
}
