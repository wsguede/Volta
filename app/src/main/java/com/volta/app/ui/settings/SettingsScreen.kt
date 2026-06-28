package com.volta.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volta.app.domain.stitching.OutputResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Output Resolution",
                modifier = Modifier.padding(padding)
            )
            OutputResolution.entries.forEach { resolution ->
                ListItem(
                    headlineContent = {
                        val label = resolution.name.lowercase()
                            .replaceFirstChar { it.uppercase() }
                        Text("$label (${resolution.width} × ${resolution.height})")
                    },
                    leadingContent = {
                        RadioButton(
                            selected = uiState.outputResolution == resolution,
                            onClick = { viewModel.setResolution(resolution) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setResolution(resolution) }
                )
            }
        }
    }
}
