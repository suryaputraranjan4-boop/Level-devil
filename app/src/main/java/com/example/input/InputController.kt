package com.example.input

import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.provider.Settings
import com.example.agent.PlayerAction
import com.example.service.LevelDevilAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class TouchCalibration(
    // Normalized screen coordinates (0.0 to 1.0)
    var leftBtnX: Float = 0.14f,
    var leftBtnY: Float = 0.85f,
    var rightBtnX: Float = 0.32f,
    var rightBtnY: Float = 0.85f,
    var jumpBtnX: Float = 0.86f,
    var jumpBtnY: Float = 0.85f,
    var playerSeedX: Float = 0.50f,
    var playerSeedY: Float = 0.55f
)

data class ActionStep(
    val action: PlayerAction,
    val durationMs: Long = 120L,
    val delayAfterMs: Long = 60L
)

enum class InputStatus {
    NOT_CONFIGURED,
    DISCONNECTED_DISABLED,
    READY_CONNECTED,
    TESTING_DISPATCH,
    TEST_VERIFIED,
    TEST_FAILED,
    CANCELED
}

class InputController(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    var calibration: TouchCalibration = TouchCalibration()

    // Configurable action timings
    var tapDurationMs: Long = 60L
    var holdDurationMs: Long = 220L
    var sequenceDelayMs: Long = 100L

    private val isDispatchingLock = AtomicBoolean(false)
    private var sequenceJob: Job? = null

    init {
        val prefs = context.getSharedPreferences("level_devil_calibration", Context.MODE_PRIVATE)
        calibration = TouchCalibration(
            leftBtnX = prefs.getFloat("left_x", 0.14f),
            leftBtnY = prefs.getFloat("left_y", 0.85f),
            rightBtnX = prefs.getFloat("right_x", 0.32f),
            rightBtnY = prefs.getFloat("right_y", 0.85f),
            jumpBtnX = prefs.getFloat("jump_x", 0.86f),
            jumpBtnY = prefs.getFloat("jump_y", 0.85f),
            playerSeedX = prefs.getFloat("player_seed_x", 0.50f),
            playerSeedY = prefs.getFloat("player_seed_y", 0.55f)
        )
        tapDurationMs = prefs.getLong("tap_duration_ms", 60L)
        holdDurationMs = prefs.getLong("hold_duration_ms", 220L)
        sequenceDelayMs = prefs.getLong("sequence_delay_ms", 100L)
    }

    fun saveCalibration() {
        context.getSharedPreferences("level_devil_calibration", Context.MODE_PRIVATE)
            .edit()
            .putFloat("left_x", calibration.leftBtnX).putFloat("left_y", calibration.leftBtnY)
            .putFloat("right_x", calibration.rightBtnX).putFloat("right_y", calibration.rightBtnY)
            .putFloat("jump_x", calibration.jumpBtnX).putFloat("jump_y", calibration.jumpBtnY)
            .putFloat("player_seed_x", calibration.playerSeedX).putFloat("player_seed_y", calibration.playerSeedY)
            .putLong("tap_duration_ms", tapDurationMs)
            .putLong("hold_duration_ms", holdDurationMs)
            .putLong("sequence_delay_ms", sequenceDelayMs)
            .apply()
    }

    fun updateTimingSettings(tapMs: Long, holdMs: Long, seqDelayMs: Long) {
        tapDurationMs = tapMs.coerceIn(20L, 500L)
        holdDurationMs = holdMs.coerceIn(50L, 1000L)
        sequenceDelayMs = seqDelayMs.coerceIn(20L, 500L)
        saveCalibration()
    }

    private val _status = MutableStateFlow(InputStatus.DISCONNECTED_DISABLED)
    val status: StateFlow<InputStatus> = _status.asStateFlow()

    private val _lastDispatchMessage = MutableStateFlow("Input engine initialized. Awaiting Accessibility authorization.")
    val lastDispatchMessage: StateFlow<String> = _lastDispatchMessage.asStateFlow()

    private val _lastDispatchLatencyMs = MutableStateFlow(0L)
    val lastDispatchLatencyMs: StateFlow<Long> = _lastDispatchLatencyMs.asStateFlow()

    private val _isDispatching = MutableStateFlow(false)
    val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

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
     * Converts normalized coordinates (0.0 to 1.0) into physical screen pixel values.
     */
    fun denormalize(normX: Float, normY: Float, screenWidth: Int, screenHeight: Int): PointF {
        val px = (normX.coerceIn(0f, 1f) * screenWidth).coerceIn(0f, screenWidth.toFloat())
        val py = (normY.coerceIn(0f, 1f) * screenHeight).coerceIn(0f, screenHeight.toFloat())
        return PointF(px, py)
    }

    /**
     * Dispatches action corresponding to the RL agent or automation choice.
     * Prevents overlapping/duplicate gestures using an atomic concurrency lock.
     */
    fun dispatchAction(
        action: PlayerAction,
        screenWidth: Int,
        screenHeight: Int,
        customHoldDurationMs: Long? = null,
        onDispatched: ((Boolean, Long) -> Unit)? = null
    ) {
        val service = LevelDevilAccessibilityService.instance
        if (service == null) {
            _status.value = InputStatus.DISCONNECTED_DISABLED
            _lastDispatchMessage.value = "Cannot dispatch: AccessibilityService not bound."
            onDispatched?.invoke(false, 0L)
            return
        }

        if (action == PlayerAction.WAIT || action == PlayerAction.RELEASE) {
            // Release/Wait: cancel any active gesture and clear dispatch state immediately
            service.cancelPendingGestures()
            _isDispatching.value = false
            _lastDispatchMessage.value = "Released all virtual buttons (Neutral)."
            onDispatched?.invoke(true, 0L)
            return
        }

        // Prevent duplicate overlapping stroke calls
        if (!isDispatchingLock.compareAndSet(false, true)) {
            _lastDispatchMessage.value = "Skipped duplicate dispatch: previous stroke still in flight."
            onDispatched?.invoke(false, 0L)
            return
        }

        _isDispatching.value = true
        val startNs = System.nanoTime()

        val leftPos = denormalize(calibration.leftBtnX, calibration.leftBtnY, screenWidth, screenHeight)
        val rightPos = denormalize(calibration.rightBtnX, calibration.rightBtnY, screenWidth, screenHeight)
        val jumpPos = denormalize(calibration.jumpBtnX, calibration.jumpBtnY, screenWidth, screenHeight)

        val duration = customHoldDurationMs ?: holdDurationMs

        val callback: (Boolean) -> Unit = { success ->
            val latencyMs = (System.nanoTime() - startNs) / 1_000_000
            _lastDispatchLatencyMs.value = latencyMs
            _isDispatching.value = false
            isDispatchingLock.set(false)

            if (success) {
                _lastDispatchMessage.value = "Dispatched ${action.displayName} (${latencyMs}ms)"
            } else {
                _lastDispatchMessage.value = "Gesture ${action.displayName} canceled by Android OS"
            }
            onDispatched?.invoke(success, latencyMs)
        }

        when (action) {
            PlayerAction.MOVE_LEFT -> {
                service.dispatchHold(leftPos.x, leftPos.y, durationMs = duration, onResult = callback)
            }
            PlayerAction.MOVE_RIGHT -> {
                service.dispatchHold(rightPos.x, rightPos.y, durationMs = duration, onResult = callback)
            }
            PlayerAction.JUMP -> {
                service.dispatchTap(jumpPos.x, jumpPos.y, durationMs = tapDurationMs, onResult = callback)
            }
            PlayerAction.LEFT_JUMP -> {
                service.dispatchDualTap(leftPos.x, leftPos.y, duration, jumpPos.x, jumpPos.y, tapDurationMs, onResult = callback)
            }
            PlayerAction.RIGHT_JUMP -> {
                service.dispatchDualTap(rightPos.x, rightPos.y, duration, jumpPos.x, jumpPos.y, tapDurationMs, onResult = callback)
            }
            PlayerAction.WAIT, PlayerAction.RELEASE -> {
                callback(true)
            }
        }
    }

    /**
     * Executes a configurable sequential chain of action steps with timed delays.
     */
    fun executeSequence(
        steps: List<ActionStep>,
        screenWidth: Int,
        screenHeight: Int,
        onStepDispatched: ((Int, ActionStep, Boolean) -> Unit)? = null,
        onSequenceCompleted: ((Boolean) -> Unit)? = null
    ) {
        cancelPendingActions()

        sequenceJob = scope.launch {
            var allSucceeded = true
            for ((index, step) in steps.withIndex()) {
                val service = LevelDevilAccessibilityService.instance
                if (service == null) {
                    allSucceeded = false
                    break
                }

                var stepSuccess = false
                val completionSignal = java.util.concurrent.CountDownLatch(1)

                dispatchAction(step.action, screenWidth, screenHeight, step.durationMs) { success, _ ->
                    stepSuccess = success
                    completionSignal.countDown()
                }

                // Wait with safety bounded timeout
                completionSignal.await(step.durationMs + 300L, java.util.concurrent.TimeUnit.MILLISECONDS)
                onStepDispatched?.invoke(index, step, stepSuccess)

                if (!stepSuccess) {
                    allSucceeded = false
                }

                if (step.delayAfterMs > 0) {
                    delay(step.delayAfterMs)
                }
            }
            onSequenceCompleted?.invoke(allSucceeded)
        }
    }

    /**
     * Safely cancels any pending automation actions or sequences immediately.
     */
    fun cancelPendingActions() {
        sequenceJob?.cancel()
        sequenceJob = null
        LevelDevilAccessibilityService.instance?.cancelPendingGestures()
        isDispatchingLock.set(false)
        _isDispatching.value = false
        _status.value = InputStatus.CANCELED
        _lastDispatchMessage.value = "All active gestures and automation actions canceled safely."
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

        service.dispatchTap(testX, testY, durationMs = tapDurationMs) { success ->
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
