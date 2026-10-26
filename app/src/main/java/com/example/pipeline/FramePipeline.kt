package com.example.pipeline

import android.content.Context
import android.graphics.Bitmap
import com.example.agent.AgentMetrics
import com.example.agent.GameAgent
import com.example.agent.PlayerAction
import com.example.agent.QLearningAgent
import com.example.agent.RLState
import com.example.agent.RewardFunction
import com.example.agent.Transition
import com.example.data.AppDatabase
import com.example.data.CheckpointEntity
import com.example.input.ActionStep
import com.example.input.InputController
import com.example.input.InputStatus
import com.example.service.LevelDevilAccessibilityService
import com.example.service.ScreenCaptureService
import com.example.vision.DetectionResult
import com.example.vision.GameObservation
import com.example.vision.HeuristicColorEdgeDetector
import com.example.vision.LevelDevilVisionDetector
import com.example.vision.MovementDirection
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
    val isCapturing: Boolean = false,
    val isAccessibilityConnected: Boolean = false,
    val isTrainingActive: Boolean = false,
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
    val statusMessage: String = "Ready"
)

class FramePipeline(
    private val context: Context,
    private val database: AppDatabase
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    val visionDetector: LevelDevilVisionDetector = HeuristicColorEdgeDetector()
    val gameAgent: GameAgent = QLearningAgent()
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
            sequenceDelayMs = inputController.sequenceDelayMs
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
    private var simEpisodes = 0
    private var simLevel = 1

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

    /**
     * Starts the real automation loop. Never starts automatically on launch.
     */
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

        // Reset vision tracker with seed
        visionDetector.resetTracker()

        _state.value = _state.value.copy(
            automationState = AutomationState.RUNNING,
            statusMessage = "Automation loop RUNNING. Continuous vision-to-action active."
        )
    }

    /**
     * Safely cancels pending actions and stops automation immediately.
     */
    fun stopAutomation(customMessage: String? = null) {
        inputController.cancelPendingActions()
        _state.value = _state.value.copy(
            automationState = AutomationState.STOPPED,
            currentAction = PlayerAction.WAIT,
            statusMessage = customMessage ?: "Automation STOPPED by user. Action dispatch halted safely."
        )
    }

    /**
     * Pauses automation dispatch while keeping frame capture running.
     */
    fun pauseAutomation() {
        inputController.cancelPendingActions()
        _state.value = _state.value.copy(
            automationState = AutomationState.PAUSED,
            statusMessage = "Automation PAUSED. Visual tracking continues without touch dispatch."
        )
    }

    /**
     * Resumes automation if requirements are met.
     */
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

    /**
     * Entry point for frames coming from ScreenCaptureService.
     */
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
            // Drop frame if previous is still processing to prevent queue latency buildup
            return
        }

        try {
            val totalStartNs = System.nanoTime()

            // 1. Vision Module: Continuous seed-guided detection
            val detectionResult = visionDetector.processFrame(
                currentFrame = frame,
                previousFrame = previousFrame,
                playerSeedX = _state.value.playerSeedX,
                playerSeedY = _state.value.playerSeedY
            )
            val currObs = detectionResult.observation

            // Tracking and Telemetry descriptors
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
                visionStatusText = "Player Lost / Obstructed. Dispatch Paused."
                playerPosText = "Searching..."
                velocityText = "Stationary"
                nextObstacleText = "None"
            }

            // 2. Game Agent Observation & Action Selection
            val agentStartNs = System.nanoTime()
            val currState = RLState.fromObservation(currObs)
            val isTraining = _state.value.isTrainingActive
            val isAutomationRunning = _state.value.automationState == AutomationState.RUNNING

            // Only decide new action if player is detected
            val selectedAction = if (currObs.playerDetected) {
                gameAgent.selectAction(currObs, explore = isTraining)
            } else {
                PlayerAction.WAIT
            }

            val qValues = gameAgent.getQValues(currState)
            val agentLatencyMs = (System.nanoTime() - agentStartNs) / 1_000_000

            // 3. Real Touch Input Dispatch
            var inputLatencyMs = 0L
            var dispatchResultMsg = _state.value.lastActionResult

            if (isAutomationRunning && _state.value.isAccessibilityConnected) {
                if (currObs.playerDetected) {
                    inputController.dispatchAction(
                        selectedAction,
                        frame.width,
                        frame.height
                    ) { success, latency ->
                        inputLatencyMs = latency
                        dispatchResultMsg = if (success) {
                            "Executed ${selectedAction.displayName} in ${latency}ms"
                        } else {
                            "Dispatch ${selectedAction.displayName} dropped or canceled by OS"
                        }
                    }
                } else {
                    // Player lost: release held touches and pause dispatch
                    inputController.dispatchAction(PlayerAction.RELEASE, frame.width, frame.height)
                    dispatchResultMsg = "Player detection lost: Touch dispatch paused."
                }
            }

            // 4. Reinforcement Learning Update
            if (isTraining && previousObservation != null && previousState != null && previousAction != null) {
                val reward = rewardFunction.calculateReward(previousObservation!!, currObs)
                val isTerminal = currObs.isDeathScreen || currObs.isVictoryScreen
                val transition = Transition(
                    state = previousState!!,
                    action = previousAction!!,
                    reward = reward,
                    nextState = currState,
                    isTerminal = isTerminal
                )
                gameAgent.update(transition)

                if (isTerminal) {
                    rewardFunction.reset()
                }
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
                agentMetrics = gameAgent.getMetrics(),
                inputStatus = inputController.status.value,
                visionStatus = visionStatusText,
                lastDetectedPlayerPos = playerPosText,
                playerVelocityDesc = velocityText,
                nextObstacleDesc = nextObstacleText,
                lastActionResult = dispatchResultMsg
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

                // Simulate realistic game physics according to agent's action
                when (_state.value.currentAction) {
                    PlayerAction.MOVE_RIGHT, PlayerAction.RIGHT_JUMP -> simPlayerX = (simPlayerX + 0.035f).coerceAtMost(0.92f)
                    PlayerAction.MOVE_LEFT, PlayerAction.LEFT_JUMP -> simPlayerX = (simPlayerX - 0.035f).coerceAtLeast(0.06f)
                    else -> {}
                }

                // Check pit hazard (0.40 to 0.44 and 0.56 to 0.60 if not jumping)
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
                    simEpisodes++
                    if (isWin) simLevel++
                    simPlayerX = 0.15f
                }

                delay(50) // ~20 FPS in simulation mode
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
            val serialized = gameAgent.saveCheckpoint()
            val metrics = gameAgent.getMetrics()
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
            val success = gameAgent.loadCheckpoint(latest.serializedQTable)
            if (success) {
                _state.value = _state.value.copy(
                    agentMetrics = gameAgent.getMetrics(),
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
        rewardFunction.reset()
        _state.value = _state.value.copy(
            agentMetrics = gameAgent.getMetrics(),
            statusMessage = "Policy Q-values and training counters reset"
        )
    }
}
