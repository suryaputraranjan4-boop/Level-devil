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
}

@Database(
    entities = [CheckpointEntity::class, TrainingSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
