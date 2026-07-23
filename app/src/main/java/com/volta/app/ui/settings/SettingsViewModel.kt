package com.volta.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.volta.app.domain.settings.AppVersionProvider
import com.volta.app.domain.settings.SettingsRepository
import com.volta.app.domain.stitching.OutputResolution
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    appVersionProvider: AppVersionProvider
) : ViewModel() {

    private val appVersion = "v${appVersionProvider.versionName}"

    val uiState: StateFlow<SettingsUiState> = settingsRepository.outputResolution
        .map { resolution ->
            SettingsUiState(outputResolution = resolution, appVersion = appVersion)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(appVersion = appVersion)
        )

    fun setResolution(resolution: OutputResolution) {
        viewModelScope.launch {
            settingsRepository.setOutputResolution(resolution)
        }
    }
}
