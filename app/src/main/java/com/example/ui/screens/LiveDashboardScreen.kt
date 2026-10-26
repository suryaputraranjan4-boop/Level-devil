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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import com.example.agent.PlayerAction
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

        // Source Switcher & Status Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(
                    label = if (state.isCapturing) "CAPTURE: ACTIVE" else "CAPTURE: IDLE",
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

        // Live Captured Screen with Vision Bounding Box Overlay
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
                        // Display bitmap
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Game Screen Preview",
                            modifier = Modifier.fillMaxSize()
                        )

                        // Render Computer Vision Overlays (Bounding Boxes & Labels)
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

                // Sub-panel under the frame showing object detections
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected: ${state.observation.detectedObjects.size} objects " +
                                "(${if (state.observation.playerDetected) "Player ✓" else "Searching..."}, " +
                                "${if (state.observation.isGrounded) "Grounded" else "Airborne"})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Vision: ${state.visionLatencyMs}ms",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyberCyan
                    )
                }
            }
        }

        // Action & Decision HUD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RL AGENT DECISION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
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
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val qVal = state.actionQValues[state.currentAction] ?: 0f
                            Text(
                                text = "Q-Value: ${String.format("%.3f", qVal)} | Epsilon: ${String.format("%.2f", state.agentMetrics.currentEpsilon)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Training active pulse
                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(
                        label = if (state.isTrainingActive) "TRAINING: ON" else "INFERENCE ONLY",
                        isActive = state.isTrainingActive,
                        activeColor = AmberPrimary,
                        inactiveColor = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Step: ${state.agentMetrics.totalSteps}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Telemetry Grid (FPS, Latencies)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "PERFORMANCE & LATENCY TELEMETRY",
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
                    label = "Total Latency",
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
                    label = "RL Policy",
                    value = "${state.agentLatencyMs}",
                    unit = "ms",
                    color = AmberPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Live Control Actions
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "PIPELINE CONTROLS",
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
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
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
                    Text("Step Synthetic Simulation Frame (+1 tick)")
                }
            }
        }
    }
}
