package com.example.input

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.agent.PlayerAction
import com.example.service.LevelDevilAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TouchCalibration(
    // Normalized screen coordinates (0.0 to 1.0)
    var leftBtnX: Float = 0.14f,
    var leftBtnY: Float = 0.85f,
    var rightBtnX: Float = 0.32f,
    var rightBtnY: Float = 0.85f,
    var jumpBtnX: Float = 0.86f,
    var jumpBtnY: Float = 0.85f
)

enum class InputStatus {
    NOT_CONFIGURED,
    DISCONNECTED_DISABLED,
    READY_CONNECTED,
    TESTING_DISPATCH,
    TEST_VERIFIED,
    TEST_FAILED
}

class InputController(
    private val context: Context
) {
    var calibration: TouchCalibration = TouchCalibration()

    private val _status = MutableStateFlow(InputStatus.DISCONNECTED_DISABLED)
    val status: StateFlow<InputStatus> = _status.asStateFlow()

    private val _lastDispatchMessage = MutableStateFlow("Input engine initialized. Awaiting Accessibility authorization.")
    val lastDispatchMessage: StateFlow<String> = _lastDispatchMessage.asStateFlow()

    private val _lastDispatchLatencyMs = MutableStateFlow(0L)
    val lastDispatchLatencyMs: StateFlow<Long> = _lastDispatchLatencyMs.asStateFlow()

    fun checkStatus() {
        val service = LevelDevilAccessibilityService.instance
        if (service != null) {
            if (_status.value != InputStatus.TEST_VERIFIED && _status.value != InputStatus.TESTING_DISPATCH) {
                _status.value = InputStatus.READY_CONNECTED
                _lastDispatchMessage.value = "Accessibility Service active. Ready to dispatch gestures."
            }
        } else {
            _status.value = InputStatus.DISCONNECTED_DISABLED
            _lastDispatchMessage.value = "Accessibility Service is not enabled. Taps cannot be sent to Level Devil."
        }
    }

    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _lastDispatchMessage.value = "Could not open settings: ${e.localizedMessage}"
        }
    }

    /**
     * Dispatches action corresponding to the RL agent choice.
     */
    fun dispatchAction(
        action: PlayerAction,
        screenWidth: Int,
        screenHeight: Int,
        onDispatched: ((Boolean, Long) -> Unit)? = null
    ) {
        val service = LevelDevilAccessibilityService.instance
        if (service == null) {
            _status.value = InputStatus.DISCONNECTED_DISABLED
            _lastDispatchMessage.value = "Cannot dispatch: AccessibilityService not bound."
            onDispatched?.invoke(false, 0L)
            return
        }

        val startNs = System.nanoTime()

        val leftPx = calibration.leftBtnX * screenWidth
        val leftPy = calibration.leftBtnY * screenHeight
        val rightPx = calibration.rightBtnX * screenWidth
        val rightPy = calibration.rightBtnY * screenHeight
        val jumpPx = calibration.jumpBtnX * screenWidth
        val jumpPy = calibration.jumpBtnY * screenHeight

        when (action) {
            PlayerAction.MOVE_LEFT -> {
                service.dispatchTap(leftPx, leftPy, durationMs = 120L) { success ->
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000
                    _lastDispatchLatencyMs.value = latencyMs
                    onDispatched?.invoke(success, latencyMs)
                }
            }
            PlayerAction.MOVE_RIGHT -> {
                service.dispatchTap(rightPx, rightPy, durationMs = 120L) { success ->
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000
                    _lastDispatchLatencyMs.value = latencyMs
                    onDispatched?.invoke(success, latencyMs)
                }
            }
            PlayerAction.JUMP -> {
                service.dispatchTap(jumpPx, jumpPy, durationMs = 80L) { success ->
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000
                    _lastDispatchLatencyMs.value = latencyMs
                    onDispatched?.invoke(success, latencyMs)
                }
            }
            PlayerAction.LEFT_JUMP -> {
                service.dispatchDualTap(leftPx, leftPy, 140L, jumpPx, jumpPy, 90L) { success ->
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000
                    _lastDispatchLatencyMs.value = latencyMs
                    onDispatched?.invoke(success, latencyMs)
                }
            }
            PlayerAction.RIGHT_JUMP -> {
                service.dispatchDualTap(rightPx, rightPy, 140L, jumpPx, jumpPy, 90L) { success ->
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000
                    _lastDispatchLatencyMs.value = latencyMs
                    onDispatched?.invoke(success, latencyMs)
                }
            }
            PlayerAction.WAIT, PlayerAction.RELEASE -> {
                // No touch sent, simply record latency
                val latencyMs = (System.nanoTime() - startNs) / 1_000_000
                _lastDispatchLatencyMs.value = latencyMs
                onDispatched?.invoke(true, latencyMs)
            }
        }
    }

    /**
     * Executes a physical test tap on the screen and awaits actual OS confirmation.
     * Required per guideline: "Never claim that actual game input works until tested."
     */
    fun runInputSelfTest(screenWidth: Int, screenHeight: Int, onComplete: (Boolean, String) -> Unit) {
        val service = LevelDevilAccessibilityService.instance
        if (service == null) {
            _status.value = InputStatus.DISCONNECTED_DISABLED
            val msg = "Self-Test FAILED: AccessibilityService is NOT enabled in Android System Settings."
            _lastDispatchMessage.value = msg
            onComplete(false, msg)
            return
        }

        _status.value = InputStatus.TESTING_DISPATCH
        _lastDispatchMessage.value = "Sending test gesture to Android GestureDescription dispatch pipeline..."

        val testX = screenWidth * 0.5f
        val testY = screenHeight * 0.5f
        val startNs = System.nanoTime()

        service.dispatchTap(testX, testY, durationMs = 50L) { success ->
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000
            _lastDispatchLatencyMs.value = latencyMs

            if (success) {
                _status.value = InputStatus.TEST_VERIFIED
                val msg = "Self-Test VERIFIED! Android OS confirmed dispatchGesture completion in ${latencyMs}ms."
                _lastDispatchMessage.value = msg
                onComplete(true, msg)
            } else {
                _status.value = InputStatus.TEST_FAILED
                val msg = "Self-Test FAILED: Android OS canceled or rejected the gesture dispatch."
                _lastDispatchMessage.value = msg
                onComplete(false, msg)
            }
        }
    }
}
