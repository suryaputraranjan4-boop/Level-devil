package com.example

import android.graphics.PointF
import android.graphics.RectF
import com.example.agent.EpisodeOutcome
import com.example.agent.FailureTrace
import com.example.agent.PlayerAction
import com.example.agent.QLearningAgent
import com.example.agent.RLState
import com.example.agent.RewardFunction
import com.example.agent.Transition
import com.example.vision.GameObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RLPart4ReinforcementLearningTest {

    @Test
    fun `test terminal transition does not bootstrap from next state`() {
        val agent = QLearningAgent(learningRate = 1.0f, discountFactor = 0.9f)
        val s0 = RLState(deltaXBucket = 0, deltaYBucket = 0, hazardBucket = 0, isGrounded = true, movingTrapActive = false)
        val s1 = RLState(deltaXBucket = 1, deltaYBucket = 0, hazardBucket = 2, isGrounded = true, movingTrapActive = false)

        // Seed s1 with a large positive Q-value (100.0)
        agent.update(
            Transition(
                state = s1,
                action = PlayerAction.MOVE_RIGHT,
                actionDispatched = true,
                reward = 100f,
                rewardReason = "Pre-seed",
                nextState = s1,
                outcome = EpisodeOutcome.WIN,
                isTerminal = true
            )
        )
        val s1MaxQ = agent.getQValues(s1)[PlayerAction.MOVE_RIGHT] ?: 0f
        assertEquals(100f, s1MaxQ, 0.001f)

        // Now execute a TERMINAL transition from s0 with reward = -50f
        // Correct Q-learning: Q(s0, action) <- 0 + 1.0 * (-50 - 0) = -50
        // Buggy bootstrapping Q-learning would calculate: -50 + 0.9 * 100 = +40 (WRONG!)
        agent.update(
            Transition(
                state = s0,
                action = PlayerAction.JUMP,
                actionDispatched = true,
                reward = -50f,
                rewardReason = "Hazard Collision",
                nextState = s1,
                outcome = EpisodeOutcome.DEATH,
                isTerminal = true
            )
        )

        val s0Q = agent.getQValues(s0)[PlayerAction.JUMP] ?: 0f
        assertEquals("Terminal state must not bootstrap from next state Q-value", -50f, s0Q, 0.001f)
    }

    @Test
    fun `test non-dispatched action is rejected and does not mutate Q-values`() {
        val agent = QLearningAgent(learningRate = 0.5f)
        val s0 = RLState(deltaXBucket = 2, deltaYBucket = 0, hazardBucket = 2, isGrounded = true, movingTrapActive = false)

        val initialQ = agent.getQValues(s0)[PlayerAction.MOVE_LEFT] ?: 0f
        assertEquals(0f, initialQ, 0.0001f)

        // Transition with actionDispatched = false (e.g. gesture canceled or OS dropped)
        val tdError = agent.update(
            Transition(
                state = s0,
                action = PlayerAction.MOVE_LEFT,
                actionDispatched = false,
                reward = -50f,
                rewardReason = "OS canceled stroke",
                nextState = s0,
                outcome = EpisodeOutcome.IN_PROGRESS,
                isTerminal = false
            )
        )

        assertEquals("TD error should be 0 for dropped dispatch", 0f, tdError, 0.0001f)
        val postQ = agent.getQValues(s0)[PlayerAction.MOVE_LEFT] ?: 0f
        assertEquals("Q-table must NOT be updated when action dispatch was rejected by OS", 0f, postQ, 0.0001f)
        assertEquals(1L, agent.getMetrics().actionsRejected)
        assertEquals(0L, agent.getMetrics().actionsDispatched)
    }

    @Test
    fun `test failure memory bank records death coordinates and suppresses repeat blunder`() {
        val agent = QLearningAgent(initialEpsilon = 1.0f) // 100% exploration
        val lethalCoord = PointF(0.42f, 0.65f)
        val hazardCoord = PointF(0.42f, 0.70f)

        val trace = FailureTrace(
            levelNumber = 1,
            deathPosition = lethalCoord,
            nearestHazardPosition = hazardCoord,
            recentActions = listOf(PlayerAction.MOVE_RIGHT, PlayerAction.MOVE_RIGHT),
            contributingAction = PlayerAction.MOVE_RIGHT,
            isSimulation = false
        )

        agent.recordFailure(trace)

        assertEquals(1, agent.getRecentFailures().size)
        assertEquals(PlayerAction.MOVE_RIGHT, agent.getRecentFailures().first().contributingAction)

        // Test action selection near lethal coordinates
        val obsNearHazard = GameObservation(
            playerDetected = true,
            playerBox = RectF(0.40f, 0.60f, 0.44f, 0.65f)
        )

        // In 30 sample explorations near the hazard, the lethal action (MOVE_RIGHT) should be filtered out
        for (i in 0 until 30) {
            val chosen = agent.selectAction(obsNearHazard, explore = true)
            assertNotEquals("Failure memory bank must filter lethal action during local exploration", PlayerAction.MOVE_RIGHT, chosen)
        }
    }

    @Test
    fun `test inference only mode freezes Q-table and epsilon decay`() {
        val agent = QLearningAgent(initialEpsilon = 0.5f, isInferenceOnly = true)
        val s0 = RLState(deltaXBucket = 1, deltaYBucket = 0, hazardBucket = 1, isGrounded = true, movingTrapActive = false)

        agent.update(
            Transition(
                state = s0,
                action = PlayerAction.JUMP,
                actionDispatched = true,
                reward = 100f,
                rewardReason = "Inference test",
                nextState = s0,
                outcome = EpisodeOutcome.WIN,
                isTerminal = true
            )
        )

        val qVal = agent.getQValues(s0)[PlayerAction.JUMP] ?: 0f
        assertEquals("Inference-only mode must not update Q-values", 0f, qVal, 0.0001f)
        assertEquals("Inference-only mode must not decay epsilon", 0.5f, agent.getMetrics().currentEpsilon, 0.0001f)
    }

    @Test
    fun `test evidence based reward separates verified win death and uncertain outcome`() {
        val rewardFn = RewardFunction()

        // 1. Verified Death
        val deathObs = GameObservation(
            playerDetected = true,
            playerBox = RectF(0.2f, 0.6f, 0.25f, 0.65f),
            isDeathScreen = true
        )
        val deathResult = rewardFn.calculateRewardDetail(
            prevObs = GameObservation(),
            currObs = deathObs,
            actionDispatched = true,
            isSimulation = false
        )
        assertEquals(EpisodeOutcome.DEATH, deathResult.outcome)
        assertTrue("Death must yield negative reward", deathResult.reward < 0f)

        // 2. Verified Victory
        val winObs = GameObservation(
            playerDetected = true,
            playerBox = RectF(0.85f, 0.6f, 0.90f, 0.65f),
            isVictoryScreen = true
        )
        val winResult = rewardFn.calculateRewardDetail(
            prevObs = GameObservation(),
            currObs = winObs,
            actionDispatched = true,
            isSimulation = false
        )
        assertEquals(EpisodeOutcome.WIN, winResult.outcome)
        assertTrue("Victory must yield high positive reward", winResult.reward >= 80f)

        // 3. Uncertain / Observation Lost
        val lostObs = GameObservation(
            playerDetected = false,
            playerBox = null,
            isDeathScreen = false,
            isVictoryScreen = false
        )
        val lostResult = rewardFn.calculateRewardDetail(
            prevObs = GameObservation(),
            currObs = lostObs,
            actionDispatched = true,
            isSimulation = false
        )
        assertEquals(EpisodeOutcome.UNKNOWN, lostResult.outcome)
    }

    @Test
    fun `test checkpoint serialization includes failure memory`() {
        val agent = QLearningAgent()
        agent.recordFailure(
            FailureTrace(
                levelNumber = 2,
                deathPosition = PointF(0.55f, 0.80f),
                nearestHazardPosition = PointF(0.55f, 0.85f),
                recentActions = listOf(PlayerAction.MOVE_LEFT),
                contributingAction = PlayerAction.MOVE_LEFT,
                isSimulation = false
            )
        )

        val json = agent.saveCheckpoint()
        assertTrue("Checkpoint must include failure traces", json.contains("failureTraces"))

        val restoredAgent = QLearningAgent()
        val success = restoredAgent.loadCheckpoint(json)
        assertTrue(success)
        assertEquals(1, restoredAgent.getRecentFailures().size)
        assertEquals(PlayerAction.MOVE_LEFT, restoredAgent.getRecentFailures().first().contributingAction)
    }
}
