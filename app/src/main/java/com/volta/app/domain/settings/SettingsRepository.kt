package com.volta.app.domain.settings

import com.volta.app.domain.stitching.OutputResolution
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val outputResolution: Flow<OutputResolution>

    suspend fun setOutputResolution(resolution: OutputResolution)
}
