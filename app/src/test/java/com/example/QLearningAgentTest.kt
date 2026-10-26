package com.example

import com.example.agent.EpisodeOutcome
import com.example.agent.PlayerAction
import com.example.agent.QLearningAgent
import com.example.agent.RLState
import com.example.agent.RewardFunction
import com.example.agent.Transition
import com.example.vision.GameObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QLearningAgentTest {

    @Test
    fun `test Q-learning Bellman update modifies action value`() {
        val agent = QLearningAgent(learningRate = 0.5f, discountFactor = 0.9f)
        val s0 = RLState(deltaXBucket = 2, deltaYBucket = 0, hazardBucket = 2, isGrounded = true, movingTrapActive = false)
        val s1 = RLState(deltaXBucket = 1, deltaYBucket = 0, hazardBucket = 2, isGrounded = true, movingTrapActive = false)

        val initialQ = agent.getQValues(s0)[PlayerAction.MOVE_RIGHT] ?: 0f
        assertEquals(0f, initialQ, 0.0001f)

        // Positive transition reward
        val transition = Transition(
            state = s0,
            action = PlayerAction.MOVE_RIGHT,
            actionDispatched = true,
            reward = 10f,
            rewardReason = "Step Forward",
            nextState = s1,
            outcome = EpisodeOutcome.IN_PROGRESS,
            isTerminal = false
        )

        agent.update(transition)

        val updatedQ = agent.getQValues(s0)[PlayerAction.MOVE_RIGHT] ?: 0f
        assertTrue("Q value should increase after positive reward", updatedQ > 0f)
    }

    @Test
    fun `test checkpoint serialization and restoration`() {
        val agent = QLearningAgent()
        val s0 = RLState(deltaXBucket = 3, deltaYBucket = 1, hazardBucket = 0, isGrounded = false, movingTrapActive = true)

        agent.update(
            Transition(
                state = s0,
                action = PlayerAction.JUMP,
                actionDispatched = true,
                reward = 25f,
                rewardReason = "Target reached",
                nextState = s0,
                outcome = EpisodeOutcome.WIN,
                isTerminal = true
            )
        )

        val serialized = agent.saveCheckpoint()
        assertTrue("Serialized checkpoint should contain qTable", serialized.contains("qTable"))

        val newAgent = QLearningAgent()
        val restored = newAgent.loadCheckpoint(serialized)
        assertTrue("Checkpoint restoration should succeed", restored)

        val restoredQ = newAgent.getQValues(s0)[PlayerAction.JUMP] ?: 0f
        assertTrue("Restored agent should preserve learned Q-value", restoredQ > 0f)
        assertEquals(1, newAgent.getMetrics().totalEpisodes)
    }

    @Test
    fun `in progress outcome cannot be counted as a terminal win or death`() {
        val agent = QLearningAgent(learningRate = 0.5f, discountFactor = 0.9f)
        val state = RLState(1, 0, 2, true, false)

        agent.update(
            Transition(
                state = state,
                action = PlayerAction.MOVE_RIGHT,
                actionDispatched = true,
                reward = 25f,
                rewardReason = "inconsistent terminal flag",
                nextState = state,
                outcome = EpisodeOutcome.IN_PROGRESS,
                isTerminal = true
            )
        )

        val metrics = agent.getMetrics()
        assertEquals("IN_PROGRESS must not become a verified episode", 0, metrics.totalEpisodes)
        assertEquals("IN_PROGRESS must not become a win", 0, metrics.wins)
        assertEquals("IN_PROGRESS must not become a death", 0, metrics.deaths)
    }

    @Test
    fun `simulation steps are not reported as Android touch dispatches`() {
        val agent = QLearningAgent()
        val state = RLState(0, 0, 2, true, false)

        agent.update(
            Transition(
                state = state,
                action = PlayerAction.MOVE_RIGHT,
                actionDispatched = true,
                reward = 1f,
                rewardReason = "synthetic step",
                nextState = state,
                outcome = EpisodeOutcome.IN_PROGRESS,
                isTerminal = false,
                isSimulation = true
            )
        )

        val metrics = agent.getMetrics()
        assertEquals("Simulation must not claim OS-dispatched gestures", 0L, metrics.actionsDispatched)
        assertEquals("Simulation must not claim rejected OS gestures", 0L, metrics.actionsRejected)
    }

    @Test
    fun `inference only records failures without changing policy Q values`() {
        val agent = QLearningAgent(isInferenceOnly = true)
        val state = RLState(0, 0, 2, true, false)
        val before = agent.getQValues(state)[PlayerAction.MOVE_RIGHT] ?: 0f

        agent.recordFailure(
            com.example.agent.FailureTrace(
                levelNumber = 1,
                deathPosition = android.graphics.PointF(0.2f, 0.6f),
                nearestHazardPosition = android.graphics.PointF(0.22f, 0.6f),
                recentActions = listOf(PlayerAction.MOVE_RIGHT),
                contributingAction = PlayerAction.MOVE_RIGHT,
                isSimulation = false
            )
        )

        val after = agent.getQValues(state)[PlayerAction.MOVE_RIGHT] ?: 0f
        assertEquals("Inference-only mode must freeze Q values", before, after, 0.0001f)
        assertEquals("Inference-only mode may retain failure telemetry", 1, agent.getRecentFailures().size)
    }

    @Test
    fun `real exit overlap is not counted as verified win but simulation can record simulated win`() {
        val rewardFn = RewardFunction()
        val exitOverlap = GameObservation(
            playerDetected = true,
            playerBox = android.graphics.RectF(0.82f, 0.55f, 0.90f, 0.68f),
            doorBox = android.graphics.RectF(0.82f, 0.54f, 0.93f, 0.75f),
            isVictoryScreen = true
        )

        val realResult = rewardFn.calculateRewardDetail(
            prevObs = exitOverlap,
            currObs = exitOverlap,
            actionDispatched = true,
            isSimulation = false
        )
        assertEquals("Door overlap alone is not proof of real game completion", EpisodeOutcome.UNKNOWN, realResult.outcome)

        val simulatedResult = rewardFn.calculateRewardDetail(
            prevObs = exitOverlap,
            currObs = exitOverlap,
            actionDispatched = true,
            isSimulation = true
        )
        assertEquals("Synthetic geometry may report a simulation-only win", EpisodeOutcome.WIN, simulatedResult.outcome)
    }

    @Test
    fun `test reward function rewards forward movement toward door`() {
        val rewardFn = RewardFunction()

        val obs1 = GameObservation(
            playerDetected = true,
            playerBox = android.graphics.RectF(0.2f, 0.6f, 0.25f, 0.65f),
            doorBox = android.graphics.RectF(0.85f, 0.6f, 0.90f, 0.70f)
        )

        val obs2 = GameObservation(
            playerDetected = true,
            playerBox = android.graphics.RectF(0.3f, 0.6f, 0.35f, 0.65f), // Moved closer
            doorBox = android.graphics.RectF(0.85f, 0.6f, 0.90f, 0.70f)
        )

        rewardFn.calculateReward(obs1, obs1)
        val reward = rewardFn.calculateReward(obs1, obs2)

        assertTrue("Moving closer to the door must yield positive reward", reward > 0f)
    }
}
