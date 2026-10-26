package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val episodeCount: Int,
    val winCount: Int,
    val deathCount: Int,
    val epsilon: Float,
    val serializedQTable: String
)

@Entity(tableName = "training_sessions")
data class TrainingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    val totalSteps: Int,
    val totalEpisodes: Int,
    val totalReward: Float,
    val winRate: Float,
    val avgLatencyMs: Float
)

@Entity(tableName = "failure_memories")
data class FailureMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val levelNumber: Int,
    val deathX: Float,
    val deathY: Float,
    val hazardX: Float,
    val hazardY: Float,
    val contributingAction: String?,
    val isSimulation: Boolean
)

@Entity(tableName = "episode_logs")
data class EpisodeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val outcome: String,
    val levelNumber: Int,
    val totalSteps: Int,
    val cumulativeReward: Float,
    val isSimulation: Boolean,
    val durationMs: Long
)
