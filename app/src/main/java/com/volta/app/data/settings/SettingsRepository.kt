package com.volta.app.data.settings

import com.volta.app.domain.stitching.OutputResolution
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val outputResolution: Flow<OutputResolution>

    suspend fun setOutputResolution(resolution: OutputResolution)
}
