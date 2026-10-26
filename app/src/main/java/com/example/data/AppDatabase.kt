package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM checkpoints ORDER BY timestamp DESC")
    fun getAllCheckpoints(): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCheckpoint(): CheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoint(checkpoint: CheckpointEntity): Long

    @Query("DELETE FROM checkpoints WHERE id = :id")
    suspend fun deleteCheckpoint(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: TrainingSessionEntity): Long

    @Query("SELECT * FROM training_sessions ORDER BY startTime DESC LIMIT 20")
    fun getRecentSessions(): Flow<List<TrainingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFailure(failure: FailureMemoryEntity): Long

    @Query("SELECT * FROM failure_memories ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFailures(limit: Int = 20): Flow<List<FailureMemoryEntity>>

    @Query("SELECT * FROM failure_memories WHERE levelNumber = :level ORDER BY timestamp DESC")
    suspend fun getFailuresForLevel(level: Int): List<FailureMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodeLog(episode: EpisodeLogEntity): Long

    @Query("SELECT * FROM episode_logs WHERE isSimulation = :isSimulation ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEpisodes(isSimulation: Boolean, limit: Int = 20): Flow<List<EpisodeLogEntity>>
}

@Database(
    entities = [
        CheckpointEntity::class,
        TrainingSessionEntity::class,
        FailureMemoryEntity::class,
        EpisodeLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
