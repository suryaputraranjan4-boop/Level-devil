package com.example.agent

import com.example.vision.GameObservation
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.random.Random

/**
 * Tabular Q-Learning Agent with Tile/Bucket Discretization, Failure Memory & Evidence-Based Updates.
 *
 * Implements standard off-policy TD control:
 * For terminal states: Q(s, a) <- Q(s, a) + alpha * [ r - Q(s, a) ]
 * For non-terminal states: Q(s, a) <- Q(s, a) + alpha * [ r + gamma * max_a' Q(s', a') - Q(s, a) ]
 *
 * Key Part 4 Enhancements:
 * - Differentiates action dispatch status: non-dispatched or rejected gestures are NOT updated as physical action failures.
 * - Failure Memory Bank: tracks lethal coordinates, contributing actions, and avoids repeating identical blunder sequences.
 * - Terminal states do NOT bootstrap from a nonexistent next state.
 * - Inference-Only Mode toggle: freezes policy weights and epsilon while evaluating.
 * - Rolling average reward tracker and granular action telemetry (selected, dispatched, rejected).
 * - Full JSON checkpoint persistence with Room integration.
 */
class QLearningAgent(
    override var learningRate: Float = 0.15f,
    override var discountFactor: Float = 0.95f,
    var initialEpsilon: Float = 1.0f,
    override var minEpsilon: Float = 0.05f,
    override var epsilonDecay: Float = 0.995f,
    override var isInferenceOnly: Boolean = false
) : GameAgent {

    override val name: String = "Tabular Q-Learning with Failure Memory"

    private val actions = PlayerAction.values()
    private val actionCount = actions.size

    // Concurrent Q-Table: state key -> FloatArray of Q-values
    private val qTable = ConcurrentHashMap<String, FloatArray>()

    // Failure Memory Bank (stores up to 50 recent lethal events)
    private val failureTraces = CopyOnWriteArrayList<FailureTrace>()

    private var epsilon = initialEpsilon
    private var totalSteps = 0L
    private var totalEpisodes = 0
    private var wins = 0
    private var deaths = 0
    private var unknowns = 0
    private var cumulativeReward = 0f
    private var actionsSelected = 0L
    private var actionsDispatched = 0L
    private var actionsRejected = 0L

    // Rolling reward window
    private val recentRewards = ArrayDeque<Float>(30)

    override fun selectAction(observation: GameObservation, explore: Boolean): PlayerAction {
        actionsSelected++
        val state = RLState.fromObservation(observation)
        val qValues = getOrCreateQValues(state.key)

        val shouldExplore = explore && !isInferenceOnly && (Random.nextFloat() < epsilon)

        if (shouldExplore) {
            // Check if any action in this state was recently lethal
            val lethalActions = getLethalActionsForState(observation)
            val candidateActions = if (lethalActions.isNotEmpty() && lethalActions.size < actionCount) {
                actions.filter { it !in lethalActions }
            } else {
                actions.toList()
            }
            return candidateActions[Random.nextInt(candidateActions.size)]
        }

        // Greedy exploitation: argmax_a Q(s, a)
        var maxQ = Float.NEGATIVE_INFINITY
        var bestActionIdx = 0
        for (i in 0 until actionCount) {
            val q = qValues[i]
            if (q > maxQ) {
                maxQ = q
                bestActionIdx = i
            }
        }
        return actions[bestActionIdx]
    }

    override fun update(transition: Transition): Float {
        // Track dispatch status
        if (transition.actionDispatched) {
            actionsDispatched++
        } else {
            actionsRejected++
            // CRITICAL: Do not update Q-value if Android rejected or canceled the touch
            return 0f
        }

        if (isInferenceOnly) {
            return 0f
        }

        totalSteps++
        cumulativeReward += transition.reward

        // Track rolling reward
        synchronized(recentRewards) {
            if (recentRewards.size >= 30) {
                recentRewards.removeFirst()
            }
            recentRewards.addLast(transition.reward)
        }

        val stateKey = transition.state.key
        val qValues = getOrCreateQValues(stateKey)
        val actionIdx = transition.action.ordinal
        val currentQ = qValues[actionIdx]

        // Q-Learning Bellman Update
        // For terminal states, NO bootstrapping from next state!
        val tdTarget = if (transition.isTerminal) {
            transition.reward
        } else {
            val nextQValues = getOrCreateQValues(transition.nextState.key)
            var maxNextQ = Float.NEGATIVE_INFINITY
            for (q in nextQValues) {
                if (q > maxNextQ) maxNextQ = q
            }
            if (maxNextQ == Float.NEGATIVE_INFINITY) maxNextQ = 0f
            transition.reward + discountFactor * maxNextQ
        }

        val tdError = tdTarget - currentQ
        qValues[actionIdx] = currentQ + learningRate * tdError

        // Handle terminal episode outcomes
        if (transition.isTerminal) {
            totalEpisodes++
            when (transition.outcome) {
                EpisodeOutcome.WIN -> wins++
                EpisodeOutcome.DEATH -> deaths++
                EpisodeOutcome.UNKNOWN -> unknowns++
                else -> {
                    if (transition.reward > 0) wins++ else deaths++
                }
            }
            // Decay epsilon after completing an episode
            epsilon = max(minEpsilon, epsilon * epsilonDecay)
        }

        return tdError
    }

    override fun recordFailure(trace: FailureTrace) {
        if (failureTraces.size >= 50) {
            failureTraces.removeAt(0)
        }
        failureTraces.add(trace)

        // Directly apply negative bias to contributing blunder action if known
        trace.contributingAction?.let { blunder ->
            val pX = trace.deathPosition.x
            val pY = trace.deathPosition.y
            val dX = if (trace.nearestHazardPosition != null) {
                ((trace.nearestHazardPosition.x - pX) * 5).toInt().coerceIn(-4, 4)
            } else 0
            val dY = if (trace.nearestHazardPosition != null) {
                ((trace.nearestHazardPosition.y - pY) * 3).toInt().coerceIn(-2, 2)
            } else 0
            val lethalState = RLState(
                deltaXBucket = dX,
                deltaYBucket = dY,
                hazardBucket = 0,
                isGrounded = true,
                movingTrapActive = true
            )
            val qVals = getOrCreateQValues(lethalState.key)
            qVals[blunder.ordinal] = (qVals[blunder.ordinal] - 15.0f).coerceAtLeast(-100f)
        }
    }

    override fun getRecentFailures(): List<FailureTrace> {
        return failureTraces.toList()
    }

    private fun getLethalActionsForState(obs: GameObservation): Set<PlayerAction> {
        val pBox = obs.playerBox ?: return emptySet()
        val lethal = mutableSetOf<PlayerAction>()
        for (f in failureTraces) {
            val dist = kotlin.math.hypot(f.deathPosition.x - pBox.centerX(), f.deathPosition.y - pBox.centerY())
            if (dist < 0.12f && f.contributingAction != null) {
                lethal.add(f.contributingAction)
            }
        }
        return lethal
    }

    override fun getQValues(state: RLState): Map<PlayerAction, Float> {
        val values = getOrCreateQValues(state.key)
        val map = mutableMapOf<PlayerAction, Float>()
        for (i in actions.indices) {
            map[actions[i]] = values[i]
        }
        return map
    }

    override fun saveCheckpoint(): String {
        val root = JSONObject()
        root.put("totalSteps", totalSteps)
        root.put("totalEpisodes", totalEpisodes)
        root.put("wins", wins)
        root.put("deaths", deaths)
        root.put("unknowns", unknowns)
        root.put("actionsSelected", actionsSelected)
        root.put("actionsDispatched", actionsDispatched)
        root.put("actionsRejected", actionsRejected)
        root.put("cumulativeReward", cumulativeReward.toDouble())
        root.put("epsilon", epsilon.toDouble())
        root.put("learningRate", learningRate.toDouble())
        root.put("discountFactor", discountFactor.toDouble())
        root.put("minEpsilon", minEpsilon.toDouble())
        root.put("epsilonDecay", epsilonDecay.toDouble())
        root.put("isInferenceOnly", isInferenceOnly)

        val tableObj = JSONObject()
        for ((key, values) in qTable) {
            val arr = JSONArray()
            for (v in values) arr.put(v.toDouble())
            tableObj.put(key, arr)
        }
        root.put("qTable", tableObj)

        val failuresArr = JSONArray()
        for (f in failureTraces) {
            val fObj = JSONObject()
            fObj.put("timestamp", f.timestamp)
            fObj.put("levelNumber", f.levelNumber)
            fObj.put("deathX", f.deathPosition.x.toDouble())
            fObj.put("deathY", f.deathPosition.y.toDouble())
            fObj.put("hazardX", f.nearestHazardPosition?.x?.toDouble() ?: -1.0)
            fObj.put("hazardY", f.nearestHazardPosition?.y?.toDouble() ?: -1.0)
            fObj.put("contributingAction", f.contributingAction?.name ?: "")
            fObj.put("isSimulation", f.isSimulation)
            failuresArr.put(fObj)
        }
        root.put("failureTraces", failuresArr)

        return root.toString()
    }

    override fun loadCheckpoint(serialized: String): Boolean {
        return try {
            val root = JSONObject(serialized)
            totalSteps = root.optLong("totalSteps", 0L)
            totalEpisodes = root.optInt("totalEpisodes", 0)
            wins = root.optInt("wins", 0)
            deaths = root.optInt("deaths", 0)
            unknowns = root.optInt("unknowns", 0)
            actionsSelected = root.optLong("actionsSelected", 0L)
            actionsDispatched = root.optLong("actionsDispatched", 0L)
            actionsRejected = root.optLong("actionsRejected", 0L)
            cumulativeReward = root.optDouble("cumulativeReward", 0.0).toFloat()
            epsilon = root.optDouble("epsilon", 0.1).toFloat()
            learningRate = root.optDouble("learningRate", 0.15).toFloat()
            discountFactor = root.optDouble("discountFactor", 0.95).toFloat()
            minEpsilon = root.optDouble("minEpsilon", 0.05).toFloat()
            epsilonDecay = root.optDouble("epsilonDecay", 0.995).toFloat()
            isInferenceOnly = root.optBoolean("isInferenceOnly", false)

            qTable.clear()
            val tableObj = root.optJSONObject("qTable")
            if (tableObj != null) {
                val keys = tableObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = tableObj.getJSONArray(k)
                    val values = FloatArray(actionCount)
                    for (i in 0 until minOf(actionCount, arr.length())) {
                        values[i] = arr.getDouble(i).toFloat()
                    }
                    qTable[k] = values
                }
            }

            failureTraces.clear()
            val failuresArr = root.optJSONArray("failureTraces")
            if (failuresArr != null) {
                for (i in 0 until failuresArr.length()) {
                    val fObj = failuresArr.getJSONObject(i)
                    val hazardX = fObj.optDouble("hazardX", -1.0).toFloat()
                    val hazardY = fObj.optDouble("hazardY", -1.0).toFloat()
                    val contribActionName = fObj.optString("contributingAction", "")
                    val contribAction = if (contribActionName.isNotEmpty()) {
                        try { PlayerAction.valueOf(contribActionName) } catch (e: Exception) { null }
                    } else null

                    failureTraces.add(
                        FailureTrace(
                            timestamp = fObj.optLong("timestamp", System.currentTimeMillis()),
                            levelNumber = fObj.optInt("levelNumber", 1),
                            deathPosition = android.graphics.PointF(
                                fObj.getDouble("deathX").toFloat(),
                                fObj.getDouble("deathY").toFloat()
                            ),
                            nearestHazardPosition = if (hazardX >= 0) android.graphics.PointF(hazardX, hazardY) else null,
                            recentActions = emptyList(),
                            contributingAction = contribAction,
                            isSimulation = fObj.optBoolean("isSimulation", false)
                        )
                    )
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun resetPolicy() {
        qTable.clear()
        failureTraces.clear()
        synchronized(recentRewards) {
            recentRewards.clear()
        }
        totalSteps = 0L
        totalEpisodes = 0
        wins = 0
        deaths = 0
        unknowns = 0
        actionsSelected = 0L
        actionsDispatched = 0L
        actionsRejected = 0L
        cumulativeReward = 0f
        epsilon = initialEpsilon
    }

    override fun getMetrics(): AgentMetrics {
        val verifiedCount = wins + deaths
        val winRate = if (verifiedCount > 0) (wins.toFloat() / verifiedCount) * 100f else 0f
        val rollingAvg = synchronized(recentRewards) {
            if (recentRewards.isEmpty()) 0f else recentRewards.sum() / recentRewards.size
        }

        return AgentMetrics(
            totalSteps = totalSteps,
            totalEpisodes = totalEpisodes,
            wins = wins,
            deaths = deaths,
            unknowns = unknowns,
            cumulativeReward = cumulativeReward,
            currentEpsilon = epsilon,
            winRate = winRate,
            qTableSize = qTable.size,
            actionsSelected = actionsSelected,
            actionsDispatched = actionsDispatched,
            actionsRejected = actionsRejected,
            rollingAverageReward = rollingAvg
        )
    }

    override fun setEpsilon(value: Float) {
        epsilon = value.coerceIn(0.0f, 1.0f)
    }

    private fun getOrCreateQValues(key: String): FloatArray {
        return qTable.computeIfAbsent(key) { FloatArray(actionCount) { 0f } }
    }
}
