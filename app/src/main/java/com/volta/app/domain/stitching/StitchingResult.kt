package com.volta.app.domain.stitching

sealed interface StitchingResult {
    data class Success(val outputPath: String) : StitchingResult
    data class Error(val message: String) : StitchingResult
}
