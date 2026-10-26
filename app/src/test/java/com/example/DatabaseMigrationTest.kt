package com.example

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private val testDbName = "migration_test.db"

    @Test
    fun `test migration from version 1 to 2 preserves existing checkpoint and session data`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(testDbName)

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `checkpoints` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `episodeCount` INTEGER NOT NULL,
                            `winCount` INTEGER NOT NULL,
                            `deathCount` INTEGER NOT NULL,
                            `epsilon` REAL NOT NULL,
                            `serializedQTable` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `training_sessions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `startTime` INTEGER NOT NULL,
                            `totalSteps` INTEGER NOT NULL,
                            `totalEpisodes` INTEGER NOT NULL,
                            `totalReward` REAL NOT NULL,
                            `winRate` REAL NOT NULL,
                            `avgLatencyMs` REAL NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val v1Db = helper.writableDatabase

        // Insert seed checkpoint into v1 schema
        val cv = ContentValues().apply {
            put("name", "v1_champion_checkpoint")
            put("timestamp", 1700000000000L)
            put("episodeCount", 42)
            put("winCount", 28)
            put("deathCount", 14)
            put("epsilon", 0.05f)
            put("serializedQTable", "{\"test_q_key\":12.5}")
        }
        val insertedId = v1Db.insert("checkpoints", 0, cv)
        assertTrue("Checkpoint insertion in v1 schema must succeed", insertedId > 0)

        // Execute migration 1 -> 2
        AppDatabase.MIGRATION_1_2.migrate(v1Db)
        v1Db.version = 2

        // Verify existing checkpoint record was NOT wiped or corrupted
        val cursor = v1Db.query("SELECT * FROM checkpoints WHERE id = ?", arrayOf(insertedId))
        assertTrue("Checkpoint cursor must have data after migration", cursor.moveToFirst())
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        val episodesIndex = cursor.getColumnIndexOrThrow("episodeCount")
        val qTableIndex = cursor.getColumnIndexOrThrow("serializedQTable")

        assertEquals("v1_champion_checkpoint", cursor.getString(nameIndex))
        assertEquals(42, cursor.getInt(episodesIndex))
        assertEquals("{\"test_q_key\":12.5}", cursor.getString(qTableIndex))
        cursor.close()

        // Verify failure_memories table was created and can accept inserts
        val failureCv = ContentValues().apply {
            put("timestamp", 1700000050000L)
            put("levelNumber", 1)
            put("deathX", 0.35f)
            put("deathY", 0.65f)
            put("hazardX", 0.35f)
            put("hazardY", 0.70f)
            put("contributingAction", "MOVE_RIGHT")
            put("isSimulation", 0)
        }
        val failureId = v1Db.insert("failure_memories", 0, failureCv)
        assertTrue("failure_memories insertion must succeed in migrated v2 db", failureId > 0)

        // Verify episode_logs table was created and can accept inserts
        val episodeCv = ContentValues().apply {
            put("timestamp", 1700000060000L)
            put("outcome", "WIN")
            put("levelNumber", 1)
            put("totalSteps", 18)
            put("cumulativeReward", 95.5f)
            put("isSimulation", 0)
            put("durationMs", 1250L)
        }
        val episodeId = v1Db.insert("episode_logs", 0, episodeCv)
        assertTrue("episode_logs insertion must succeed in migrated v2 db", episodeId > 0)

        v1Db.close()
    }
}
