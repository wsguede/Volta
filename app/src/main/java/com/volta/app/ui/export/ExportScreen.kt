package com.volta.app.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ExportScreen(
    onFinished: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.phase) {
                ExportPhase.Stitching -> {
                    Text("Stitching photosphere...")
                    LinearProgressIndicator(progress = { uiState.progress })
                }
                ExportPhase.SavingToGallery -> {
                    Text("Saving to photo library...")
                    LinearProgressIndicator()
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
