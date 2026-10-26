package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.input.InputStatus
import com.example.pipeline.DashboardState
import com.example.ui.theme.AmberPrimary
import com.example.ui.theme.CardBorder
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkSurfaceElevated

@Composable
fun TouchInputScreen(
    state: DashboardState,
    onOpenAccessibilitySettings: () -> Unit,
    onRunSelfTest: (Int, Int) -> Unit,
    onUpdateCalibration: (Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    onUpdateTimings: (Long, Long, Long) -> Unit,
    onExecuteTestSequence: (Int, Int) -> Unit,
    onCancelPendingActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.roundToPx() }

    // Calibration coordinates
    var leftX by remember(state.leftBtnX) { mutableFloatStateOf(state.leftBtnX) }
    var leftY by remember(state.leftBtnY) { mutableFloatStateOf(state.leftBtnY) }
    var rightX by remember(state.rightBtnX) { mutableFloatStateOf(state.rightBtnX) }
    var rightY by remember(state.rightBtnY) { mutableFloatStateOf(state.rightBtnY) }
    var jumpX by remember(state.jumpBtnX) { mutableFloatStateOf(state.jumpBtnX) }
    var jumpY by remember(state.jumpBtnY) { mutableFloatStateOf(state.jumpBtnY) }
    var playerSeedX by remember(state.playerSeedX) { mutableFloatStateOf(state.playerSeedX) }
    var playerSeedY by remember(state.playerSeedY) { mutableFloatStateOf(state.playerSeedY) }
    var activeHandle by remember { mutableIntStateOf(-1) }

    // Timing state
    var tapDuration by remember(state.tapDurationMs) { mutableLongStateOf(state.tapDurationMs) }
    var holdDuration by remember(state.holdDurationMs) { mutableLongStateOf(state.holdDurationMs) }
    var sequenceDelay by remember(state.sequenceDelayMs) { mutableLongStateOf(state.sequenceDelayMs) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // 1. Accessibility Service Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isAccessibilityConnected) MaterialTheme.colorScheme.surfaceVariant else DarkSurfaceElevated
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (state.isAccessibilityConnected) CyberGreen else CyberRed
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
                            imageVector = if (state.isAccessibilityConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (state.isAccessibilityConnected) CyberGreen else CyberRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "ACCESSIBILITY GESTURE DISPATCHER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (state.isAccessibilityConnected) "Connected & Authorized" else "Service Not Enabled",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (state.isAccessibilityConnected)
                        "The Level Devil AI Accessibility Service is active and bound to Android OS. It dispatches hardware taps, continuous holds, and dual-touch strokes via dispatchGesture()."
                    else
                        "Android security sandbox restricts cross-app input injection. You must enable 'Level Devil AI Touch Dispatcher' in Android System Settings -> Accessibility.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_accessibility_settings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onRunSelfTest(screenWidthPx, screenHeightPx) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("run_input_selftest_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Single Tap", fontSize = 12.sp)
                    }
                }

                if (state.inputStatus == InputStatus.TEST_VERIFIED || state.inputStatus == InputStatus.TEST_FAILED) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (state.inputStatus == InputStatus.TEST_VERIFIED) Color(0xFF00381C) else Color(0xFF380000))
                            .border(1.dp, if (state.inputStatus == InputStatus.TEST_VERIFIED) CyberGreen else CyberRed, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (state.inputStatus == InputStatus.TEST_VERIFIED)
                                "✓ Self-Test Verified: Android OS successfully acknowledged gesture dispatch in ${state.inputLatencyMs}ms."
                            else
                                "✗ Self-Test Failed: Android rejected or dropped gesture. Confirm Accessibility Service is turned on.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (state.inputStatus == InputStatus.TEST_VERIFIED) CyberGreen else CyberRed
                        )
                    }
                }
            }
        }

        // 2. Action Timing & Sequence Controls
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
                        text = "GESTURE TIMING & ACTION SEQUENCES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Tap Duration: ${tapDuration}ms (Used for quick Jump taps)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = tapDuration.toFloat(),
                    onValueChange = { tapDuration = it.toLong() },
                    valueRange = 20f..300f
                )

                Text("Hold Duration: ${holdDuration}ms (Used for Left / Right directional walks)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = holdDuration.toFloat(),
                    onValueChange = { holdDuration = it.toLong() },
                    valueRange = 50f..800f
                )

                Text("Sequence Step Delay: ${sequenceDelay}ms (Time between chained actions)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = sequenceDelay.toFloat(),
                    onValueChange = { sequenceDelay = it.toLong() },
                    valueRange = 20f..400f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onUpdateTimings(tapDuration, holdDuration, sequenceDelay) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_timings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Save Timings", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onExecuteTestSequence(screenWidthPx, screenHeightPx) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_sequence_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                    ) {
                        Text("Test Sequence", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onCancelPendingActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_actions_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRed)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Emergency Stop: Cancel All Gestures")
                }
            }
        }

        // 3. Four Draggable Calibration Markers & Interactive Canvas
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "VIRTUAL TOUCH CALIBRATION (4 MARKERS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Drag markers on the interactive preview below, or use the sliders. Saving persists all 8 coordinates to device storage.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive schematic of phone screen with 4 draggable markers
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0B0C12))
                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { touch ->
                                    val points = listOf(
                                        Offset(leftX * size.width, leftY * size.height),
                                        Offset(rightX * size.width, rightY * size.height),
                                        Offset(jumpX * size.width, jumpY * size.height),
                                        Offset(playerSeedX * size.width, playerSeedY * size.height)
                                    )
                                    val nearest = points.indices.minByOrNull { (points[it] - touch).getDistance() }
                                    activeHandle = if (nearest != null && (points[nearest] - touch).getDistance() <= 48f) nearest else -1
                                },
                                onDragEnd = { activeHandle = -1 },
                                onDragCancel = { activeHandle = -1 },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val nx = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                                    val ny = (change.position.y / size.height).coerceIn(0.02f, 0.98f)
                                    when (activeHandle) {
                                        0 -> { leftX = nx; leftY = ny }
                                        1 -> { rightX = nx; rightY = ny }
                                        2 -> { jumpX = nx; jumpY = ny }
                                        3 -> { playerSeedX = nx; playerSeedY = ny }
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw Left Button target (Handle 0)
                        drawCircle(color = AmberPrimary, radius = 24f, center = Offset(leftX * w, leftY * h))
                        drawCircle(color = Color.White, radius = 28f, center = Offset(leftX * w, leftY * h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                        // Draw Right Button target (Handle 1)
                        drawCircle(color = AmberPrimary, radius = 24f, center = Offset(rightX * w, rightY * h))
                        drawCircle(color = Color.White, radius = 28f, center = Offset(rightX * w, rightY * h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                        // Draw Jump Button target (Handle 2)
                        drawCircle(color = CyberCyan, radius = 28f, center = Offset(jumpX * w, jumpY * h))

                        // Draw Player Seed Target (Handle 3)
                        drawCircle(color = CyberGreen, radius = 20f, center = Offset(playerSeedX * w, playerSeedY * h))
                        drawCircle(color = Color.White, radius = 26f, center = Offset(playerSeedX * w, playerSeedY * h), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    }

                    // Labels
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text("← LEFT   RIGHT →", color = AmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        Text("JUMP ↑", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Coordinate Sliders
                Text("Left Arrow Button (X: ${(leftX * 100).toInt()}%, Y: ${(leftY * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = leftX, onValueChange = { leftX = it }, valueRange = 0.02f..0.98f)

                Spacer(modifier = Modifier.height(6.dp))

                Text("Right Arrow Button (X: ${(rightX * 100).toInt()}%, Y: ${(rightY * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = rightX, onValueChange = { rightX = it }, valueRange = 0.02f..0.98f)

                Spacer(modifier = Modifier.height(6.dp))

                Text("Jump Button (X: ${(jumpX * 100).toInt()}%, Y: ${(jumpY * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = jumpX, onValueChange = { jumpX = it }, valueRange = 0.02f..0.98f)

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Player Seed (X: ${(playerSeedX * 100).toInt()}%, Y: ${(playerSeedY * 100).toInt()}%) — Seed for vision tracker initialization",
                    fontSize = 12.sp, color = CyberGreen, fontWeight = FontWeight.SemiBold
                )
                Slider(value = playerSeedX, onValueChange = { playerSeedX = it }, valueRange = 0.02f..0.98f)

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onUpdateCalibration(leftX, leftY, rightX, rightY, jumpX, jumpY, playerSeedX, playerSeedY) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_calibration_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary, contentColor = Color.Black)
                ) {
                    Text("Apply & Save Touch Coordinates", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
