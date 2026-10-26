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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.agent.PlayerAction
import com.example.pipeline.DashboardState
import com.example.ui.components.TelemetryCard
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurfaceElevated
import kotlin.math.abs
import kotlin.math.max

@Composable
fun RLTrainingScreen(
    state: DashboardState,
    onSetEpsilon: (Float) -> Unit,
    onSaveCheckpoint: (String) -> Unit,
    onLoadCheckpoint: () -> Unit,
    onResetPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var checkpointNameInput by remember { mutableStateOf("Level Devil v1 Policy") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Training Metrics Cards
        Text(
            text = "REINFORCEMENT LEARNING METRICS",
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
                label = "Episodes",
                value = "${state.agentMetrics.totalEpisodes}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Successes",
                value = "${state.agentMetrics.wins}",
                color = CyberGreen,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Failures",
                value = "${state.agentMetrics.deaths}",
                color = CyberRed,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TelemetryCard(
                label = "Win Rate",
                value = String.format("%.1f", state.agentMetrics.winRate),
                unit = "%",
                color = if (state.agentMetrics.winRate > 40f) CyberGreen else AmberPrimary,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Cumulative Reward",
                value = String.format("%.1f", state.agentMetrics.cumulativeReward),
                color = CyberCyan,
                modifier = Modifier.weight(1f)
            )
            TelemetryCard(
                label = "Q-States",
                value = "${state.agentMetrics.qTableSize}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        // Q-Value Action Distribution Visualizer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Q-VALUE DISTRIBUTION (CURRENT STATE)",
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

                        // Normalized Q-Value bar
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

        // Epsilon Exploration Control
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

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = state.agentMetrics.currentEpsilon,
                    onValueChange = onSetEpsilon,
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.testTag("epsilon_slider")
                )

                Text(
                    text = "Controls the trade-off between random action exploration and exploitation of learned Q-values. Automatically decays by 0.995 each terminal episode.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Checkpoint Persistence & Database
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MODEL PERSISTENCE (ROOM DATABASE)",
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
                    Text("Reset Policy & Wipe Q-Table")
                }
            }
        }

        // Mathematical Formulation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "BELLMAN EQUATION & REWARD FUNCTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Q(s, a) ← Q(s, a) + α [ r + γ max Q(s', a') - Q(s, a) ]\n\n" +
                            "• Step Penalty: -0.05 per tick\n" +
                            "• Distance Delta: + (dist_{t-1} - dist_t) × 25.0 toward Exit Door\n" +
                            "• Level Complete: +100.0\n" +
                            "• Hazard / Spike Collision: -50.0\n" +
                            "• Dynamic Trap Avoidance: +1.0",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                Text("Are you sure you want to clear all learned Q-values and reset episode counters? This action cannot be undone.")
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
