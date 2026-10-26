package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.agent.PlayerAction
import com.example.input.ActionStep
import com.example.input.InputController
import com.example.input.InputStatus
import com.example.pipeline.AutomationState
import com.example.pipeline.PipelineSourceMode
import com.example.service.LevelDevilAccessibilityService
import com.example.vision.HeuristicColorEdgeDetector
import com.example.vision.MovementDirection
import com.example.vision.SyntheticLevelDevilFrameGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TouchControlTest {

    private lateinit var context: Context
    private lateinit var inputController: InputController

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        inputController = InputController(context)
    }

    @Test
    fun `test coordinate normalization across various screen resolutions`() {
        // Standard phone: 1080 x 2400
        val p1 = inputController.denormalize(0.14f, 0.85f, 1080, 2400)
        assertEquals(151.2f, p1.x, 0.5f)
        assertEquals(2040.0f, p1.y, 0.5f)

        // Compact phone: 720 x 1280
        val p2 = inputController.denormalize(0.50f, 0.50f, 720, 1280)
        assertEquals(360.0f, p2.x, 0.5f)
        assertEquals(640.0f, p2.y, 0.5f)

        // Out of bounds coordinate clamping test
        val clamped = inputController.denormalize(-0.2f, 1.5f, 1000, 2000)
        assertEquals(0f, clamped.x, 0.01f)
        assertEquals(2000f, clamped.y, 0.01f)
    }

    @Test
    fun `test input failure and graceful reporting when AccessibilityService is unavailable`() {
        // AccessibilityService is not bound in JVM test environment
        inputController.checkStatus()
        assertEquals(InputStatus.DISCONNECTED_DISABLED, inputController.status.value)

        var dispatchSuccess = true
        inputController.dispatchAction(PlayerAction.JUMP, 1080, 2400) { success, _ ->
            dispatchSuccess = success
        }

        assertFalse("Dispatch must fail gracefully when service is not bound", dispatchSuccess)
        assertTrue(inputController.lastDispatchMessage.value.contains("AccessibilityService not bound"))
    }

    @Test
    fun `test action cancellation clears dispatching state`() {
        inputController.cancelPendingActions()
        assertEquals(InputStatus.CANCELED, inputController.status.value)
        assertFalse(inputController.isDispatching.value)
        assertTrue(inputController.lastDispatchMessage.value.contains("canceled safely"))
    }

    @Test
    fun `test persistent calibration and timing configuration storage`() {
        inputController.calibration.leftBtnX = 0.20f
        inputController.calibration.leftBtnY = 0.80f
        inputController.calibration.playerSeedX = 0.45f
        inputController.updateTimingSettings(tapMs = 75L, holdMs = 280L, seqDelayMs = 90L)

        // Read fresh instance from SharedPreferences
        val freshController = InputController(context)
        assertEquals(0.20f, freshController.calibration.leftBtnX, 0.001f)
        assertEquals(0.80f, freshController.calibration.leftBtnY, 0.001f)
        assertEquals(0.45f, freshController.calibration.playerSeedX, 0.001f)
        assertEquals(75L, freshController.tapDurationMs)
        assertEquals(280L, freshController.holdDurationMs)
        assertEquals(90L, freshController.sequenceDelayMs)
    }

    @Test
    fun `test seed-guided continuous vision tracking and velocity direction estimation`() {
        val generator = SyntheticLevelDevilFrameGenerator(width = 320, height = 180)
        val detector = HeuristicColorEdgeDetector(processWidth = 160, processHeight = 90)

        // Step 1: Initial frame, seed provided near initial player pos (0.20, 0.65)
        val frame1 = generator.generateFrame(playerXPercent = 0.20f, playerYPercent = 0.65f)
        val result1 = detector.processFrame(frame1, null, playerSeedX = 0.20f, playerSeedY = 0.65f)

        assertTrue("Player should be detected using seed", result1.observation.playerDetected)
        assertNotNull(result1.observation.playerBox)
        assertEquals(MovementDirection.STATIONARY, result1.observation.movementDirection)

        // Step 2: Consecutive frame with player displaced to the right (x: 0.24)
        val frame2 = generator.generateFrame(playerXPercent = 0.24f, playerYPercent = 0.65f)
        val result2 = detector.processFrame(frame2, frame1)

        assertTrue("Player should be continuously tracked in second frame", result2.observation.playerDetected)
        val box2 = result2.observation.playerBox!!
        assertTrue("Player X coordinate should increase", box2.centerX() > 0.21f)
        assertEquals(MovementDirection.MOVING_RIGHT, result2.observation.movementDirection)
        assertTrue("Player velocity X should be positive", result2.observation.playerVelocityX > 0f)

        // Step 3: Obstacle lookahead should detect next spike or trap ahead towards door
        assertNotNull("Next obstacle ahead must be identified", result2.observation.nextObstacle)
    }

    @Test
    fun `test vision detector pauses and reports error when player is lost`() {
        val generator = SyntheticLevelDevilFrameGenerator(width = 320, height = 180)
        val detector = HeuristicColorEdgeDetector(processWidth = 160, processHeight = 90)

        // Death screen frame has no yellow player cube
        val deathFrame = generator.generateFrame(showDeathScreen = true)
        val result = detector.processFrame(deathFrame, null)

        assertFalse("Player must not be detected when absent", result.observation.playerDetected)
        assertNull("Player box must be null when lost", result.observation.playerBox)
        assertTrue(result.observation.trackingStatusMessage.contains("Lost") || result.observation.trackingStatusMessage.contains("Obstructed"))
    }

    @Test
    fun `test automation lifecycle state transitions`() {
        val app = LevelDevilApplication.instance
        val pipeline = app.framePipeline

        // Initially STOPPED — never starts automatically on launch
        assertEquals(AutomationState.STOPPED, pipeline.state.value.automationState)

        // When starting without AccessibilityService connected, enters ERROR_PAUSED
        pipeline.startAutomation()
        assertEquals(AutomationState.ERROR_PAUSED, pipeline.state.value.automationState)

        // Calling stopAutomation resets safely to STOPPED
        pipeline.stopAutomation("Test stopped")
        assertEquals(AutomationState.STOPPED, pipeline.state.value.automationState)
        assertEquals(PlayerAction.WAIT, pipeline.state.value.currentAction)

        // Calling pauseAutomation transitions to PAUSED
        pipeline.pauseAutomation()
        assertEquals(AutomationState.PAUSED, pipeline.state.value.automationState)
    }
}
