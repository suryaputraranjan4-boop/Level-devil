package com.example

import android.graphics.Bitmap
import com.example.vision.HeuristicColorEdgeDetector
import com.example.vision.ObjectType
import com.example.vision.SyntheticLevelDevilFrameGenerator
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisionDetectorTest {

    private val generator = SyntheticLevelDevilFrameGenerator(width = 320, height = 180)
    private val detector = HeuristicColorEdgeDetector(processWidth = 160, processHeight = 90)

    @Test
    fun `test detector locates player yellow box in synthetic frame`() {
        val frame = generator.generateFrame(playerXPercent = 0.20f, playerYPercent = 0.65f)
        val result = detector.processFrame(frame, null)

        assertTrue("Player should be detected", result.observation.playerDetected)
        assertNotNull("Player box should not be null", result.observation.playerBox)

        val playerBox = result.observation.playerBox!!
        assertTrue("Player X should be around 0.20", playerBox.centerX() in 0.15f..0.30f)
    }

    @Test
    fun `test detector locates exit door`() {
        val frame = generator.generateFrame()
        val result = detector.processFrame(frame, null)

        val doorObj = result.detectedObjects.find { it.type == ObjectType.DOOR }
        assertNotNull("Exit door object should be identified", doorObj)
        assertTrue("Door should be on the right side of the screen", doorObj!!.boundingBox.centerX() > 0.70f)
    }

    @Test
    fun `door overlap is not falsely labeled as verified completion screen`() {
        val frame = generator.generateFrame(
            playerXPercent = 0.82f,
            playerYPercent = 0.65f,
            includeMovingTrap = false
        )
        val result = detector.processFrame(frame, null)

        assertTrue("Door overlap may be tracked as a candidate exit event", result.observation.isExitOverlap)
        org.junit.Assert.assertFalse(
            "A player-door overlap alone must not be reported as a verified victory screen",
            result.observation.isVictoryScreen
        )
    }

    @Test
    fun `test temporal motion detection triggers on dynamic obstacle movement`() {
        // Frame 1 with trap at initial position
        val frame1 = generator.generateFrame(includeMovingTrap = true)
        detector.processFrame(frame1, null)

        // Frame 2 with trap offset shifted
        val frame2 = generator.generateFrame(includeMovingTrap = true)
        val result2 = detector.processFrame(frame2, frame1)

        val movingObj = result2.detectedObjects.find { it.type == ObjectType.MOVING_TRAP }
        assertNotNull("Moving trap should be detected via inter-frame temporal differencing", movingObj)
    }

    @Test
    fun `test death screen detection`() {
        val deathFrame = generator.generateFrame(showDeathScreen = true)
        val result = detector.processFrame(deathFrame, null)

        assertTrue("Death screen should be detected", result.observation.isDeathScreen)
    }
}
