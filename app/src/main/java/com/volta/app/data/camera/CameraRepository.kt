package com.volta.app.data.camera

import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
    fun cameraFrames(): Flow<ImageProxy>
    fun startPreview()
    fun stopPreview()
}
