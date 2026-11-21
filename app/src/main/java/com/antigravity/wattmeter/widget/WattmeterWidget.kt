package com.antigravity.wattmeter.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.antigravity.wattmeter.MainActivity
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.UserSettings
import com.antigravity.wattmeter.data.repository.BatteryReader
import com.antigravity.wattmeter.data.repository.BatteryRepository
import java.util.Locale

class WattmeterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = BatteryRepository.getInstance(context)
        val snapshot = repo.snapshotFlow.value
        val displaySnapshot = if (snapshot.timestamp > 0L) {
            snapshot
        } else {
            BatteryReader(context).readBattery(UserSettings()).first
        }

        provideContent {
            WidgetContent(context, displaySnapshot)
        }
    }

    @Composable
    private fun WidgetContent(context: Context, snapshot: BatterySnapshot) {
        val isCharging = snapshot.isCharging
        val backgroundColor = Color(0xFF121417)
        val cardColor = Color(0xFF1B1F23)
        val accentColor = if (isCharging) Color(0xFF00E5FF) else Color(0xFF90A4AE)
        val textPrimary = Color(0xFFECEFF1)
        val textSecondary = Color(0xFF78909C)
        val chipBackground = if (isCharging) Color(0xFF004D40) else Color(0xFF263238)
        val chipText = if (isCharging) Color(0xFF64FFDA) else Color(0xFFB0BEC5)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundColor)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(launchIntent))
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wattmeter",
                        style = TextStyle(
                            color = ColorProvider(textPrimary),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .background(chipBackground)
                            .cornerRadius(6.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCharging) "⚡ Charging" else "Discharging",
                            style = TextStyle(
                                color = ColorProvider(chipText),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", snapshot.smoothedWatt),
                        style = TextStyle(
                            color = ColorProvider(accentColor),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "W",
                        style = TextStyle(
                            color = ColorProvider(accentColor),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(cardColor)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "LEVEL",
                                style = TextStyle(
                                    color = ColorProvider(textSecondary),
                                    fontSize = 9.sp
                                )
                            )
                            Text(
                                text = "${snapshot.levelPercent}%",
                                style = TextStyle(
                                    color = ColorProvider(textPrimary),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "CURRENT",
                                style = TextStyle(
                                    color = ColorProvider(textSecondary),
                                    fontSize = 9.sp
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%.0fmA", snapshot.currentNowMa),
                                style = TextStyle(
                                    color = ColorProvider(textPrimary),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "TEMP",
                                style = TextStyle(
                                    color = ColorProvider(textSecondary),
                                    fontSize = 9.sp
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f°C", snapshot.temperatureCelsius),
                                style = TextStyle(
                                    color = ColorProvider(textPrimary),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "VOLT",
                                style = TextStyle(
                                    color = ColorProvider(textSecondary),
                                    fontSize = 9.sp
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%.2fV", snapshot.voltageVolt),
                                style = TextStyle(
                                    color = ColorProvider(textPrimary),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
