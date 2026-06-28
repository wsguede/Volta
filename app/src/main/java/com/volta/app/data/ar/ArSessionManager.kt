package com.volta.app.data.ar

import com.volta.app.domain.model.DevicePose
import kotlinx.coroutines.flow.Flow

interface ArSessionManager {
    val isAvailable: Flow<Boolean>
    val currentPose: Flow<DevicePose>
    fun resume()
    fun pause()
}
