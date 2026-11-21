package com.antigravity.wattmeter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wattmeter.ui.components.DiagnosticBanner
import com.antigravity.wattmeter.ui.components.MetricCard
import com.antigravity.wattmeter.ui.components.WattGauge
import com.antigravity.wattmeter.ui.theme.CyanPrimary
import com.antigravity.wattmeter.ui.theme.DangerRed
import com.antigravity.wattmeter.ui.theme.SuccessGreen
import com.antigravity.wattmeter.ui.theme.TealSecondary
import com.antigravity.wattmeter.ui.theme.WarningAmber
import com.antigravity.wattmeter.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snapshot by viewModel.snapshot.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()
    val sessionDuration by viewModel.sessionDurationSeconds.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    val scrollState = rememberScrollState()

    val durationText = if (snapshot.isCharging && sessionDuration > 0) {
        val hours = sessionDuration / 3600
        val mins = (sessionDuration % 3600) / 60
        val secs = sessionDuration % 60
        if (hours > 0) String.format(Locale.US, "%dh %02dm", hours, mins)
        else String.format(Locale.US, "%dm %02ds", mins, secs)
    } else {
        "--"
    }

    val etaText = if (snapshot.isCharging && snapshot.levelPercent < 100 && kotlin.math.abs(snapshot.currentNowMa) > 50.0) {
        val capacity = if (snapshot.capacityDesignMah > 0) snapshot.capacityDesignMah else 4500
        val remainingMah = capacity * (100 - snapshot.levelPercent) / 100.0
        val hoursRemaining = remainingMah / kotlin.math.abs(snapshot.currentNowMa)
        val totalMinutes = (hoursRemaining * 60).toInt().coerceIn(1, 1440)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        if (h > 0) "~${h}h ${m}m" else "~${m} min"
    } else if (snapshot.levelPercent >= 100) {
        "Full"
    } else {
        "--"
    }

    val tempColor = when {
        snapshot.temperatureCelsius >= 42.0f -> DangerRed
        snapshot.temperatureCelsius >= 37.0f -> WarningAmber
        else -> SuccessGreen
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Wattmeter",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (snapshot.isCharging) "Power Source: ${snapshot.plugType}" else "On Battery",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (snapshot.isCharging) SuccessGreen.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (snapshot.isCharging) SuccessGreen else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (snapshot.isCharging) "CHARGING" else "DISCHARGING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = if (snapshot.isCharging) SuccessGreen else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        WattGauge(
            watt = snapshot.watt,
            smoothedWatt = snapshot.smoothedWatt,
            isCharging = snapshot.isCharging,
            size = 230.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        if ((snapshot.isCharging && snapshot.currentNowMa == 0.0) || diagnostic.isZeroCurrentWarning) {
            DiagnosticBanner(
                diagnosticInfo = diagnostic,
                onToggleInvertSign = { viewModel.toggleInvertSign() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "CURRENT",
                value = String.format(Locale.US, "%.0f", snapshot.currentNowMa),
                unit = "mA",
                icon = Icons.Default.Bolt,
                accentColor = CyanPrimary,
                subtitle = if (snapshot.isCharging) "Charging flow" else "Discharge rate",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "VOLTAGE",
                value = String.format(Locale.US, "%.2f", snapshot.voltageVolt),
                unit = "V",
                icon = Icons.Default.FlashOn,
                accentColor = TealSecondary,
                subtitle = "Cell potential",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "TEMPERATURE",
                value = String.format(Locale.US, "%.1f", snapshot.temperatureCelsius),
                unit = "°C",
                icon = Icons.Default.Thermostat,
                accentColor = tempColor,
                subtitle = if (snapshot.temperatureCelsius >= 40f) "High temp" else "Normal range",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "BATTERY LEVEL",
                value = "${snapshot.levelPercent}",
                unit = "%",
                icon = Icons.Default.BatteryChargingFull,
                accentColor = SuccessGreen,
                subtitle = "Health: ${snapshot.health}",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "DURATION",
                value = durationText,
                unit = "",
                icon = Icons.Default.Schedule,
                accentColor = MaterialTheme.colorScheme.tertiary,
                subtitle = if (snapshot.isCharging) "Charging session" else "Idle timer",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "ESTIMATED FULL",
                value = etaText,
                unit = "",
                icon = Icons.Default.Power,
                accentColor = WarningAmber,
                subtitle = if (snapshot.isCharging && snapshot.levelPercent < 100) "Target 100%" else "Battery full",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = { viewModel.toggleMonitoringService(context) },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .height(46.dp),
            shape = RoundedCornerShape(23.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) DangerRed else MaterialTheme.colorScheme.primary,
                contentColor = if (isServiceRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = "Toggle Monitor",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isServiceRunning) "Stop Foreground Monitor" else "Start Foreground Monitor",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
