package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LevelDevilAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.i(TAG, "LevelDevilAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can inspect foreground package name if desired (e.g. com.unept.leveldevil)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isConnected.value = false
        }
        Log.i(TAG, "LevelDevilAccessibilityService destroyed")
    }

    /**
     * Dispatches a tap gesture at the specified screen pixel coordinates.
     */
    fun dispatchTap(x: Float, y: Float, durationMs: Long = 60L, onResult: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onResult?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onResult?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a dual touch (e.g. holding Direction while tapping Jump).
     */
    fun dispatchDualTap(
        x1: Float, y1: Float, duration1Ms: Long,
        x2: Float, y2: Float, duration2Ms: Long,
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val path1 = Path().apply { moveTo(x1, y1) }
        val path2 = Path().apply { moveTo(x2, y2) }

        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, duration1Ms)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0L, duration2Ms)

        val builder = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)

        dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onResult?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onResult?.invoke(false)
            }
        }, null)
    }

    companion object {
        private const val TAG = "LevelDevilAccessibility"
        var instance: LevelDevilAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
