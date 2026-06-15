package com.volta.app.ui.capture

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
fun CaptureScreen(
    onNavigateToExport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CaptureContent(uiState = uiState)
}

@Composable
private fun CaptureContent(uiState: CaptureUiState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Capture Screen — ${uiState.coveragePercent}% covered")
    }
}

@Preview
@Composable
private fun PreviewCaptureScreen() {
    VoltaTheme {
        CaptureContent(uiState = CaptureUiState(coveragePercent = 42f))
    }
}
