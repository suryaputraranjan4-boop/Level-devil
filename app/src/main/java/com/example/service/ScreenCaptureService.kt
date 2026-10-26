package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.LevelDevilApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Reusable Bitmap to avoid frequent allocations and garbage collection pauses
    private var reusableBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Service with MEDIA_PROJECTION type
        val notification = createNotification()
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            LevelDevilApplication.NOTIFICATION_CAPTURE_ID,
            notification,
            foregroundType
        )

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        val screenWidth = intent.getIntExtra(EXTRA_SCREEN_WIDTH, 1280)
        val screenHeight = intent.getIntExtra(EXTRA_SCREEN_HEIGHT, 720)
        val screenDensity = intent.getIntExtra(EXTRA_SCREEN_DENSITY, 320)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            startMediaProjection(resultCode, resultData, screenWidth, screenHeight, screenDensity)
        } else {
            Log.e(TAG, "Invalid MediaProjection intent data")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startMediaProjection(
        resultCode: Int,
        resultData: Intent,
        width: Int,
        height: Int,
        density: Int
    ) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection was null")
            _isCapturing.value = false
            stopSelf()
            return
        }

        // Downscale capture surface slightly (e.g., width / 2) for maximum speed and minimal memory
        val captureWidth = (width / 2).coerceAtLeast(320)
        val captureHeight = (height / 2).coerceAtLeast(180)

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)

        imageReader?.setOnImageAvailableListener({ reader ->
            val captureStartNs = System.nanoTime()
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    processImageDirectly(image, captureWidth, captureHeight, captureStartNs)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error acquiring frame: ${e.message}")
            } finally {
                image?.close()
            }
        }, backgroundHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "LevelDevilCapture",
            captureWidth,
            captureHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )

        _isCapturing.value = true
        Log.i(TAG, "Screen capture active: ${captureWidth}x${captureHeight}")
    }

    private fun processImageDirectly(
        image: Image,
        width: Int,
        height: Int,
        captureStartNs: Long
    ) {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmapWidth = width + rowPadding / pixelStride

        if (reusableBitmap == null || reusableBitmap?.width != bitmapWidth || reusableBitmap?.height != height) {
            reusableBitmap?.recycle()
            reusableBitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        }

        reusableBitmap?.copyPixelsFromBuffer(buffer)

        val captureLatencyMs = (System.nanoTime() - captureStartNs) / 1_000_000

        // Crop rowPadding if present
        val finalFrame = if (rowPadding == 0) {
            reusableBitmap!!
        } else {
            Bitmap.createBitmap(reusableBitmap!!, 0, 0, width, height)
        }

        // Pass directly to frame processing pipeline in memory (no disk I/O)
        LevelDevilApplication.instance.framePipeline.onNewCapturedFrame(finalFrame, captureLatencyMs)
    }

    private fun stopCapture() {
        _isCapturing.value = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            reusableBitmap?.recycle()
            reusableBitmap = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing capture resources", e)
        }
    }

    override fun onDestroy() {
        stopCapture()
        handlerThread?.quitSafely()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, LevelDevilApplication.CHANNEL_CAPTURE_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Capture", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_SCREEN_WIDTH = "extra_screen_width"
        const val EXTRA_SCREEN_HEIGHT = "extra_screen_height"
        const val EXTRA_SCREEN_DENSITY = "extra_screen_density"

        private val _isCapturing = MutableStateFlow(false)
        val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()
    }
}
