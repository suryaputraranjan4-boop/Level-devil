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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pipeline.DashboardState
import com.example.ui.components.TelemetryCard
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurfaceElevated

@Composable
fun ArchitectureScreen(
    state: DashboardState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // RAM stats
    val runtime = Runtime.getRuntime()
    val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)
    val totalMemoryMb = runtime.totalMemory() / (1024 * 1024)
    val freeMemoryMb = runtime.freeMemory() / (1024 * 1024)
    val usedMemoryMb = totalMemoryMb - freeMemoryMb

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Part 3 Architecture Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PART 3 — TOUCH CONTROL & GAMEPLAY AUTOMATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Real Android touch-control architecture using AccessibilityService.dispatchGesture(), continuous seed-initialized optical vision tracking, safe automation lifecycle (Start/Stop/Pause/Resume), and stroke cancellation.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Memory & Hardware Optimization
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "8GB RAM DEVICE OPTIMIZATION & RESOURCE FOOTPRINT",
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
                    TelemetryCard(
                        label = "Heap Used",
                        value = "$usedMemoryMb",
                        unit = "MB",
                        color = CyberGreen,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCard(
                        label = "Heap Total",
                        value = "$totalMemoryMb",
                        unit = "MB",
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCard(
                        label = "Max Allowed",
                        value = "$maxMemoryMb",
                        unit = "MB",
                        color = AmberPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• In-memory frame ingestion: Zero image files are written to disk, preventing Flash wear and I/O bottlenecks.\n" +
                            "• Reusable bitmap buffers: Fixed-size allocation prevents garbage collector pauses during continuous 30/60 FPS loops.\n" +
                            "• Concurrency guard: Atomic locks prevent duplicate stroke collisions and stuck touches.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Android Accessibility Limitations & Technical Facts Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ANDROID TOUCH & ACCESSIBILITY TECHNICAL FACTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Sandbox Boundary: Android prohibits one app from injecting MotionEvents into another without AccessibilityService.dispatchGesture(), Shizuku (ADB), or Root.\n\n" +
                            "2. Dispatch Gesture Capabilities: Supports tap (short stroke), hold (extended duration stroke), and simultaneous dual-touch (multiple StrokeDescriptions in a single GestureDescription).\n\n" +
                            "3. Gesture Queue Limitations: Android only processes one GestureDescription at a time. Overlapping requests cause cancellation. Our InputController uses an atomic concurrency lock to ensure strokes never collide.\n\n" +
                            "4. Safe Cancellation: When Stop is pressed, active sequence jobs are canceled and cancelPendingGestures() drains callbacks immediately.",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Capability Matrix
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PART 3 CAPABILITY MATRIX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                StatusSection(
                    title = "[VERIFIED & IMPLEMENTED] Real Touch & Automation",
                    titleColor = CyberGreen,
                    items = listOf(
                        "Tap, Hold, and Release gesture dispatching via LevelDevilAccessibilityService",
                        "Simultaneous dual-touch (holding Left/Right while tapping Jump) in a single GestureDescription",
                        "Safe automation lifecycle: Explicit user Start, Stop, Pause, Resume with zero auto-start on launch",
                        "Emergency stroke cancellation and concurrency lock preventing stuck touches or duplicate strokes",
                        "Continuous seed-guided vision tracking: initializes from player seed and tracks actual position/velocity",
                        "Spatial lookahead identifying next obstacle and platform ahead of player",
                        "Automated decision pause when player detection is lost or obstructed (no fake detections)",
                        "Persistent calibration (all 8 coordinates: Left, Right, Jump, Player Seed + timing settings) in SharedPreferences",
                        "Configurable tap duration, hold duration, and sequence delays"
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                StatusSection(
                    title = "[TESTED] Unit & Robolectric Suite",
                    titleColor = CyberCyan,
                    items = listOf(
                        "Coordinate normalization across varying screen resolutions and aspect ratios",
                        "Tap, hold, release, and cancellation lifecycle",
                        "Automation state transitions (STOPPED -> RUNNING -> PAUSED -> RESUME -> STOPPED)",
                        "Input failure handling when Accessibility Service is unavailable",
                        "Seed-to-observation player tracking and velocity estimation",
                        "Full Android APK compilation and packaging verified (AGP 9.1.1 + Kotlin 2.2.10)"
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                StatusSection(
                    title = "[REMAINING BLOCKERS FOR PHYSICAL LEVEL DEVIL PLAY]",
                    titleColor = CyberRed,
                    items = listOf(
                        "Device Authorization: User must manually enable 'Level Devil AI Touch Dispatcher' in Android System Settings -> Accessibility on their device",
                        "Screen Capture Authorization: User must grant MediaProjection permission in the system popup",
                        "Level Devil Game Installation: The Level Devil APK must be installed and running in foreground on the device"
                    )
                )
            }
        }
    }
}

@Composable
private fun StatusSection(
    title: String,
    titleColor: Color,
    items: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        for (item in items) {
            Text(
                text = "• $item",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
        }
    }
}
