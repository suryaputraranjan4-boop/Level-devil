package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pipeline.AutomationState
import com.example.pipeline.DashboardState
import com.example.pipeline.PipelineSourceMode
import com.example.ui.components.StatusBadge
import com.example.ui.components.TelemetryCard
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurfaceElevated
import com.example.vision.ObjectType

@Composable
fun LiveDashboardScreen(
    state: DashboardState,
    onRequestCaptureStart: () -> Unit,
    onStopCapture: () -> Unit,
    onStartAutomation: () -> Unit,
    onStopAutomation: () -> Unit,
    onPauseAutomation: () -> Unit,
    onResumeAutomation: () -> Unit,
    onToggleTraining: () -> Unit,
    onSetSourceMode: (PipelineSourceMode) -> Unit,
    onStepSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 1. Safe Automation Master Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (state.automationState) {
                    AutomationState.RUNNING -> Color(0xFF0F2617)
                    AutomationState.PAUSED -> Color(0xFF26200A)
                    AutomationState.ERROR_PAUSED -> Color(0xFF2E0F12)
                    AutomationState.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when (state.automationState) {
                    AutomationState.RUNNING -> CyberGreen
                    AutomationState.PAUSED -> AmberPrimary
                    AutomationState.ERROR_PAUSED -> CyberRed
                    AutomationState.STOPPED -> CardBorder
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = when (state.automationState) {
                                AutomationState.RUNNING -> CyberGreen
                                AutomationState.PAUSED -> AmberPrimary
                                AutomationState.ERROR_PAUSED -> CyberRed
                                AutomationState.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "AUTOMATION ENGINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Status: ${state.automationState.label.uppercase()}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    StatusBadge(
                        label = state.automationState.label.uppercase(),
                        isActive = state.automationState == AutomationState.RUNNING,
                        activeColor = CyberGreen,
                        inactiveColor = when (state.automationState) {
                            AutomationState.PAUSED -> AmberPrimary
                            AutomationState.ERROR_PAUSED -> CyberRed
                            else -> Color.Gray
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = state.statusMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Master Automation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state.automationState) {
                        AutomationState.STOPPED, AutomationState.ERROR_PAUSED -> {
                            Button(
                                onClick = onStartAutomation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("start_automation_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Automation", fontWeight = FontWeight.Bold)
                            }
                        }
                        AutomationState.RUNNING -> {
                            Button(
                                onClick = onPauseAutomation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pause_automation_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onStopAutomation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_automation_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberRed, contentColor = Color.White)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Automation", fontWeight = FontWeight.Bold)
                            }
                        }
                        AutomationState.PAUSED -> {
                            Button(
                                onClick = onResumeAutomation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("resume_automation_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen, contentColor = Color.Black)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onStopAutomation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_automation_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberRed, contentColor = Color.White)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Source Switcher & Connection Status Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(
                    label = if (state.isCapturing) "CAPTURE: ON" else "CAPTURE: OFF",
                    isActive = state.isCapturing,
                    activeColor = CyberGreen,
                    inactiveColor = Color.Gray
                )
                StatusBadge(
                    label = if (state.isAccessibilityConnected) "ACCESSIBILITY: ON" else "ACCESSIBILITY: OFF",
                    isActive = state.isAccessibilityConnected,
                    activeColor = CyberCyan,
                    inactiveColor = CyberRed
                )
            }

            // Mode Selector Pill
            FilledTonalButton(
                onClick = {
                    val nextMode = if (state.sourceMode == PipelineSourceMode.LIVE_SCREEN) {
                        PipelineSourceMode.SYNTHETIC_SIMULATION
                    } else {
                        PipelineSourceMode.LIVE_SCREEN
                    }
                    onSetSourceMode(nextMode)
                },
                modifier = Modifier.testTag("mode_toggle_button")
            ) {
                Text(
                    text = if (state.sourceMode == PipelineSourceMode.LIVE_SCREEN) "Mode: Live Screen" else "Mode: Synthetic Sim",
                    fontSize = 12.sp
                )
            }
        }

        // Accessibility Warning Banner if not enabled
        if (!state.isAccessibilityConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1014)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = CyberRed)
                    Text(
                        text = "Accessibility Service is not enabled. Touch automation is disabled. Open 'Controls' tab to authorize.",
                        fontSize = 11.sp,
                        color = Color(0xFFFFB3B8)
                    )
                }
            }
        }

        // 3. Live Captured Screen with Vision Overlays
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color(0xFF0F1015)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = state.currentFrameBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Game Screen Preview",
                            modifier = Modifier.fillMaxSize()
                        )

                        // Render Computer Vision Overlays
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasW = size.width
                            val canvasH = size.height

                            for (obj in state.observation.detectedObjects) {
                                val box = obj.boundingBox
                                val leftPx = box.left * canvasW
                                val topPx = box.top * canvasH
                                val widthPx = (box.right - box.left) * canvasW
                                val heightPx = (box.bottom - box.top) * canvasH

                                val strokeColor = when (obj.type) {
                                    ObjectType.PLAYER -> Color(0xFFFFD000)
                                    ObjectType.DOOR -> CyberCyan
                                    ObjectType.SPIKE, ObjectType.MOVING_TRAP -> CyberRed
                                    ObjectType.PLATFORM -> CyberGreen
                                    ObjectType.UNKNOWN -> Color.White
                                }

                                drawRect(
                                    color = strokeColor,
                                    topLeft = Offset(leftPx, topPx),
                                    size = Size(widthPx, heightPx),
                                    style = Stroke(width = 3f)
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Awaiting Screen Frame Ingestion",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Watermark / Mode indicator in bottom right
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (state.sourceMode == PipelineSourceMode.LIVE_SCREEN) "MEDIA_PROJECTION" else "SYNTHETIC_CALIBRATION",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sub-panel under the frame showing tracking state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vision: ${state.visionStatus}",
                        fontSize = 11.sp,
                        color = if (state.observation.playerDetected) CyberGreen else AmberPrimary
                    )
                    Text(
                        text = "Player: ${state.lastDetectedPlayerPos}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 4. Live Vision-to-Action Telemetry Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "VISION-TO-ACTION REASONING & DISPATCH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.currentAction.shortSymbol,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = AmberPrimary
                        )
                        Column {
                            Text(
                                text = state.currentAction.displayName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.lastActionResult,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        StatusBadge(
                            label = if (state.isGestureInProgress) "STROKE ACTIVE" else "IDLE",
                            isActive = state.isGestureInProgress,
                            activeColor = CyberCyan,
                            inactiveColor = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Movement & Obstacle diagnostics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceElevated)
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("PLAYER MOTION", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            Text(state.playerVelocityDesc, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CyberCyan)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceElevated)
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("NEXT OBSTACLE", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                            Text(state.nextObstacleDesc, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (state.nextObstacleDesc.contains("Path Clear")) CyberGreen else CyberRed)
                        }
                    }
                }
            }
        }

        // 5. Latency Telemetry Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "REAL-TIME LATENCY & PIPELINE METRICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryCard(
                    label = "Total Loop",
                    value = "${state.totalLatencyMs}",
                    unit = "ms",
                    color = if (state.totalLatencyMs < 25) CyberGreen else AmberPrimary,
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    label = "Framerate",
                    value = String.format("%.1f", state.fps),
                    unit = "FPS",
                    color = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    label = "Vision",
                    value = "${state.visionLatencyMs}",
                    unit = "ms",
                    color = CyberGreen,
                    modifier = Modifier.weight(1f)
                )
                TelemetryCard(
                    label = "Input Dispatch",
                    value = "${state.inputLatencyMs}",
                    unit = "ms",
                    color = AmberPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 6. Pipeline Capture & Mode Controls
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "FRAME CAPTURE CONTROLS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!state.isCapturing) {
                    Button(
                        onClick = onRequestCaptureStart,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_capture_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Capture", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStopCapture,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stop_capture_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberRed, contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Capture", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onToggleTraining,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_training_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isTrainingActive) CyberGreen else DarkSurfaceElevated,
                        contentColor = if (state.isTrainingActive) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.isTrainingActive) "Pause RL" else "Train Policy", fontWeight = FontWeight.SemiBold)
                }
            }

            if (state.sourceMode == PipelineSourceMode.SYNTHETIC_SIMULATION) {
                OutlinedButton(
                    onClick = onStepSimulation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("step_simulation_button")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Step Synthetic Simulation (+1 frame tick)")
                }
            }
        }
    }
}
