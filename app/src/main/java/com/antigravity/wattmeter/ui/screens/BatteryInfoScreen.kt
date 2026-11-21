package com.antigravity.wattmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.wattmeter.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryInfoScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val diagnostic by viewModel.diagnostic.collectAsState()
    val scrollState = rememberScrollState()

    val capacityStr = if (snapshot.capacityDesignMah > 0) "${snapshot.capacityDesignMah} mAh" else "Not Reported"
    val chargeCounterStr = if (snapshot.chargeCounterUah > 0) "${snapshot.chargeCounterUah / 1000} mAh (${snapshot.chargeCounterUah} µAh)" else "Not Reported"
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Battery Information",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Hardware specifications and internal battery telemetry",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfoSectionCard(title = "Battery Health & Specs") {
            InfoRow(label = "Health", value = snapshot.health.ifEmpty { "Good" })
            InfoRow(label = "Status", value = snapshot.status.ifEmpty { "Discharging" })
            InfoRow(label = "Technology", value = snapshot.technology.ifEmpty { "Li-ion" })
            InfoRow(label = "Design Capacity", value = capacityStr)
            InfoRow(label = "Charge Counter", value = chargeCounterStr)
            InfoRow(label = "Battery Level", value = "${snapshot.levelPercent}%")
        }

        Spacer(modifier = Modifier.height(16.dp))

        InfoSectionCard(title = "Electrical Metrics") {
            InfoRow(label = "Instant Power", value = String.format(Locale.US, "%.2f W", snapshot.watt))
            InfoRow(label = "Smoothed Power", value = String.format(Locale.US, "%.2f W", snapshot.smoothedWatt))
            InfoRow(label = "Current Now", value = String.format(Locale.US, "%.1f mA", snapshot.currentNowMa))
            InfoRow(label = "Terminal Voltage", value = String.format(Locale.US, "%.3f V", snapshot.voltageVolt))
            InfoRow(label = "Temperature", value = String.format(Locale.US, "%.1f °C", snapshot.temperatureCelsius))
        }

        Spacer(modifier = Modifier.height(16.dp))

        InfoSectionCard(title = "Sensor & Data Source") {
            InfoRow(label = "Data Source", value = snapshot.currentDataSource.ifEmpty { "BatteryManager" })
            InfoRow(label = "Power Source", value = snapshot.plugType.ifEmpty { "Battery" })
            InfoRow(label = "Detected Scale Unit", value = diagnostic.detectedUnit.ifEmpty { "mA" })
            InfoRow(label = "Sign Inverted", value = if (diagnostic.signReversed) "Yes" else "No")
            InfoRow(label = "Last Polled", value = timeFormat.format(Date(snapshot.timestamp)))
        }

        if (diagnostic.sysfsChecked.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            InfoSectionCard(title = "Linux Kernel Sysfs Probes") {
                diagnostic.sysfsChecked.forEach { (path, status) ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = path,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun InfoSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
