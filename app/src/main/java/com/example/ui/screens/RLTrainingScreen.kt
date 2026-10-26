package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.EpisodeOutcome
import com.example.agent.PlayerAction
import com.example.pipeline.DashboardState
import com.example.ui.components.StatusBadge
import com.example.ui.components.TelemetryCard
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurfaceElevated
import kotlin.math.abs

@Composable
fun RLTrainingScreen(
    state: DashboardState,
    onSetEpsilon: (Float) -> Unit,
    onSaveCheckpoint: (String) -> Unit,
    onLoadCheckpoint: () -> Unit,
    onResetPolicy: () -> Unit,
    onToggleInferenceOnly: () -> Unit = {},
    onUpdateHyperparameters: (Float, Float, Float, Float) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var checkpointNameInput by remember { mutableStateOf("Level Devil v1 Policy") }

    var selectedMetricView by remember { mutableStateOf("REAL") } // "REAL" or "SIMULATION"

    var localAlpha by remember(state.learningRate) { mutableFloatStateOf(state.learningRate) }
    var localGamma by remember(state.discountFactor) { mutableFloatStateOf(state.discountFactor) }
    var localMinEps by remember(state.minEpsilon) { mutableFloatStateOf(state.minEpsilon) }
    var localDecay by remember(state.epsilonDecay) { mutableFloatStateOf(state.epsilonDecay) }

    val activeMetrics = if (selectedMetricView == "REAL") state.realMetrics else state.simMetrics

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 1. Evidence-Based Telemetry & Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "REINFORCEMENT LEARNING ENGINE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (state.isInferenceOnly) "Mode: INFERENCE-ONLY (Frozen)" else "Mode: ADAPTIVE TRAINING",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isInferenceOnly) CyberCyan else CyberGreen
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onToggleInferenceOnly,
                    modifier = Modifier.testTag("toggle_inference_button")
                ) {
                    Icon(
                        imageVector = if (state.isInferenceOnly) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (state.isInferenceOnly) "Resume Learning" else "Freeze Weights", fontSize = 11.sp)
                }
            }
        }

        // 2. Metrics Separation Selector (Real Screen vs Synthetic Simulation)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DATA ISOLATION: METRIC BUCKET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedMetricView = "REAL" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedMetricView == "REAL") CyberGreen else DarkSurfaceElevated,
                                contentColor = if (selectedMetricView == "REAL") Color.Black else Color.White
                            ),
                            modifier = Modifier.height(32.dp).testTag("select_real_metrics")
                        ) {
                            Text("Real Game Screen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { selectedMetricView = "SIMULATION" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedMetricView == "SIMULATION") AmberPrimary else DarkSurfaceElevated,
                                contentColor = if (selectedMetricView == "SIMULATION") Color.Black else Color.White
                            ),
                            modifier = Modifier.height(32.dp).testTag("select_sim_metrics")
                        ) {
                            Text("Synthetic Sim", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (selectedMetricView == "REAL") {
                        "Real screen metrics only. Door overlap is not a confirmed win; death counts come from a visual heuristic and need on-device validation."
                    } else {
                        "Showing synthetic benchmark loop outcomes. Synthetic data is isolated from real game metrics."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Active Metrics Display Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Episodes",
                value = "${activeMetrics.totalEpisodes}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Confirmed Wins",
                value = "${activeMetrics.wins}",
                color = CyberGreen,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Death-Screen Detections",
                value = "${activeMetrics.deaths}",
                color = CyberRed,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Observed Win Rate",
                value = String.format("%.1f", activeMetrics.winRate),
                unit = "%",
                color = if (activeMetrics.winRate > 40f) CyberGreen else AmberPrimary,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Rolling Reward",
                value = String.format("%.2f", activeMetrics.rollingAverageReward),
                color = if (activeMetrics.rollingAverageReward >= 0) CyberGreen else CyberRed,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Q-States",
                value = "${activeMetrics.qTableSize}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // 4. Action Execution Integrity (Selected vs Dispatched vs Rejected)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ACTION EXECUTION INTEGRITY (ANDROID OS TELEMETRY)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Selected by Policy", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${activeMetrics.actionsSelected}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dispatched to OS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${activeMetrics.actionsDispatched}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyberGreen)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dropped / Canceled", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${activeMetrics.actionsRejected}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (activeMetrics.actionsRejected > 0) CyberRed else Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Q-Learning updates exclusively on verified dispatched actions. Rejected touches are not penalized as game blunders.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 5. Evidence-Based Reward Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LAST EVIDENCE-BASED REWARD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = String.format("%+.2f", state.lastRewardValue),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (state.lastRewardValue >= 0) CyberGreen else CyberRed
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Reason: ${state.lastRewardReason}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Episode Outcome: ${state.lastOutcome.label}",
                    fontSize = 11.sp,
                    color = when (state.lastOutcome) {
                        EpisodeOutcome.WIN -> CyberGreen
                        EpisodeOutcome.DEATH -> CyberRed
                        EpisodeOutcome.UNKNOWN -> AmberPrimary
                        EpisodeOutcome.IN_PROGRESS -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // 6. Failure Memory Bank Inspector (Learn from Mistakes)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.Dangerous, contentDescription = null, tint = CyberRed, modifier = Modifier.size(18.dp))
                        Text(
                            text = "FAILURE MEMORY BANK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberRed,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "${state.recentFailures.size} Lethal Traces",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (state.recentFailures.isEmpty()) {
                    Text(
                        text = "No verified deaths recorded in memory bank yet. When a player death occurs, the lethal coordinate and blunder action are saved here to adjust exploration.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.recentFailures.takeLast(3).reversed().forEach { failure ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${if (failure.levelNumber > 0) "Level ${failure.levelNumber}" else "Level unknown"} Death at (${(failure.deathPosition.x * 100).toInt()}%, ${(failure.deathPosition.y * 100).toInt()}%)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Contributing Action: ${failure.contributingAction?.displayName ?: "Unknown"}",
                                            fontSize = 10.sp,
                                            color = CyberRed
                                        )
                                    }
                                    StatusBadge(
                                        label = if (failure.isSimulation) "SIM TRACE" else "LIVE TRACE",
                                        isActive = false,
                                        activeColor = CyberGreen,
                                        inactiveColor = if (failure.isSimulation) AmberPrimary else CyberRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Q-Value Action Distribution Visualizer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Q-VALUE DISTRIBUTION (CURRENT DISCRETIZED STATE)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                val qValues = state.actionQValues
                var maxVal = 0.1f
                for ((_, v) in qValues) {
                    if (abs(v) > maxVal) maxVal = abs(v)
                }

                for (action in PlayerAction.values()) {
                    val qVal = qValues[action] ?: 0f
                    val isSelected = action == state.currentAction

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = action.shortSymbol,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = action.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AmberPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = String.format("%.3f", qVal),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (qVal >= 0) CyberGreen else CyberRed
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val barFraction = (abs(qVal) / maxVal).coerceIn(0.02f, 1.0f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(DarkSurfaceElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = barFraction)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (qVal >= 0) CyberGreen else CyberRed)
                            )
                        }
                    }
                }
            }
        }

        // 8. Exploration & Hyperparameters Control
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXPLORATION RATE (ε-GREEDY)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${(state.agentMetrics.currentEpsilon * 100).toInt()}% Random Explore",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AmberPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = state.agentMetrics.currentEpsilon,
                    onValueChange = onSetEpsilon,
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.testTag("epsilon_slider")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "HYPERPARAMETERS (BELLMAN TD-LEARNING)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Alpha (Learning Rate)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Learning Rate (α): ${String.format("%.2f", localAlpha)}", fontSize = 11.sp)
                }
                Slider(
                    value = localAlpha,
                    onValueChange = {
                        localAlpha = it
                        onUpdateHyperparameters(localAlpha, localGamma, localMinEps, localDecay)
                    },
                    valueRange = 0.05f..0.50f
                )

                // Gamma (Discount Factor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Discount Factor (γ): ${String.format("%.2f", localGamma)}", fontSize = 11.sp)
                }
                Slider(
                    value = localGamma,
                    onValueChange = {
                        localGamma = it
                        onUpdateHyperparameters(localAlpha, localGamma, localMinEps, localDecay)
                    },
                    valueRange = 0.70f..0.99f
                )
            }
        }

        // 9. Room Checkpoint Persistence & Policy Wipe
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CHECKPOINT PERSISTENCE (ROOM DATABASE)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_checkpoint_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Checkpoint", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onLoadCheckpoint,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load_checkpoint_button")
                    ) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Latest", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_policy_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRed)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Policy, Memory Bank & Q-Table")
                }
            }
        }
    }

    // Save Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Policy Checkpoint") },
            text = {
                Column {
                    Text("Enter a label for this reinforcement learning checkpoint:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = checkpointNameInput,
                        onValueChange = { checkpointNameInput = it },
                        label = { Text("Checkpoint Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveCheckpoint(checkpointNameInput)
                    showSaveDialog = false
                }) {
                    Text("Save to DB")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Confirm Policy Reset") },
            text = {
                Text("Are you sure you want to clear all learned Q-values, failure traces, and reset episode counters? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetPolicy()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
