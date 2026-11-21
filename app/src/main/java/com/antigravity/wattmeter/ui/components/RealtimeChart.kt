package com.antigravity.wattmeter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun RealtimeChart(
    dataPoints: List<Double>,
    unit: String,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val textPaintColor = MaterialTheme.colorScheme.onSurfaceVariant.hashCode()

    val (minVal, maxVal) = remember(dataPoints) {
        if (dataPoints.isEmpty()) {
            Pair(0.0, 10.0)
        } else {
            val actualMin = dataPoints.minOrNull() ?: 0.0
            val actualMax = dataPoints.maxOrNull() ?: 10.0
            val range = actualMax - actualMin
            val padding = if (range < 0.1) 1.0 else range * 0.15
            Pair(max(0.0, actualMin - padding), actualMax + padding)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        if (dataPoints.size < 2) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Collecting real-time data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val chartHeight = size.height
                val gridLines = 4
                val valRange = if (maxVal - minVal <= 0.001) 1.0 else maxVal - minVal

                val textPaint = android.graphics.Paint().apply {
                    color = textPaintColor
                    textSize = 10.sp.toPx()
                    isAntiAlias = true
                }

                for (i in 0..gridLines) {
                    val y = chartHeight * (i.toFloat() / gridLines)
                    val valueAtLine = maxVal - (valRange * (i.toDouble() / gridLines))

                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.US, "%.1f %s", valueAtLine, unit),
                        8.dp.toPx(),
                        if (y + 12.dp.toPx() > chartHeight) y - 4.dp.toPx() else y + 12.dp.toPx(),
                        textPaint
                    )
                }

                val stepX = width / (dataPoints.size - 1)
                val path = Path()
                val fillPath = Path()

                var prevX = 0f
                var prevY = 0f

                dataPoints.forEachIndexed { index, value ->
                    val x = index * stepX
                    val normalizedY = ((value - minVal) / valRange).coerceIn(0.0, 1.0).toFloat()
                    val y = chartHeight - (normalizedY * chartHeight)

                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, chartHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        val controlX = (prevX + x) / 2f
                        path.cubicTo(controlX, prevY, controlX, y, x, y)
                        fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
                    }

                    prevX = x
                    prevY = y
                }

                fillPath.lineTo(width, chartHeight)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.35f),
                            lineColor.copy(alpha = 0.0f)
                        ),
                        startY = 0f,
                        endY = chartHeight
                    )
                )

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                val actualMax = dataPoints.maxOrNull() ?: maxVal
                val maxNorm = ((actualMax - minVal) / valRange).coerceIn(0.0, 1.0).toFloat()
                val maxY = chartHeight - (maxNorm * chartHeight)
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    start = Offset(0f, maxY),
                    end = Offset(width, maxY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
        }
    }
}
