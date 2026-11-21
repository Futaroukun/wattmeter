package com.antigravity.wattmeter.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wattmeter.ui.theme.CyanPrimary
import com.antigravity.wattmeter.ui.theme.TealSecondary
import com.antigravity.wattmeter.ui.theme.WarningAmber
import java.util.Locale
import kotlin.math.max

@Composable
fun WattGauge(
    watt: Double,
    smoothedWatt: Double,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
    isEstimated: Boolean = false
) {
    val displayWatt = max(0.0, watt)
    val maxWatt = when {
        displayWatt > 100.0 -> 140f
        displayWatt > 65.0 -> 100f
        displayWatt > 33.0 -> 65f
        displayWatt > 18.0 -> 33f
        else -> 20f
    }

    val progressFraction = (displayWatt.toFloat() / maxWatt).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "WattProgress"
    )

    val startAngle = 140f
    val sweepAngle = 260f

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val activeGradient = Brush.sweepGradient(
        0.0f to CyanPrimary,
        0.5f to TealSecondary,
        1.0f to WarningAmber
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 18.dp.toPx()
            val diameter = minOf(this.size.width, this.size.height) - strokeWidthPx
            val topLeft = Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            if (animatedProgress > 0.001f) {
                drawArc(
                    brush = activeGradient,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            val tickCount = 10
            for (i in 0..tickCount) {
                val tickFraction = i.toFloat() / tickCount
                val tickAngleDeg = startAngle + sweepAngle * tickFraction
                val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                val outerRadius = diameter / 2f
                val innerRadius = outerRadius - 8.dp.toPx()
                val center = Offset(this.size.width / 2f, this.size.height / 2f)

                val start = Offset(
                    x = (center.x + innerRadius * kotlin.math.cos(tickAngleRad)).toFloat(),
                    y = (center.y + innerRadius * kotlin.math.sin(tickAngleRad)).toFloat()
                )
                val end = Offset(
                    x = (center.x + outerRadius * kotlin.math.cos(tickAngleRad)).toFloat(),
                    y = (center.y + outerRadius * kotlin.math.sin(tickAngleRad)).toFloat()
                )

                val tickActive = tickFraction <= animatedProgress
                drawLine(
                    color = if (tickActive) Color.White.copy(alpha = 0.8f) else trackColor.copy(alpha = 0.5f),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isCharging) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Active Charge",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "CHARGING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = CyanPrimary
                    )
                }
            } else {
                Text(
                    text = "DISCHARGING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = String.format(Locale.US, "%.1f", displayWatt),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "WATTS",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            if (smoothedWatt > 0.0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "Avg %.1f W", max(0.0, smoothedWatt)),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "0 - ${maxWatt.toInt()}W Scale" + if (isEstimated) " • Est." else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
