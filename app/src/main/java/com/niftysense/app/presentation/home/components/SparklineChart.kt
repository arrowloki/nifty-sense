package com.niftysense.app.presentation.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.niftysense.app.presentation.theme.Emerald500
import com.niftysense.app.presentation.theme.Rose500

@Composable
fun SparklineChart(
    points: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(70.dp)
) {
    if (points.size < 2) return

    val strokeColor = if (isPositive) Emerald500 else Rose500

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val minVal = points.minOrNull() ?: 0.0
        val maxVal = points.maxOrNull() ?: 1.0
        val range = if (maxVal - minVal > 0.0) maxVal - minVal else 1.0

        val padding = 8f
        val usableWidth = width - 2 * padding
        val usableHeight = height - 2 * padding
        val stepX = usableWidth / (points.size - 1)

        val strokePath = Path()
        val fillPath = Path()

        points.forEachIndexed { index, value ->
            val x = padding + index * stepX
            val normalizedY = ((value - minVal) / range).toFloat()
            val y = padding + (1f - normalizedY) * usableHeight

            if (index == 0) {
                strokePath.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                strokePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (index == points.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        // Draw gradient under the curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    strokeColor.copy(alpha = 0.25f),
                    strokeColor.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = height
            )
        )

        // Draw line
        drawPath(
            path = strokePath,
            color = strokeColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
