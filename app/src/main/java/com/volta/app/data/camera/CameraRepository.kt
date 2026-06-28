package com.volta.app.data.camera

import com.volta.app.domain.model.FrameData
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
    fun cameraFrames(): Flow<FrameData>
    fun startPreview()
    fun stopPreview()
}
