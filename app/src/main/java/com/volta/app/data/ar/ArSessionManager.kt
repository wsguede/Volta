package com.volta.app.data.ar

import com.volta.app.domain.model.ArFrame
import com.volta.app.domain.model.DevicePose
import com.volta.app.domain.model.TrackingState
import kotlinx.coroutines.flow.Flow

interface ArSessionManager {
    val isAvailable: Flow<Boolean>
    val currentPose: Flow<DevicePose>
    val cameraFrames: Flow<ArFrame>
    val trackingState: Flow<TrackingState>
    fun resume()
    fun pause()
}
