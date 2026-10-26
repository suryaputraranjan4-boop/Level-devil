package com.example.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.example.agent.AgentMetrics
import com.example.agent.EpisodeOutcome
import com.example.agent.FailureTrace
import com.example.agent.GameAgent
import com.example.agent.PlayerAction
import com.example.agent.QLearningAgent
import com.example.agent.RLState
import com.example.agent.RewardFunction
import com.example.agent.Transition
import com.example.data.AppDatabase
import com.example.data.CheckpointEntity
import com.example.data.EpisodeLogEntity
import com.example.data.FailureMemoryEntity
import com.example.input.ActionStep
import com.example.input.InputController
import com.example.input.InputStatus
import com.example.service.LevelDevilAccessibilityService
import com.example.service.ScreenCaptureService
import com.example.vision.GameObservation
import com.example.vision.HeuristicColorEdgeDetector
import com.example.vision.LevelDevilVisionDetector
import com.example.vision.ObjectType
import com.example.vision.SyntheticLevelDevilFrameGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

enum class PipelineSourceMode {
    LIVE_SCREEN,
    SYNTHETIC_SIMULATION
}

enum class AutomationState(val label: String) {
    STOPPED("Stopped"),
    RUNNING("Running"),
    PAUSED("Paused"),
    ERROR_PAUSED("Error (Paused)")
}

data class DashboardState(
    val currentFrameBitmap: Bitmap? = null,
    val observation: GameObservation = GameObservation(),
    val currentAction: PlayerAction = PlayerAction.WAIT,
    val actionQValues: Map<PlayerAction, Float> = emptyMap(),
    val fps: Float = 0f,
    val captureLatencyMs: Long = 0L,
    val visionLatencyMs: Long = 0L,
    val agentLatencyMs: Long = 0L,
    val inputLatencyMs: Long = 0L,
    val totalLatencyMs: Long = 0L,
    val agentMetrics: AgentMetrics = AgentMetrics(),
    val realMetrics: AgentMetrics = AgentMetrics(),
    val simMetrics: AgentMetrics = AgentMetrics(),
    val lastRewardValue: Float = 0f,
    val lastRewardReason: String = "Awaiting transition",
    val lastOutcome: EpisodeOutcome = EpisodeOutcome.IN_PROGRESS,
    val recentFailures: List<FailureTrace> = emptyList(),
    val isCapturing: Boolean = false,
    val isAccessibilityConnected: Boolean = false,
    val isTrainingActive: Boolean = false,
    val isInferenceOnly: Boolean = false,
    val learningRate: Float = 0.15f,
    val discountFactor: Float = 0.95f,
    val minEpsilon: Float = 0.05f,
    val epsilonDecay: Float = 0.995f,
    val sourceMode: PipelineSourceMode = PipelineSourceMode.SYNTHETIC_SIMULATION,
    val inputStatus: InputStatus = InputStatus.DISCONNECTED_DISABLED,
    val automationState: AutomationState = AutomationState.STOPPED,
    val visionStatus: String = "Awaiting Frame Ingestion",
    val lastActionResult: String = "No actions dispatched yet",
    val lastDetectedPlayerPos: String = "Unknown",
    val playerVelocityDesc: String = "Stationary",
    val nextObstacleDesc: String = "None Detected",
    val isGestureInProgress: Boolean = false,
    val tapDurationMs: Long = 60L,
    val holdDurationMs: Long = 220L,
    val sequenceDelayMs: Long = 100L,
    val leftBtnX: Float = 0.14f,
    val leftBtnY: Float = 0.85f,
    val rightBtnX: Float = 0.32f,
    val rightBtnY: Float = 0.85f,
    val jumpBtnX: Float = 0.86f,
    val jumpBtnY: Float = 0.85f,
    val playerSeedX: Float = 0.50f,
    val playerSeedY: Float = 0.55f,
    val currentLevel: Int = 1,
    val statusMessage: String = "Ready"
)

