package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.LevelDevilApplication
import com.example.agent.PlayerAction
import com.example.input.InputStatus
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

    fun toggleTraining() {
        pipeline.toggleTraining()
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

    fun updateCalibration(leftX: Float, leftY: Float, rightX: Float, rightY: Float, jumpX: Float, jumpY: Float) {
        pipeline.inputController.calibration.apply {
            this.leftBtnX = leftX
            this.leftBtnY = leftY
            this.rightBtnX = rightX
            this.rightBtnY = rightY
            this.jumpBtnX = jumpX
            this.jumpBtnY = jumpY
        }
        _userFeedback.value = "Virtual button touch coordinates calibrated."
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
