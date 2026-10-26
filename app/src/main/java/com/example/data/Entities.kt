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
