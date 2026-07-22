package com.volta.app.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.volta.app.ui.theme.VoltaTheme
import com.volta.app.ui.util.findComponentActivity
import com.volta.app.ui.util.openAppSettings

@Composable
fun CaptureScreen(
    onExport: () -> Unit,
    onSettings: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findComponentActivity()

    val permissionsLauncher = rememberLauncherForActivityResult(
        RequestMultiplePermissions()
    ) { results ->
        val cameraGranted = results[Manifest.permission.CAMERA] == true
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val isPermanent = !cameraGranted &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        viewModel.onCameraPermissionResult(
            granted = cameraGranted,
            isPermanentlyDenied = isPermanent
        )
        viewModel.onLocationPermissionResult(locationGranted)
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val locationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (cameraGranted) {
            viewModel.onCameraPermissionResult(granted = true, isPermanentlyDenied = false)
        }
        if (locationGranted) {
            viewModel.onLocationPermissionResult(granted = true)
        }
        val toRequest = buildList {
            if (!cameraGranted) add(Manifest.permission.CAMERA)
            if (!locationGranted) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (toRequest.isNotEmpty()) {
            permissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    PermissionsResumeObserver(
        activity = activity,
        context = context,
        cameraPermission = uiState.cameraPermission,
        onCameraPermissionResult = viewModel::onCameraPermissionResult,
        onLocationPermissionResult = viewModel::onLocationPermissionResult
    )

    CaptureContent(
        uiState = uiState,
        onExport = onExport,
        onSettings = onSettings,
        onRequestCameraPermission = {
            permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        },
        onOpenAppSettings = { context.openAppSettings() }
    )
}

@Composable
private fun PermissionsResumeObserver(
    activity: ComponentActivity,
    context: Context,
    cameraPermission: CapturePermissionState,
    onCameraPermissionResult: (granted: Boolean, isPermanentlyDenied: Boolean) -> Unit,
    onLocationPermissionResult: (granted: Boolean) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentCameraPermission = rememberUpdatedState(cameraPermission)
    val currentOnCamera = rememberUpdatedState(onCameraPermissionResult)
    val currentOnLocation = rememberUpdatedState(onLocationPermissionResult)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val cameraGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                val hasBeenRequested =
                    currentCameraPermission.value != CapturePermissionState.NotRequested
                if (cameraGranted) {
                    currentOnCamera.value(true, false)
                } else if (hasBeenRequested) {
                    val isPermanent = !activity.shouldShowRequestPermissionRationale(
                        Manifest.permission.CAMERA
                    )
                    currentOnCamera.value(false, isPermanent)
                }
                val locationGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                currentOnLocation.value(locationGranted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CaptureContent(
    uiState: CaptureUiState,
    onExport: () -> Unit,
    onSettings: () -> Unit,
    onRequestCameraPermission: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volta") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Text("⚙")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.cameraPermission) {
                CapturePermissionState.NotRequested -> CameraStartingPlaceholder()
                CapturePermissionState.Granted -> CaptureActiveContent(
                    uiState = uiState,
                    onExport = onExport
                )
                CapturePermissionState.Denied -> CameraPermissionDeniedCard(
                    onOpenAppSettings = onOpenAppSettings,
                    onRetry = onRequestCameraPermission,
                    modifier = Modifier.align(Alignment.Center)
                )
                CapturePermissionState.PermanentlyDenied -> CameraPermissionDeniedCard(
                    onOpenAppSettings = onOpenAppSettings,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (uiState.cameraPermission == CapturePermissionState.Granted &&
                uiState.gpsStatus == CaptureGpsStatus.Unavailable
            ) {
                GpsUnavailableBanner(modifier = Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun CameraStartingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Starting camera…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CaptureActiveContent(uiState: CaptureUiState, onExport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text("Frames: ${uiState.framesCaptured}")
        Text(
            "Coverage: ${
                String.format(
                    java.util.Locale.US,
                    "%.0f",
                    uiState.coveragePercent
                )
            }%"
        )
        Button(
            onClick = onExport,
            enabled = uiState.framesCaptured > 0
        ) {
            Text("Export")
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CameraPermissionDeniedCard(
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.padding(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Camera access required",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Volta needs camera access to capture a photosphere.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (onRetry != null) {
                Button(onClick = onRetry) { Text("Grant Permission") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenAppSettings) { Text("Open Settings") }
            } else {
                Button(onClick = onOpenAppSettings) { Text("Open Settings") }
            }
        }
    }
}

@Composable
private fun GpsUnavailableBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = "GPS unavailable — photosphere will be exported without location data",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Preview
@Composable
fun PreviewCaptureContent() {
    VoltaTheme {
        CaptureContent(
            uiState = CaptureUiState(
                cameraPermission = CapturePermissionState.Granted,
                framesCaptured = 42,
                coveragePercent = 73.5f
            ),
            onExport = {},
            onSettings = {}
        )
    }
}

@Preview
@Composable
fun PreviewCaptureContentNotRequested() {
    VoltaTheme {
        CaptureContent(
            uiState = CaptureUiState(),
            onExport = {},
            onSettings = {}
        )
    }
}

@Preview
@Composable
fun PreviewCaptureContentDenied() {
    VoltaTheme {
        CaptureContent(
            uiState = CaptureUiState(cameraPermission = CapturePermissionState.Denied),
            onExport = {},
            onSettings = {}
        )
    }
}

@Preview
@Composable
fun PreviewCaptureContentPermanentlyDenied() {
    VoltaTheme {
        CaptureContent(
            uiState = CaptureUiState(cameraPermission = CapturePermissionState.PermanentlyDenied),
            onExport = {},
            onSettings = {}
        )
    }
}

@Preview
@Composable
fun PreviewCaptureContentGpsUnavailable() {
    VoltaTheme {
        CaptureContent(
            uiState = CaptureUiState(
                cameraPermission = CapturePermissionState.Granted,
                framesCaptured = 5,
                coveragePercent = 20f,
                gpsStatus = CaptureGpsStatus.Unavailable
            ),
            onExport = {},
            onSettings = {}
        )
    }
}
