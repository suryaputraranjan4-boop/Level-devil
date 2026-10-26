package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.pipeline.FramePipeline

class LevelDevilApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var framePipeline: FramePipeline
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "level_devil_ai.db"
        ).fallbackToDestructiveMigration().build()

        framePipeline = FramePipeline(this, database)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_CAPTURE_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_CAPTURE_ID = "channel_screen_capture"
        const val NOTIFICATION_CAPTURE_ID = 1001

        lateinit var instance: LevelDevilApplication
            private set
    }
}
