package com.niftysense.app.widget.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

object SparklineBitmapGenerator {

    private val positiveColor = Color.parseColor("#10B981") // Emerald Green
    private val negativeColor = Color.parseColor("#F43F5E") // Rose Red

    fun generateSparklineBitmap(
        points: List<Double>,
        isPositive: Boolean,
        width: Int = 220,
        height: Int = 70
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (points.size < 2) {
            return bitmap
        }

        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val range = if (maxVal - minVal > 0.0) maxVal - minVal else 1.0

        val strokeColor = if (isPositive) positiveColor else negativeColor
        val padding = 6f
        val usableWidth = width - 2 * padding
        val usableHeight = height - 2 * padding

        val linePath = Path()
        val fillPath = Path()

        val stepX = usableWidth / (points.size - 1)

        points.forEachIndexed { index, value ->
            val x = padding + index * stepX
            val normalizedY = ((value - minVal) / range).toFloat()
            // Invert Y because canvas Y grows downwards
            val y = padding + (1f - normalizedY) * usableHeight

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, height.toFloat())
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (index == points.size - 1) {
                fillPath.lineTo(x, height.toFloat())
                fillPath.close()
            }
        }

        // 1. Draw gradient fill underneath line
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.argb(45, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)),
                Color.argb(0, Color.red(strokeColor), Color.green(strokeColor), Color.blue(strokeColor)),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fillPath, fillPaint)

        // 2. Draw smooth stroke line
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(linePath, strokePaint)

        return bitmap
    }
}
