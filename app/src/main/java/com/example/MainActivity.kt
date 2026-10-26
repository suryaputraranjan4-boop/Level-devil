package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.screens.ArchitectureScreen
import com.example.ui.screens.LiveDashboardScreen
import com.example.ui.screens.RLTrainingScreen
import com.example.ui.screens.TouchInputScreen
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // ActivityResultLauncher for MediaProjection screen capture authorization
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            viewModel.startScreenCapture(
                context = this,
                resultCode = result.resultCode,
                resultData = result.data!!,
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                density = metrics.densityDpi
            )
        }
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handled gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MyApplicationTheme {
                MainAppContent(
                    viewModel = viewModel,
                    onRequestCaptureStart = {
                        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
                    },
                    onStopCapture = {
                        viewModel.stopScreenCapture(this)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    onRequestCaptureStart: () -> Unit,
    onStopCapture: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val feedbackMessage by viewModel.userFeedback.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissFeedback()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Level Devil AI Player",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Vision HUD", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = "RL Training") },
                    label = { Text("RL Policy", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    icon = { Icon(Icons.Default.TouchApp, contentDescription = "Input") },
                    label = { Text("Controls", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Diagnostics") },
                    label = { Text("Architecture", fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTabIndex) {
                0 -> LiveDashboardScreen(
                    state = state,
                    onRequestCaptureStart = onRequestCaptureStart,
                    onStopCapture = onStopCapture,
                    onStartAutomation = { viewModel.startAutomation() },
                    onStopAutomation = { viewModel.stopAutomation() },
                    onPauseAutomation = { viewModel.pauseAutomation() },
                    onResumeAutomation = { viewModel.resumeAutomation() },
                    onToggleTraining = { viewModel.toggleTraining() },
                    onSetSourceMode = { viewModel.setSourceMode(it) },
                    onStepSimulation = { viewModel.stepSimulationOnce() }
                )
                1 -> RLTrainingScreen(
                    state = state,
                    onSetEpsilon = { viewModel.setEpsilon(it) },
                    onSaveCheckpoint = { viewModel.saveCheckpoint(it) },
                    onLoadCheckpoint = { viewModel.loadCheckpoint() },
                    onResetPolicy = { viewModel.resetPolicy() }
                )
                2 -> TouchInputScreen(
                    state = state,
                    onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                    onRunSelfTest = { w, h -> viewModel.runInputSelfTest(w, h) },
                    onUpdateCalibration = { lx, ly, rx, ry, jx, jy, sx, sy ->
                        viewModel.updateCalibration(lx, ly, rx, ry, jx, jy, sx, sy)
                    },
                    onUpdateTimings = { tap, hold, delay ->
                        viewModel.updateTimingSettings(tap, hold, delay)
                    },
                    onExecuteTestSequence = { w, h ->
                        viewModel.executeTestSequence(w, h)
                    },
                    onCancelPendingActions = {
                        viewModel.stopAutomation()
                    }
                )
                3 -> ArchitectureScreen(
                    state = state
                )
            }
        }
    }
}
