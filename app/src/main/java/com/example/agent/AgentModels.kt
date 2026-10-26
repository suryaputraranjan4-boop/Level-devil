package com.example.agent

import com.example.vision.GameObservation
import kotlin.math.hypot

enum class PlayerAction(val displayName: String, val shortSymbol: String) {
    WAIT("Wait / Idle", "⧖"),
    MOVE_LEFT("Move Left", "←"),
    MOVE_RIGHT("Move Right", "→"),
    JUMP("Jump", "↑"),
    LEFT_JUMP("Left + Jump", "↖"),
    RIGHT_JUMP("Right + Jump", "↗"),
    RELEASE("Release All", "○")
}

data class RLState(
    val deltaXBucket: Int, // Relative X towards door: -4 to +4
    val deltaYBucket: Int, // Relative Y towards door: -2 to +2
    val hazardBucket: Int, // 0 = danger, 1 = caution, 2 = clear
    val isGrounded: Boolean,
    val movingTrapActive: Boolean
) {
    val key: String
        get() = "$deltaXBucket:$deltaYBucket:$hazardBucket:${if (isGrounded) 1 else 0}:${if (movingTrapActive) 1 else 0}"

    companion object {
        fun fromObservation(obs: GameObservation): RLState {
            val pBox = obs.playerBox
            val dBox = obs.doorBox

            val deltaX = if (pBox != null && dBox != null) {
                ((dBox.centerX() - pBox.centerX()) * 5).toInt().coerceIn(-4, 4)
            } else {
                0
            }

            val deltaY = if (pBox != null && dBox != null) {
                ((dBox.centerY() - pBox.centerY()) * 3).toInt().coerceIn(-2, 2)
            } else {
                0
            }

            val hazardBucket = when {
                obs.nearestSpikeDistance < 0.08f -> 0
                obs.nearestSpikeDistance < 0.20f -> 1
                else -> 2
            }

            return RLState(
                deltaXBucket = deltaX,
                deltaYBucket = deltaY,
                hazardBucket = hazardBucket,
                isGrounded = obs.isGrounded,
                movingTrapActive = obs.movingTrapNearby
            )
        }
    }
}

data class Transition(
    val state: RLState,
    val action: PlayerAction,
    val reward: Float,
    val nextState: RLState,
    val isTerminal: Boolean
)

data class AgentMetrics(
    val totalSteps: Long = 0L,
    val totalEpisodes: Int = 0,
    val wins: Int = 0,
    val deaths: Int = 0,
    val cumulativeReward: Float = 0f,
    val currentEpsilon: Float = 1.0f,
    val winRate: Float = 0f,
    val qTableSize: Int = 0
)

interface GameAgent {
    val name: String
    fun selectAction(observation: GameObservation, explore: Boolean = true): PlayerAction
    fun update(transition: Transition): Float
    fun getQValues(state: RLState): Map<PlayerAction, Float>
    fun saveCheckpoint(): String
    fun loadCheckpoint(serialized: String): Boolean
    fun resetPolicy()
    fun getMetrics(): AgentMetrics
    fun setEpsilon(value: Float)
}

class RewardFunction {
    private var lastDistanceToDoor: Float? = null

    fun reset() {
        lastDistanceToDoor = null
    }

    fun calculateReward(prevObs: GameObservation, currObs: GameObservation): Float {
        var reward = -0.05f // Small living cost / step penalty

        if (currObs.isDeathScreen) {
            return -50.0f
        }

        if (currObs.isVictoryScreen) {
            return 100.0f
        }

        // Distance progress reward
        val pBox = currObs.playerBox
        val dBox = currObs.doorBox
        if (pBox != null && dBox != null) {
            val currDist = hypot(dBox.centerX() - pBox.centerX(), dBox.centerY() - pBox.centerY())
            val lastDist = lastDistanceToDoor
            if (lastDist != null) {
                val delta = lastDist - currDist
                reward += (delta * 25.0f) // Positive if moving closer to door
            }
            lastDistanceToDoor = currDist
        }

        // Moving trap avoidance incentive
        if (prevObs.movingTrapNearby && !currObs.isDeathScreen) {
            reward += 1.0f
        }

        return reward
    }
}
