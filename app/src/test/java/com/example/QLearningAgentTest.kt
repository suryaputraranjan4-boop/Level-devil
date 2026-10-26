package com.example

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

        // Give a positive transition reward
        val transition = Transition(
            state = s0,
            action = PlayerAction.MOVE_RIGHT,
            reward = 10f,
            nextState = s1,
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
                reward = 25f,
                nextState = s0,
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
