package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `failure_memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `levelNumber` INTEGER NOT NULL,
                        `deathX` REAL NOT NULL,
                        `deathY` REAL NOT NULL,
                        `hazardX` REAL NOT NULL,
                        `hazardY` REAL NOT NULL,
                        `contributingAction` TEXT,
                        `isSimulation` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `episode_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `levelNumber` INTEGER NOT NULL,
                        `totalSteps` INTEGER NOT NULL,
                        `cumulativeReward` REAL NOT NULL,
                        `isSimulation` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
