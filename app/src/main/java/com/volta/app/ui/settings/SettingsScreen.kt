package com.volta.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volta.app.domain.stitching.OutputResolution
import com.volta.app.ui.theme.VoltaTheme

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onResolutionSelected = viewModel::setResolution
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onResolutionSelected: (OutputResolution) -> Unit
) {
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
        },
        bottomBar = {
            Text(
                text = uiState.appVersion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Output Resolution",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutputResolution.entries.forEach { resolution ->
                val label = resolution.name.lowercase()
                    .replaceFirstChar { it.uppercase() }
                ListItem(
                    headlineContent = {
                        Text("$label (${resolution.width} × ${resolution.height})")
                    },
                    leadingContent = {
                        RadioButton(
                            selected = uiState.outputResolution == resolution,
                            onClick = { onResolutionSelected(resolution) }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResolutionSelected(resolution) }
                )
            }
        }
    }
}

@Preview
@Composable
fun PreviewSettingsContent() {
    VoltaTheme {
        SettingsContent(
            uiState = SettingsUiState(appVersion = "v2026.7.0"),
            onBack = {},
            onResolutionSelected = {}
        )
    }
}
