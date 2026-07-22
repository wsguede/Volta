package com.volta.app.domain.stitching

import com.volta.app.domain.capture.CapturedFrame
import kotlinx.coroutines.flow.Flow

interface StitchingEngine {
    fun stitch(
        frames: List<CapturedFrame>,
        outputWidth: Int,
        outputHeight: Int
    ): Flow<StitchingProgress>
}

data class StitchingProgress(val percent: Float, val result: StitchingResult? = null)
