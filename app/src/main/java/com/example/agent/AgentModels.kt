package com.example.agent

import android.graphics.PointF
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

enum class EpisodeOutcome(val label: String) {
    IN_PROGRESS("In Progress"),
    WIN("Level Complete (Exit Reached)"),
    DEATH("Verified Death (Collision/Fall)"),
    UNKNOWN("Uncertain / Observation Lost")
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

/**
 * Detailed transition record for evidence-based Reinforcement Learning.
 */
data class Transition(
    val state: RLState,
    val action: PlayerAction,
    val actionDispatched: Boolean, // True only if Android OS / accessibility dispatched the gesture
    val reward: Float,
    val rewardReason: String,
    val nextState: RLState,
    val outcome: EpisodeOutcome,
    val isTerminal: Boolean,
    val isSimulation: Boolean = false,
    val playerPosition: PointF? = null,
    val hazardPosition: PointF? = null
)

/**
 * Failure event trace recorded upon verified player death.
 */
data class FailureTrace(
    val timestamp: Long = System.currentTimeMillis(),
    val levelNumber: Int,
    val deathPosition: PointF,
    val nearestHazardPosition: PointF?,
    val recentActions: List<PlayerAction>,
    val contributingAction: PlayerAction?,
    val isSimulation: Boolean
)

data class RewardDetail(
    val reward: Float,
    val reason: String,
    val outcome: EpisodeOutcome
)

data class AgentMetrics(
    val totalSteps: Long = 0L,
    val totalEpisodes: Int = 0,
    val wins: Int = 0,
    val deaths: Int = 0,
    val unknowns: Int = 0,
    val cumulativeReward: Float = 0f,
    val currentEpsilon: Float = 1.0f,
    val winRate: Float = 0f,
    val qTableSize: Int = 0,
    val actionsSelected: Long = 0L,
    val actionsDispatched: Long = 0L,
    val actionsRejected: Long = 0L,
    val rollingAverageReward: Float = 0f
)

interface GameAgent {
    val name: String
    var learningRate: Float
    var discountFactor: Float
    var minEpsilon: Float
    var epsilonDecay: Float
    var isInferenceOnly: Boolean

    fun selectAction(observation: GameObservation, explore: Boolean = true): PlayerAction
    fun update(transition: Transition): Float
    fun recordFailure(trace: FailureTrace)
    fun getRecentFailures(): List<FailureTrace>
    fun getQValues(state: RLState): Map<PlayerAction, Float>
    fun saveCheckpoint(): String
    fun loadCheckpoint(serialized: String): Boolean
    fun resetPolicy()
    fun getMetrics(): AgentMetrics
    fun setEpsilon(value: Float)
}

/**
 * Evidence-based Reward Function using verified vision observations.
 * Distinguishes verified wins, verified deaths, distance progress, and uncertain outcomes.
 */
class RewardFunction {
    private var lastDistanceToDoor: Float? = null

    fun reset() {
        lastDistanceToDoor = null
    }

    fun calculateRewardDetail(
        prevObs: GameObservation,
        currObs: GameObservation,
        actionDispatched: Boolean,
        isSimulation: Boolean
    ): RewardDetail {
        // If Android OS canceled or rejected the gesture, do not award/penalize game outcome
        if (!actionDispatched) {
            return RewardDetail(
                reward = -0.01f,
                reason = "Gesture dispatch canceled/rejected by OS",
                outcome = EpisodeOutcome.IN_PROGRESS
            )
        }

        // 1. Verified Death
        if (currObs.isDeathScreen) {
            return RewardDetail(
                reward = if (isSimulation) -35.0f else -50.0f,
                reason = "Verified Death (Hazard / Trap Collision)",
                outcome = EpisodeOutcome.DEATH
            )
        }

        // A real victory is counted only when a completion screen is recognized.
        if (currObs.isVictoryScreen) {
            return RewardDetail(
                reward = if (isSimulation) 80.0f else 100.0f,
                reason = if (isSimulation) "Synthetic completion signal" else "Completion screen recognized",
                outcome = EpisodeOutcome.WIN
            )
        }

        // The synthetic environment knows its geometry; live door overlap alone is not proof
        // that the game registered a level completion.
        if (currObs.isExitOverlap) {
            return if (isSimulation) {
                RewardDetail(
                    reward = 80.0f,
                    reason = "Synthetic exit reached (simulation result only)",
                    outcome = EpisodeOutcome.WIN
                )
            } else {
                RewardDetail(
                    reward = 0.0f,
                    reason = "Exit overlap detected; real completion is not verified",
                    outcome = EpisodeOutcome.UNKNOWN
                )
            }
        }

        // 3. Uncertain / Observation Lost
        if (!currObs.playerDetected && !currObs.isDeathScreen && !currObs.isVictoryScreen) {
            return RewardDetail(
                reward = -0.05f,
                reason = "Player Observation Lost / Occluded",
                outcome = EpisodeOutcome.UNKNOWN
            )
        }

        // 4. In-progress distance progress
        var reward = -0.05f // Step living penalty to incentivize efficiency
        var reason = "Step Tick"

        val pBox = currObs.playerBox
        val dBox = currObs.doorBox
        if (pBox != null && dBox != null) {
            val currDist = hypot(dBox.centerX() - pBox.centerX(), dBox.centerY() - pBox.centerY())
            val lastDist = lastDistanceToDoor
            if (lastDist != null) {
                val delta = lastDist - currDist
                if (delta > 0.005f) {
                    val progressReward = (delta * 30.0f)
                    reward += progressReward
                    reason = "Progress Toward Exit (+${String.format("%.2f", progressReward)})"
                } else if (delta < -0.01f) {
                    reward -= 0.1f
                    reason = "Retreating Away from Exit"
                }
            }
            lastDistanceToDoor = currDist
        }

        // Dynamic hazard avoidance bonus
        if (prevObs.movingTrapNearby && !currObs.movingTrapNearby && !currObs.isDeathScreen) {
            reward += 1.5f
            reason = "Evaded Dynamic Moving Hazard"
        }

        return RewardDetail(
            reward = reward,
            reason = reason,
            outcome = EpisodeOutcome.IN_PROGRESS
        )
    }

    fun calculateReward(prevObs: GameObservation, currObs: GameObservation): Float {
        return calculateRewardDetail(prevObs, currObs, actionDispatched = true, isSimulation = false).reward
    }
}
