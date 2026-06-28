package com.volta.app.ui.export

data class ExportUiState(
    val phase: ExportPhase = ExportPhase.Stitching,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

sealed interface ExportPhase {
    data object Stitching : ExportPhase
    data object SavingToGallery : ExportPhase
    data object Complete : ExportPhase
    data object Error : ExportPhase
}
