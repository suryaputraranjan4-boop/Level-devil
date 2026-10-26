package com.example.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Generates synthetic Level Devil frames with the game's actual minimalist visual geometry:
 * - Off-white/cream background (#E8E6DF)
 * - Solid black rectangular platforms (#1C1C1E)
 * - Yellow player cube (#FFD000) with facial eyes
 * - Triangular hazardous spikes
 * - Wooden exit doorway (#8B5A2B)
 * - Dynamic moving trap obstacles
 *
 * Used for calibration, unit testing, and offline verification of the vision and RL pipeline.
 */
class SyntheticLevelDevilFrameGenerator(
    private val width: Int = 320,
    private val height: Int = 180
) {
    private val bgPaint = Paint().apply { color = Color.rgb(232, 230, 223) }
    private val platformPaint = Paint().apply { color = Color.rgb(28, 28, 30) }
    private val playerPaint = Paint().apply { color = Color.rgb(255, 208, 0) }
    private val eyePaint = Paint().apply { color = Color.rgb(20, 20, 20) }
    private val doorPaint = Paint().apply { color = Color.rgb(139, 90, 43) }
    private val doorArchPaint = Paint().apply { color = Color.rgb(110, 65, 30) }
    private val spikePaint = Paint().apply { color = Color.rgb(35, 35, 35) }
    private val textPaint = Paint().apply {
        color = Color.rgb(40, 40, 40)
        textSize = 14f
        isAntiAlias = true
    }

    private var trapOffsetX = 0f
    private var trapDirection = 1f

    fun generateFrame(
        playerXPercent: Float = 0.20f,
        playerYPercent: Float = 0.65f,
        includeMovingTrap: Boolean = true,
        showDeathScreen: Boolean = false,
        levelNumber: Int = 1
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (showDeathScreen) {
            // Draw Level Devil Death / "OUCH" screen with prominent red skull / banner
            val bgDark = Paint().apply { color = Color.rgb(30, 20, 25) }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgDark)
            val redBannerPaint = Paint().apply { color = Color.rgb(220, 35, 35) }
            canvas.drawRect(width * 0.25f, height * 0.38f, width * 0.75f, height * 0.62f, redBannerPaint)
            val deathPaint = Paint().apply {
                color = Color.WHITE
                textSize = 24f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText("OUCH!", width / 2f, height * 0.54f, deathPaint)
            return bitmap
        }

        // 1. Draw Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw level header
        canvas.drawText("LEVEL $levelNumber", width * 0.42f, height * 0.16f, textPaint)

        // 2. Draw Ground Platforms
        canvas.drawRect(width * 0.05f, height * 0.75f, width * 0.40f, height * 0.88f, platformPaint)
        canvas.drawRect(width * 0.60f, height * 0.75f, width * 0.95f, height * 0.88f, platformPaint)
        canvas.drawRect(width * 0.44f, height * 0.62f, width * 0.56f, height * 0.68f, platformPaint)

        // 3. Draw Pit Spikes in the gaps
        drawSpikes(canvas, width * 0.40f, width * 0.44f, height * 0.88f, 3)
        drawSpikes(canvas, width * 0.56f, width * 0.60f, height * 0.88f, 3)

        // 4. Draw Exit Door on the right
        val doorLeft = width * 0.82f
        val doorTop = height * 0.54f
        val doorRight = width * 0.93f
        val doorBottom = height * 0.75f
        canvas.drawRect(doorLeft, doorTop, doorRight, doorBottom, doorPaint)
        canvas.drawRect(doorLeft + 3f, doorTop + 4f, doorRight - 3f, doorBottom, doorArchPaint)

        // 5. Draw Dynamic Moving Trap if requested
        if (includeMovingTrap) {
            trapOffsetX += trapDirection * 12f
            if (trapOffsetX > 30f || trapOffsetX < -30f) trapDirection *= -1f

            val trapX = (width * 0.50f) + trapOffsetX
            val trapY = height * 0.45f
            canvas.drawRect(trapX - 10f, trapY - 10f, trapX + 10f, trapY + 10f, platformPaint)
        }

        // 6. Draw Player (Yellow Box with eyes)
        val pSize = width * 0.05f
        val pLeft = width * playerXPercent
        val pTop = height * playerYPercent
        canvas.drawRect(pLeft, pTop, pLeft + pSize, pTop + pSize, playerPaint)

        // Player eyes
        val eyeSize = pSize * 0.18f
        canvas.drawRect(pLeft + pSize * 0.55f, pTop + pSize * 0.25f, pLeft + pSize * 0.55f + eyeSize, pTop + pSize * 0.25f + eyeSize, eyePaint)
        canvas.drawRect(pLeft + pSize * 0.80f, pTop + pSize * 0.25f, pLeft + pSize * 0.80f + eyeSize, pTop + pSize * 0.25f + eyeSize, eyePaint)

        return bitmap
    }

    private fun drawSpikes(canvas: Canvas, startX: Float, endX: Float, baseY: Float, count: Int) {
        val step = (endX - startX) / count
        val spikeHeight = 12f
        for (i in 0 until count) {
            val sx = startX + i * step
            val path = Path().apply {
                moveTo(sx, baseY)
                lineTo(sx + step / 2f, baseY - spikeHeight)
                lineTo(sx + step, baseY)
                close()
            }
            canvas.drawPath(path, spikePaint)
        }
    }
}