class FramePipeline(
    private val context: Context,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    val visionDetector: LevelDevilVisionDetector = HeuristicColorEdgeDetector()
    // Keep synthetic training isolated so its policy and outcomes cannot contaminate real-game metrics.
    val gameAgent: GameAgent = QLearningAgent()
    private val simulationAgent: GameAgent = QLearningAgent()
    val rewardFunction = RewardFunction()
    val inputController = InputController(context)
    val syntheticGenerator = SyntheticLevelDevilFrameGenerator()

    private val _state = MutableStateFlow(
        DashboardState(
            leftBtnX = inputController.calibration.leftBtnX,
            leftBtnY = inputController.calibration.leftBtnY,
            rightBtnX = inputController.calibration.rightBtnX,
            rightBtnY = inputController.calibration.rightBtnY,
            jumpBtnX = inputController.calibration.jumpBtnX,
            jumpBtnY = inputController.calibration.jumpBtnY,
            playerSeedX = inputController.calibration.playerSeedX,
            playerSeedY = inputController.calibration.playerSeedY,
            tapDurationMs = inputController.tapDurationMs,
            holdDurationMs = inputController.holdDurationMs,
            sequenceDelayMs = inputController.sequenceDelayMs,
            learningRate = gameAgent.learningRate,
            discountFactor = gameAgent.discountFactor,
            minEpsilon = gameAgent.minEpsilon,
            epsilonDecay = gameAgent.epsilonDecay
        )
    )
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    fun publishCalibration() {
        val c = inputController.calibration
        _state.value = _state.value.copy(
            leftBtnX = c.leftBtnX, leftBtnY = c.leftBtnY,
            rightBtnX = c.rightBtnX, rightBtnY = c.rightBtnY,
            jumpBtnX = c.jumpBtnX, jumpBtnY = c.jumpBtnY,
            playerSeedX = c.playerSeedX, playerSeedY = c.playerSeedY,
            tapDurationMs = inputController.tapDurationMs,
            holdDurationMs = inputController.holdDurationMs,
            sequenceDelayMs = inputController.sequenceDelayMs
        )
    }

    private var previousFrame: Bitmap? = null
    private var previousObservation: GameObservation? = null
    private var previousState: RLState? = null
    private var previousAction: PlayerAction? = null
    private var lastActionDispatchedStatus: Boolean = true
    private var recentActionHistory = ArrayDeque<PlayerAction>(5)

    // FPS calculation
    private var frameCount = 0
    private var lastFpsCalculationTime = System.currentTimeMillis()
    private var currentFps = 0f

    // Simulation loop job
    private var simulationJob: Job? = null
    private val isProcessingFrame = AtomicBoolean(false)

    // Synthetic simulation state
    private var simPlayerX = 0.20f
    private var simPlayerY = 0.65f
    private var simLevel = 1

    // Simulation isolated metrics
    private var simSteps = 0L
    private var simEpisodes = 0
    private var simWins = 0
    private var simDeaths = 0
    private var simUnknowns = 0
    private var simCumulativeReward = 0f
    private val simRecentRewards = ArrayDeque<Float>(30)

    init {
        // Collect Accessibility Service connection state
        scope.launch {
            LevelDevilAccessibilityService.isConnected.collect { connected ->
                inputController.checkStatus()
                _state.value = _state.value.copy(
                    isAccessibilityConnected = connected,
                    inputStatus = inputController.status.value
                )
                if (!connected && _state.value.automationState == AutomationState.RUNNING) {
                    stopAutomation("Accessibility Service disconnected. Automation halted.")
                }
            }
        }

        // Collect gesture in-progress state
        scope.launch {
            LevelDevilAccessibilityService.isGestureInProgress.collect { inProgress ->
                _state.value = _state.value.copy(isGestureInProgress = inProgress)
            }
        }

        // Collect MediaProjection capture state
        scope.launch {
            ScreenCaptureService.isCapturing.collect { capturing ->
                _state.value = _state.value.copy(isCapturing = capturing)
                if (capturing) {
                    setSourceMode(PipelineSourceMode.LIVE_SCREEN)
                } else if (_state.value.sourceMode == PipelineSourceMode.LIVE_SCREEN && _state.value.automationState == AutomationState.RUNNING) {
                    stopAutomation("Screen capture stopped. Automation halted.")
                }
            }
        }
    }

    // --- Automation Lifecycle Controls ---

    fun startAutomation() {
        inputController.checkStatus()

        if (!_state.value.isAccessibilityConnected) {
            _state.value = _state.value.copy(
                automationState = AutomationState.ERROR_PAUSED,
                statusMessage = "Cannot start automation: Accessibility Service is not enabled."
            )
            return
        }

        if (_state.value.sourceMode == PipelineSourceMode.LIVE_SCREEN && !_state.value.isCapturing) {
            _state.value = _state.value.copy(
                automationState = AutomationState.ERROR_PAUSED,
                statusMessage = "Cannot start automation: Screen capture is not active."
            )
            return
        }

        visionDetector.resetTracker()

        _state.value = _state.value.copy(
            automationState = AutomationState.RUNNING,
            statusMessage = "Automation loop RUNNING. Continuous vision-to-action active."
        )
    }

    fun stopAutomation(customMessage: String? = null) {
        inputController.cancelPendingActions()
        _state.value = _state.value.copy(
            automationState = AutomationState.STOPPED,
            currentAction = PlayerAction.WAIT,
            statusMessage = customMessage ?: "Automation STOPPED by user. Action dispatch halted safely."
        )
    }

    fun pauseAutomation() {
        inputController.cancelPendingActions()
        _state.value = _state.value.copy(
            automationState = AutomationState.PAUSED,
            statusMessage = "Automation PAUSED. Visual tracking continues without touch dispatch."
        )
    }

    fun resumeAutomation() {
        startAutomation()
    }

    fun setSourceMode(mode: PipelineSourceMode) {
        if (_state.value.automationState == AutomationState.RUNNING) {
            stopAutomation("Source mode changed. Automation stopped.")
        }
        _state.value = _state.value.copy(sourceMode = mode)
        if (mode == PipelineSourceMode.SYNTHETIC_SIMULATION) {
            startSimulationRunner()
        } else {
            stopSimulationRunner()
        }
    }

    fun toggleTraining() {
        val next = !_state.value.isTrainingActive
        _state.value = _state.value.copy(
            isTrainingActive = next,
            statusMessage = if (next) "RL Training Loop ACTIVE" else "Training PAUSED (Inference Only)"
        )
    }

    fun toggleInferenceOnly() {
        val next = !_state.value.isInferenceOnly
        gameAgent.isInferenceOnly = next
        simulationAgent.isInferenceOnly = next
        _state.value = _state.value.copy(
            isInferenceOnly = next,
            statusMessage = if (next) "Inference-Only Mode: Q-updates & Epsilon decay FROZEN" else "Learning Mode: Q-updates ENABLED"
        )
    }

    fun updateHyperparameters(lr: Float, gamma: Float, minEps: Float, decay: Float) {
        val boundedLr = lr.coerceIn(0.01f, 1.0f)
        val boundedGamma = gamma.coerceIn(0.1f, 0.99f)
        val boundedMinEpsilon = minEps.coerceIn(0.01f, 0.5f)
        val boundedDecay = decay.coerceIn(0.90f, 0.9999f)
        listOf(gameAgent, simulationAgent).forEach { agent ->
            agent.learningRate = boundedLr
            agent.discountFactor = boundedGamma
            agent.minEpsilon = boundedMinEpsilon
            agent.epsilonDecay = boundedDecay
        }
        _state.value = _state.value.copy(
            learningRate = gameAgent.learningRate,
            discountFactor = gameAgent.discountFactor,
            minEpsilon = gameAgent.minEpsilon,
            epsilonDecay = gameAgent.epsilonDecay
        )
    }

    fun updateTimings(tapMs: Long, holdMs: Long, seqDelayMs: Long) {
        inputController.updateTimingSettings(tapMs, holdMs, seqDelayMs)
        publishCalibration()
    }

    fun executeTestSequence(steps: List<ActionStep>, screenWidth: Int, screenHeight: Int) {
        inputController.executeSequence(
            steps = steps,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onStepDispatched = { idx, step, success ->
                _state.value = _state.value.copy(
                    currentAction = step.action,
                    lastActionResult = "Step ${idx + 1}/${steps.size}: ${step.action.displayName} -> ${if (success) "Success" else "Failed"}"
                )
            },
            onSequenceCompleted = { allSuccess ->
                _state.value = _state.value.copy(
                    lastActionResult = if (allSuccess) "Test Sequence Completed Successfully" else "Test Sequence Finished with Warnings"
                )
            }
        )
    }

    fun onNewCapturedFrame(frame: Bitmap, captureLatencyMs: Long) {
        if (_state.value.sourceMode != PipelineSourceMode.LIVE_SCREEN) return
        processFramePipeline(frame, captureLatencyMs)
    }

    /**
     * Complete end-to-end processing pipeline:
     * Frame -> Vision Tracking -> State Evaluation -> Decision Selection -> Touch Dispatch -> Training Update
     */
    fun processFramePipeline(frame: Bitmap, captureLatencyMs: Long) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            return
        }

        try {
            val totalStartNs = System.nanoTime()

            // 1. Vision Module
            val detectionResult = visionDetector.processFrame(
                currentFrame = frame,
                previousFrame = previousFrame,
                playerSeedX = _state.value.playerSeedX,
                playerSeedY = _state.value.playerSeedY
            )
            val currObs = detectionResult.observation

            // Telemetry strings
            val visionStatusText: String
            val playerPosText: String
            val velocityText: String
            val nextObstacleText: String

            if (currObs.playerDetected && currObs.playerBox != null) {
                val pBox = currObs.playerBox
                visionStatusText = currObs.trackingStatusMessage
                playerPosText = "X: ${(pBox.centerX() * 100).toInt()}%, Y: ${(pBox.centerY() * 100).toInt()}%"
                velocityText = "${currObs.movementDirection.label} (vx: ${String.format("%.2f", currObs.playerVelocityX)}, vy: ${String.format("%.2f", currObs.playerVelocityY)})"
                nextObstacleText = currObs.nextObstacle?.let {
                    val distPercent = (abs(it.boundingBox.centerX() - pBox.centerX()) * 100).toInt()
                    "${it.label} ($distPercent% ahead)"
                } ?: "Path Clear"
            } else {
                visionStatusText = "Player Lost / Occluded. Dispatch Paused."
                playerPosText = "Searching..."
                velocityText = "Stationary"
                nextObstacleText = "None"
            }

            // 2. Decision Making
            val agentStartNs = System.nanoTime()
            val currState = RLState.fromObservation(currObs)
            val isTraining = _state.value.isTrainingActive
            val isAutomationRunning = _state.value.automationState == AutomationState.RUNNING
            val isSim = _state.value.sourceMode == PipelineSourceMode.SYNTHETIC_SIMULATION
            val activeAgent = if (isSim) simulationAgent else gameAgent

            val selectedAction = if (currObs.playerDetected) {
                activeAgent.selectAction(currObs, explore = isTraining)
            } else {
                PlayerAction.WAIT
            }

            val qValues = activeAgent.getQValues(currState)
            val agentLatencyMs = (System.nanoTime() - agentStartNs) / 1_000_000

            // 3. Real Touch Dispatch
            var inputLatencyMs = 0L
            var dispatchResultMsg = _state.value.lastActionResult
            var currentDispatchSuccess = true

            if (isAutomationRunning && _state.value.isAccessibilityConnected) {
                if (currObs.playerDetected) {
                    inputController.dispatchAction(
                        selectedAction,
                        frame.width,
                        frame.height
                    ) { success, latency ->
                        inputLatencyMs = latency
                        currentDispatchSuccess = success
                        dispatchResultMsg = if (success) {
                            "Executed ${selectedAction.displayName} in ${latency}ms"
                        } else {
                            "Dispatch ${selectedAction.displayName} dropped or canceled by OS"
                        }
                    }
                } else {
                    inputController.dispatchAction(PlayerAction.RELEASE, frame.width, frame.height)
                    currentDispatchSuccess = true
                    dispatchResultMsg = "Player detection lost: Touch dispatch paused."
                }
            } else if (isSim) {
                currentDispatchSuccess = true
                dispatchResultMsg = "Simulated action: ${selectedAction.displayName}"
            }

            // 4. Reinforcement Learning Update with verified dispatch status
            var lastRewardVal = _state.value.lastRewardValue
            var lastRewardReasonStr = _state.value.lastRewardReason
            var lastOutcomeVal = _state.value.lastOutcome

            if (isTraining && previousObservation != null && previousState != null && previousAction != null) {
                val rewardDetail = rewardFunction.calculateRewardDetail(
                    prevObs = previousObservation!!,
                    currObs = currObs,
                    actionDispatched = lastActionDispatchedStatus,
                    isSimulation = isSim
                )
                lastRewardVal = rewardDetail.reward
                lastRewardReasonStr = rewardDetail.reason
                lastOutcomeVal = rewardDetail.outcome

                val isTerminal = (rewardDetail.outcome == EpisodeOutcome.WIN || rewardDetail.outcome == EpisodeOutcome.DEATH)

                val pBox = currObs.playerBox
                val nearestSpike = currObs.detectedObjects.firstOrNull { it.type == ObjectType.SPIKE || it.type == ObjectType.MOVING_TRAP }

                val transition = Transition(
                    state = previousState!!,
                    action = previousAction!!,
                    actionDispatched = lastActionDispatchedStatus,
                    reward = rewardDetail.reward,
                    rewardReason = rewardDetail.reason,
                    nextState = currState,
                    outcome = rewardDetail.outcome,
                    isTerminal = isTerminal,
                    isSimulation = isSim,
                    playerPosition = pBox?.let { PointF(it.centerX(), it.centerY()) },
                    hazardPosition = nearestSpike?.boundingBox?.let { PointF(it.centerX(), it.centerY()) }
                )

                // Update the agent Q-values
                activeAgent.update(transition)

                // Update simulation-isolated metrics if running in simulation
                if (isSim) {
                    simSteps++
                    simCumulativeReward += rewardDetail.reward
                    synchronized(simRecentRewards) {
                        if (simRecentRewards.size >= 30) simRecentRewards.removeFirst()
                        simRecentRewards.addLast(rewardDetail.reward)
                    }
                    if (isTerminal) {
                        simEpisodes++
                        when (rewardDetail.outcome) {
                            EpisodeOutcome.WIN -> simWins++
                            EpisodeOutcome.DEATH -> simDeaths++
                            else -> simUnknowns++
                        }
                    }
                }

                // If verified death, record in Failure Memory Bank & Room DB
                if (rewardDetail.outcome == EpisodeOutcome.DEATH && pBox != null) {
                    val trace = FailureTrace(
                        levelNumber = _state.value.currentLevel,
                        deathPosition = PointF(pBox.centerX(), pBox.centerY()),
                        nearestHazardPosition = nearestSpike?.boundingBox?.let { PointF(it.centerX(), it.centerY()) },
                        recentActions = recentActionHistory.toList(),
                        contributingAction = previousAction,
                        isSimulation = isSim
                    )
                    activeAgent.recordFailure(trace)

                    // Persist to Room DB
                    scope.launch(Dispatchers.IO) {
                        try {
                            database.appDao().insertFailure(
                                FailureMemoryEntity(
                                    levelNumber = trace.levelNumber,
                                    deathX = trace.deathPosition.x,
                                    deathY = trace.deathPosition.y,
                                    hazardX = trace.nearestHazardPosition?.x ?: -1f,
                                    hazardY = trace.nearestHazardPosition?.y ?: -1f,
                                    contributingAction = trace.contributingAction?.name,
                                    isSimulation = trace.isSimulation
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                // Reset distance tracking on terminal
                if (isTerminal) {
                    rewardFunction.reset()
                    recentActionHistory.clear()
                }
            }

            // Track recent action history
            if (selectedAction != PlayerAction.WAIT) {
                if (recentActionHistory.size >= 5) recentActionHistory.removeFirst()
                recentActionHistory.addLast(selectedAction)
            }

            // Update FPS
            frameCount++
            val now = System.currentTimeMillis()
            if (now - lastFpsCalculationTime >= 1000L) {
                currentFps = (frameCount * 1000f) / (now - lastFpsCalculationTime)
                frameCount = 0
                lastFpsCalculationTime = now
            }

            val totalLatencyMs = (System.nanoTime() - totalStartNs) / 1_000_000 + captureLatencyMs

            previousFrame = frame
            previousObservation = currObs
            previousState = currState
            previousAction = selectedAction
            lastActionDispatchedStatus = currentDispatchSuccess

            val mainMetrics = activeAgent.getMetrics()
            val simRollingAvg = synchronized(simRecentRewards) {
                if (simRecentRewards.isEmpty()) 0f else simRecentRewards.sum() / simRecentRewards.size
            }
            val simMetricsObj = AgentMetrics(
                totalSteps = simSteps,
                totalEpisodes = simEpisodes,
                wins = simWins,
                deaths = simDeaths,
                unknowns = simUnknowns,
                cumulativeReward = simCumulativeReward,
                currentEpsilon = mainMetrics.currentEpsilon,
                winRate = if (simWins + simDeaths > 0) (simWins.toFloat() / (simWins + simDeaths)) * 100f else 0f,
                qTableSize = mainMetrics.qTableSize,
                actionsSelected = simSteps,
                actionsDispatched = 0L,
                actionsRejected = 0L,
                rollingAverageReward = simRollingAvg
            )

            _state.value = _state.value.copy(
                currentFrameBitmap = frame,
                observation = currObs,
                currentAction = selectedAction,
                actionQValues = qValues,
                fps = currentFps,
                captureLatencyMs = captureLatencyMs,
                visionLatencyMs = detectionResult.visionLatencyMs,
                agentLatencyMs = agentLatencyMs,
                inputLatencyMs = inputLatencyMs,
                totalLatencyMs = totalLatencyMs,
                agentMetrics = mainMetrics,
                realMetrics = if (!isSim) mainMetrics else _state.value.realMetrics,
                simMetrics = simMetricsObj,
                lastRewardValue = lastRewardVal,
                lastRewardReason = lastRewardReasonStr,
                lastOutcome = lastOutcomeVal,
                recentFailures = activeAgent.getRecentFailures(),
                inputStatus = inputController.status.value,
                visionStatus = visionStatusText,
                lastDetectedPlayerPos = playerPosText,
                playerVelocityDesc = velocityText,
                nextObstacleDesc = nextObstacleText,
                lastActionResult = dispatchResultMsg,
                currentLevel = if (isSim) simLevel else 1
            )
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isProcessingFrame.set(false)
        }
    }

    fun startSimulationRunner() {
        if (simulationJob?.isActive == true) return

        simulationJob = scope.launch {
            while (isActive && _state.value.sourceMode == PipelineSourceMode.SYNTHETIC_SIMULATION) {
                val startNs = System.nanoTime()

                when (_state.value.currentAction) {
                    PlayerAction.MOVE_RIGHT, PlayerAction.RIGHT_JUMP -> simPlayerX = (simPlayerX + 0.035f).coerceAtMost(0.92f)
                    PlayerAction.MOVE_LEFT, PlayerAction.LEFT_JUMP -> simPlayerX = (simPlayerX - 0.035f).coerceAtLeast(0.06f)
                    else -> {}
                }

                val inPit = (simPlayerX in 0.40f..0.43f || simPlayerX in 0.57f..0.60f)
                val reachedDoor = simPlayerX >= 0.85f

                val isDeath = inPit
                val isWin = reachedDoor

                val frame = syntheticGenerator.generateFrame(
                    playerXPercent = simPlayerX,
                    playerYPercent = if (isDeath) 0.82f else 0.65f,
                    includeMovingTrap = true,
                    showDeathScreen = isDeath,
                    levelNumber = simLevel
                )

                val captureLatencyMs = (System.nanoTime() - startNs) / 1_000_000
                processFramePipeline(frame, captureLatencyMs)

                if (isDeath || isWin) {
                    delay(250)
                    if (isWin) simLevel++
                    simPlayerX = 0.15f
                }

                delay(50)
            }
        }
    }

    fun stopSimulationRunner() {
        simulationJob?.cancel()
        simulationJob = null
    }

    fun stepSimulationOnce() {
        scope.launch {
            val startNs = System.nanoTime()
            when (_state.value.currentAction) {
                PlayerAction.MOVE_RIGHT, PlayerAction.RIGHT_JUMP -> simPlayerX = (simPlayerX + 0.035f).coerceAtMost(0.92f)
                PlayerAction.MOVE_LEFT, PlayerAction.LEFT_JUMP -> simPlayerX = (simPlayerX - 0.035f).coerceAtLeast(0.06f)
                else -> {}
            }
            val frame = syntheticGenerator.generateFrame(
                playerXPercent = simPlayerX,
                playerYPercent = 0.65f,
                includeMovingTrap = true,
                showDeathScreen = false,
                levelNumber = simLevel
            )
            val captureLatencyMs = (System.nanoTime() - startNs) / 1_000_000
            processFramePipeline(frame, captureLatencyMs)
        }
    }

    suspend fun saveCheckpoint(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val checkpointAgent = if (_state.value.sourceMode == PipelineSourceMode.SYNTHETIC_SIMULATION) simulationAgent else gameAgent
            val serialized = checkpointAgent.saveCheckpoint()
            val metrics = checkpointAgent.getMetrics()
            val entity = CheckpointEntity(
                name = name,
                episodeCount = metrics.totalEpisodes,
                winCount = metrics.wins,
                deathCount = metrics.deaths,
                epsilon = metrics.currentEpsilon,
                serializedQTable = serialized
            )
            database.appDao().insertCheckpoint(entity)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadLatestCheckpoint(): Boolean = withContext(Dispatchers.IO) {
        try {
            val latest = database.appDao().getLatestCheckpoint() ?: return@withContext false
            val checkpointAgent = if (_state.value.sourceMode == PipelineSourceMode.SYNTHETIC_SIMULATION) simulationAgent else gameAgent
            val success = checkpointAgent.loadCheckpoint(latest.serializedQTable)
            if (success) {
                _state.value = _state.value.copy(
                    agentMetrics = checkpointAgent.getMetrics(),
                    recentFailures = checkpointAgent.getRecentFailures(),
                    statusMessage = "Loaded checkpoint: ${latest.name}"
                )
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun resetPolicy() {
        gameAgent.resetPolicy()
        simulationAgent.resetPolicy()
        rewardFunction.reset()
        recentActionHistory.clear()
        simSteps = 0L
        simEpisodes = 0
        simWins = 0
        simDeaths = 0
        simUnknowns = 0
        simCumulativeReward = 0f
        synchronized(simRecentRewards) {
            simRecentRewards.clear()
        }
        _state.value = _state.value.copy(
            agentMetrics = gameAgent.getMetrics(),
            realMetrics = AgentMetrics(),
            simMetrics = AgentMetrics(),
            recentFailures = emptyList(),
            lastRewardValue = 0f,
            lastRewardReason = "Policy reset",
            lastOutcome = EpisodeOutcome.IN_PROGRESS,
            statusMessage = "Policy Q-values, failure memory, and episode counters reset"
        )
    }
}
