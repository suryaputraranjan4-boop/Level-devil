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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Screen dimensions
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.roundToPx() }

    // Calibration coordinates (default Level Devil landscape positions)
    var leftX by remember(state.leftBtnX) { mutableFloatStateOf(state.leftBtnX) }
    var leftY by remember(state.leftBtnY) { mutableFloatStateOf(state.leftBtnY) }
    var rightX by remember(state.rightBtnX) { mutableFloatStateOf(state.rightBtnX) }
    var rightY by remember(state.rightBtnY) { mutableFloatStateOf(state.rightBtnY) }
    var jumpX by remember(state.jumpBtnX) { mutableFloatStateOf(state.jumpBtnX) }
    var jumpY by remember(state.jumpBtnY) { mutableFloatStateOf(state.jumpBtnY) }
    var playerSeedX by remember(state.playerSeedX) { mutableFloatStateOf(state.playerSeedX) }
    var playerSeedY by remember(state.playerSeedY) { mutableFloatStateOf(state.playerSeedY) }
    var activeHandle by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Accessibility Service Status Card
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
                                text = if (state.isAccessibilityConnected) "Connected & Bound to Android OS" else "Service Not Enabled",
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
                        "The Level Devil AI Accessibility Service is currently connected. It can simulate precise taps on the screen via Android's dispatchGesture() API without root."
                    else
                        "Android security prevents standard apps from tapping on other apps. You must enable 'Level Devil AI Touch Dispatcher' in Android System Accessibility Settings to allow touch injection.",
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
                        Text("Run Self-Test", fontSize = 12.sp)
                    }
                }

                // Self-Test result banner
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
                                "✓ Self-Test Verified: Android OS successfully acknowledged gesture dispatch (${state.inputLatencyMs}ms latency)."
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

        // Virtual Touch Position Schematic & Calibration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "VIRTUAL TOUCH CALIBRATION PREVIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Calibrate where touch events will be dispatched on the game screen. Positions correspond to Level Devil's virtual arrow and jump buttons.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive schematic of phone screen
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
                                    activeHandle = points.indices.minByOrNull { (points[it] - touch).getDistance() } ?: -1
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

                        // Draw Left Button target
                        drawCircle(
                            color = AmberPrimary,
                            radius = 24f,
                            center = Offset(leftX * w, leftY * h)
                        )

                        // Draw Right Button target
                        drawCircle(
                            color = AmberPrimary,
                            radius = 24f,
                            center = Offset(rightX * w, rightY * h)
                        )

                        // Draw Jump Button target
                        drawCircle(color = CyberCyan, radius = 28f, center = Offset(jumpX * w, jumpY * h))
                        drawCircle(color = CyberGreen, radius = 20f, center = Offset(playerSeedX * w, playerSeedY * h))
                        drawCircle(color = Color.White, radius = 25f, center = Offset(playerSeedX * w, playerSeedY * h),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
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
                Slider(value = leftX, onValueChange = { leftX = it }, valueRange = 0.05f..0.45f)

                Spacer(modifier = Modifier.height(6.dp))

                Text("Right Arrow Button (X: ${(rightX * 100).toInt()}%, Y: ${(rightY * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = rightX, onValueChange = { rightX = it }, valueRange = 0.15f..0.55f)

                Spacer(modifier = Modifier.height(6.dp))

                Text("Jump Button (X: ${(jumpX * 100).toInt()}%, Y: ${(jumpY * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = jumpX, onValueChange = { jumpX = it }, valueRange = 0.60f..0.95f)

                Text(
                    "Player seed (X: ${(playerSeedX * 100).toInt()}%, Y: ${(playerSeedY * 100).toInt()}%) — drag the green marker onto the player before capture starts.",
                    fontSize = 12.sp, color = CyberGreen
                )
                Text("Drag any of the four markers to adjust both X and Y. Saving stores all coordinates on this device.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
