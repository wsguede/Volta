package com.volta.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volta.app.ui.theme.VoltaTheme

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(uiState = uiState, onResolutionChange = viewModel::setOutputResolution)
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onResolutionChange: (OutputResolution) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings Screen — ${uiState.outputResolution.label}")
    }
}

@Preview
@Composable
private fun PreviewSettingsScreen() {
    VoltaTheme {
        SettingsContent(
            uiState = SettingsUiState(),
            onResolutionChange = {},
        )
    }
}
