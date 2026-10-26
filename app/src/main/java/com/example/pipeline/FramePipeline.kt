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
import com.example.data.TrainingSessionEntity
import com.example.input.InputController
import com.example.input.InputStatus
import com.example.service.LevelDevilAccessibilityService
import com.example.service.ScreenCaptureService
import com.example.vision.DetectionResult
import com.example.vision.GameObservation
import com.example.vision.HeuristicColorEdgeDetector
import com.example.vision.LevelDevilVisionDetector
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

enum class PipelineSourceMode {
    LIVE_SCREEN,
    SYNTHETIC_SIMULATION
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

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

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
            }
        }

        // Collect MediaProjection capture state
        scope.launch {
            ScreenCaptureService.isCapturing.collect { capturing ->
                _state.value = _state.value.copy(isCapturing = capturing)
                if (capturing) {
                    setSourceMode(PipelineSourceMode.LIVE_SCREEN)
                }
            }
        }
    }

    fun setSourceMode(mode: PipelineSourceMode) {
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

    /**
     * Entry point for frames coming from ScreenCaptureService.
     */
    fun onNewCapturedFrame(frame: Bitmap, captureLatencyMs: Long) {
        if (_state.value.sourceMode != PipelineSourceMode.LIVE_SCREEN) return
        processFramePipeline(frame, captureLatencyMs)
    }

    /**
     * Complete end-to-end processing pipeline:
     * Frame -> Vision -> Observation & Reward -> Agent Decision -> Touch Dispatch -> Training Update
     */
    fun processFramePipeline(frame: Bitmap, captureLatencyMs: Long) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            // Drop frame if previous is still processing to prevent queue latency buildup
            return
        }

        try {
            val totalStartNs = System.nanoTime()

            // 1. Vision Module
            val detectionResult = visionDetector.processFrame(frame, previousFrame)
            val currObs = detectionResult.observation

            // 2. Game Agent Observation & Action Selection
            val agentStartNs = System.nanoTime()
            val currState = RLState.fromObservation(currObs)
            val isTraining = _state.value.isTrainingActive
            val selectedAction = gameAgent.selectAction(currObs, explore = isTraining)
            val qValues = gameAgent.getQValues(currState)
            val agentLatencyMs = (System.nanoTime() - agentStartNs) / 1_000_000

            // 3. Touch Input Dispatch
            var inputLatencyMs = 0L
            if (_state.value.sourceMode == PipelineSourceMode.LIVE_SCREEN) {
                inputController.dispatchAction(
                    selectedAction,
                    frame.width,
                    frame.height
                ) { _, latency ->
                    inputLatencyMs = latency
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
                inputStatus = inputController.status.value
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

                delay(50) // ~20 FPS in simulation mode for battery & responsiveness
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
