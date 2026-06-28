package com.volta.app.data.ar

import com.volta.app.domain.coverage.SphereRegion
import kotlinx.coroutines.flow.Flow

interface ArSessionManager {
    val isAvailable: Flow<Boolean>
    val currentOrientation: Flow<SphereRegion>
    fun resume()
    fun pause()
}
