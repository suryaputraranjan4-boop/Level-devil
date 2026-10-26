package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.LevelDevilApplication
import com.example.agent.PlayerAction
import com.example.input.ActionStep
import com.example.pipeline.DashboardState
import com.example.pipeline.PipelineSourceMode
import com.example.service.ScreenCaptureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val pipeline = LevelDevilApplication.instance.framePipeline

    val uiState: StateFlow<DashboardState> = pipeline.state

    private val _userFeedback = MutableStateFlow<String?>(null)
    val userFeedback: StateFlow<String?> = _userFeedback.asStateFlow()

    fun dismissFeedback() {
        _userFeedback.value = null
    }

    // --- Automation Lifecycle Controls ---

    fun startAutomation() {
        pipeline.startAutomation()
    }

    fun stopAutomation() {
        pipeline.stopAutomation()
    }

    fun pauseAutomation() {
        pipeline.pauseAutomation()
    }

    fun resumeAutomation() {
        pipeline.resumeAutomation()
    }

    fun toggleTraining() {
        pipeline.toggleTraining()
    }

    fun toggleInferenceOnly() {
        pipeline.toggleInferenceOnly()
        _userFeedback.value = if (pipeline.gameAgent.isInferenceOnly) {
            "Inference-Only mode enabled: Policy updates frozen."
        } else {
            "Learning mode enabled: Policy updates active."
        }
    }

    fun updateHyperparameters(lr: Float, gamma: Float, minEps: Float, decay: Float) {
        pipeline.updateHyperparameters(lr, gamma, minEps, decay)
        _userFeedback.value = "Updated RL parameters: α=$lr, γ=$gamma, ε_min=$minEps, decay=$decay"
    }

    fun setSourceMode(mode: PipelineSourceMode) {
        pipeline.setSourceMode(mode)
    }

    fun stepSimulationOnce() {
        pipeline.stepSimulationOnce()
    }

    fun setEpsilon(value: Float) {
        pipeline.gameAgent.setEpsilon(value)
    }

    fun resetPolicy() {
        pipeline.resetPolicy()
        _userFeedback.value = "Q-Learning policy and episode counters have been reset."
    }

    fun saveCheckpoint(name: String = "Manual Checkpoint") {
        viewModelScope.launch {
            val success = pipeline.saveCheckpoint(name)
            _userFeedback.value = if (success) "Saved checkpoint '$name' to database." else "Failed to save checkpoint."
        }
    }

    fun loadCheckpoint() {
        viewModelScope.launch {
            val success = pipeline.loadLatestCheckpoint()
            _userFeedback.value = if (success) "Successfully restored latest checkpoint." else "No saved checkpoint found in database."
        }
    }

    fun openAccessibilitySettings() {
        pipeline.inputController.openAccessibilitySettings()
    }

    fun runInputSelfTest(screenWidth: Int, screenHeight: Int) {
        pipeline.inputController.runInputSelfTest(screenWidth, screenHeight) { success, msg ->
            _userFeedback.value = msg
        }
    }

    fun updateCalibration(
        leftX: Float, leftY: Float, rightX: Float, rightY: Float,
        jumpX: Float, jumpY: Float, playerSeedX: Float, playerSeedY: Float
    ) {
        pipeline.inputController.calibration.apply {
            this.leftBtnX = leftX.coerceIn(0f, 1f)
            this.leftBtnY = leftY.coerceIn(0f, 1f)
            this.rightBtnX = rightX.coerceIn(0f, 1f)
            this.rightBtnY = rightY.coerceIn(0f, 1f)
            this.jumpBtnX = jumpX.coerceIn(0f, 1f)
            this.jumpBtnY = jumpY.coerceIn(0f, 1f)
            this.playerSeedX = playerSeedX.coerceIn(0f, 1f)
            this.playerSeedY = playerSeedY.coerceIn(0f, 1f)
        }
        pipeline.inputController.saveCalibration()
        pipeline.publishCalibration()
        _userFeedback.value = "Calibration saved on device, including player tracking seed."
    }

    fun updateTimingSettings(tapMs: Long, holdMs: Long, seqDelayMs: Long) {
        pipeline.updateTimings(tapMs, holdMs, seqDelayMs)
        _userFeedback.value = "Touch action timings updated: Tap=${tapMs}ms, Hold=${holdMs}ms."
    }

    fun executeTestSequence(screenWidth: Int, screenHeight: Int) {
        val steps = listOf(
            ActionStep(PlayerAction.MOVE_RIGHT, durationMs = pipeline.inputController.holdDurationMs, delayAfterMs = 100L),
            ActionStep(PlayerAction.JUMP, durationMs = pipeline.inputController.tapDurationMs, delayAfterMs = 120L),
            ActionStep(PlayerAction.RELEASE, durationMs = 0L, delayAfterMs = 50L)
        )
        pipeline.executeTestSequence(steps, screenWidth, screenHeight)
    }

    fun stopScreenCapture(context: Context) {
        val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        context.startService(stopIntent)
    }

    fun startScreenCapture(
        context: Context,
        resultCode: Int,
        resultData: Intent,
        width: Int,
        height: Int,
        density: Int
    ) {
        val startIntent = Intent(context, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenCaptureService.EXTRA_SCREEN_WIDTH, width)
            putExtra(ScreenCaptureService.EXTRA_SCREEN_HEIGHT, height)
            putExtra(ScreenCaptureService.EXTRA_SCREEN_DENSITY, density)
        }
        context.startForegroundService(startIntent)
        setSourceMode(PipelineSourceMode.LIVE_SCREEN)
    }
}
