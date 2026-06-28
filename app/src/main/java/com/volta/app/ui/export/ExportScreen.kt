package com.volta.app.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volta.app.ui.theme.VoltaTheme

@Composable
fun ExportScreen(onFinished: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExportContent(uiState = uiState, onFinished = onFinished, onCancel = onFinished)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportContent(uiState: ExportUiState, onFinished: () -> Unit, onCancel: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Export") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uiState.phase) {
                ExportPhase.Stitching -> {
                    Text("Stitching photosphere...")
                    LinearProgressIndicator(progress = { uiState.progress })
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
                ExportPhase.SavingToGallery -> {
                    Text("Saving to photo library...")
                    LinearProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
                ExportPhase.Complete -> {
                    Text("Photosphere exported!")
                    Button(onClick = onFinished) {
                        Text("Done")
                    }
                }
                ExportPhase.Error -> {
                    Text("Export failed: ${uiState.errorMessage}")
                    Button(onClick = onFinished) {
                        Text("Back")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewExportContentStitching() {
    VoltaTheme {
        ExportContent(
            uiState = ExportUiState(phase = ExportPhase.Stitching, progress = 0.6f),
            onFinished = {},
            onCancel = {}
        )
    }
}

@Preview
@Composable
fun PreviewExportContentComplete() {
    VoltaTheme {
        ExportContent(
            uiState = ExportUiState(phase = ExportPhase.Complete),
            onFinished = {},
            onCancel = {}
        )
    }
}
