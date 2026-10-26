package com.example.vision

import android.graphics.Bitmap
import android.graphics.RectF

enum class ObjectType {
    PLAYER,
    PLATFORM,
    SPIKE,
    DOOR,
    MOVING_TRAP,
    UNKNOWN
}

enum class MovementDirection(val label: String, val symbol: String) {
    STATIONARY("Stationary", "●"),
    MOVING_LEFT("Moving Left", "←"),
    MOVING_RIGHT("Moving Right", "→"),
    JUMPING("Jumping Up", "↑"),
    FALLING("Falling Down", "↓")
}

data class DetectedObject(
    val type: ObjectType,
    val boundingBox: RectF, // Normalized 0.0 to 1.0 (left, top, right, bottom)
    val confidence: Float,
    val label: String,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f
)

data class GameObservation(
    val timestampMs: Long = System.currentTimeMillis(),
    val playerDetected: Boolean = false,
    val playerBox: RectF? = null,
    val doorBox: RectF? = null,
    val nearestSpikeDistance: Float = 1.0f, // 0.0 = touching, 1.0 = far
    val nearestPlatformDistanceBelow: Float = 1.0f,
    val isGrounded: Boolean = false,
    val movingTrapNearby: Boolean = false,
    val isDeathScreen: Boolean = false,
    val isVictoryScreen: Boolean = false,
    val detectedObjects: List<DetectedObject> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val playerVelocityX: Float = 0f,
    val playerVelocityY: Float = 0f,
    val movementDirection: MovementDirection = MovementDirection.STATIONARY,
    val nextObstacle: DetectedObject? = null,
    val nextPlatform: DetectedObject? = null,
    val trackingStatusMessage: String = "Idle"
)

data class DetectionResult(
    val observation: GameObservation,
    val detectedObjects: List<DetectedObject>,
    val visionLatencyMs: Long,
    val previewBitmap: Bitmap? = null
)

interface LevelDevilVisionDetector {
    val detectorName: String
    val description: String

    /**
     * Processes a game frame and detects game elements with optional player seed tracking.
     * @param currentFrame The freshly captured game frame bitmap.
     * @param previousFrame Optional prior frame used for temporal motion differential detection.
     * @param playerSeedX Optional initial normalized X seed coordinate to initialize player tracking.
     * @param playerSeedY Optional initial normalized Y seed coordinate to initialize player tracking.
     * @param resetTracking When true, forces the tracker to re-initialize using the seed.
     */
    fun processFrame(
        currentFrame: Bitmap,
        previousFrame: Bitmap?,
        playerSeedX: Float? = null,
        playerSeedY: Float? = null,
        resetTracking: Boolean = false
    ): DetectionResult

    /**
     * Clears tracked player state and velocity.
     */
    fun resetTracker()
}
