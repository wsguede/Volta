package com.volta.app.ui.export

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class ExportUiState {
    data object Idle : ExportUiState()
    data class Stitching(val progressPercent: Float) : ExportUiState()
    data object Saving : ExportUiState()
    data object Success : ExportUiState()
    data class Error(val message: String) : ExportUiState()
}

@HiltViewModel
class ExportViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()
}
