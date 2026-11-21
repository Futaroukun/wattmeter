package com.antigravity.wattmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.wattmeter.data.model.NotificationMetric
import com.antigravity.wattmeter.data.model.ThemeMode
import com.antigravity.wattmeter.data.model.UnitMultiplier
import com.antigravity.wattmeter.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val userSettings by viewModel.userSettings.collectAsState()
    val scrollState = rememberScrollState()

    var themeMenuExpanded by remember { mutableStateOf(false) }
    var pollingMenuExpanded by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }
    var windowMenuExpanded by remember { mutableStateOf(false) }
    var metricMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Preferences, measurement telemetry, and alarms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionCard(title = "Appearance & Interface") {
            ExposedDropdownMenuBox(
                expanded = themeMenuExpanded,
                onExpandedChange = { themeMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = userSettings.themeMode.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = themeMenuExpanded,
                    onDismissRequest = { themeMenuExpanded = false }
                ) {
                    ThemeMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name) },
                            onClick = {
                                viewModel.setThemeMode(mode)
                                themeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionCard(title = "Sampling & Calibration") {
            ExposedDropdownMenuBox(
                expanded = pollingMenuExpanded,
                onExpandedChange = { pollingMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${userSettings.pollingIntervalMs} ms",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Polling Interval") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pollingMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = pollingMenuExpanded,
                    onDismissRequest = { pollingMenuExpanded = false }
                ) {
                    listOf(500L, 1000L, 2000L, 5000L).forEach { interval ->
                        DropdownMenuItem(
                            text = { Text("$interval ms") },
                            onClick = {
                                viewModel.setPollingInterval(interval)
                                pollingMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = unitMenuExpanded,
                onExpandedChange = { unitMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = userSettings.unitMultiplier.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unit Multiplier") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = unitMenuExpanded,
                    onDismissRequest = { unitMenuExpanded = false }
                ) {
                    UnitMultiplier.values().forEach { multiplier ->
                        DropdownMenuItem(
                            text = { Text(multiplier.name) },
                            onClick = {
                                viewModel.setUnitMultiplier(multiplier)
                                unitMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = windowMenuExpanded,
                onExpandedChange = { windowMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = "${userSettings.movingAvgWindow} samples",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Moving Average Window") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = windowMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = windowMenuExpanded,
                    onDismissRequest = { windowMenuExpanded = false }
                ) {
                    listOf(1, 3, 5, 10, 15, 20).forEach { size ->
                        DropdownMenuItem(
                            text = { Text("$size samples") },
                            onClick = {
                                viewModel.setMovingAvgWindow(size)
                                windowMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Invert Current Sign",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reverse negative/positive current if device inverts readings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = userSettings.invertCurrentSign,
                    onCheckedChange = { viewModel.toggleInvertSign() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionCard(title = "Notification & Service") {
            ExposedDropdownMenuBox(
                expanded = metricMenuExpanded,
                onExpandedChange = { metricMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = userSettings.notificationMetric.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Notification Subtitle Metric") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = metricMenuExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = metricMenuExpanded,
                    onDismissRequest = { metricMenuExpanded = false }
                ) {
                    NotificationMetric.values().forEach { metric ->
                        DropdownMenuItem(
                            text = { Text(metric.name) },
                            onClick = {
                                viewModel.setNotificationMetric(metric)
                                metricMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Start on Boot",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Launch background monitor when Android boots up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = userSettings.autoStartOnBoot,
                    onCheckedChange = { viewModel.setAutoStartOnBoot(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Start on Plug",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically monitor when charger is connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = userSettings.autoStartOnPlug,
                    onCheckedChange = { viewModel.setAutoStartOnPlug(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSectionCard(title = "Alarms & Threshold Alerts") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Full Battery Alarm",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sound alarm when battery reaches 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = userSettings.alarmFullBattery,
                    onCheckedChange = { viewModel.setAlarmFullBattery(it) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Battery Level Alarm Threshold",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${userSettings.alarmLevelThreshold}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = userSettings.alarmLevelThreshold.toFloat(),
                    onValueChange = { viewModel.setAlarmLevelThreshold(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 9
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Overheat Alarm Threshold",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f °C", userSettings.alarmTempThreshold),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Slider(
                    value = userSettings.alarmTempThreshold,
                    onValueChange = { viewModel.setAlarmTempThreshold(it) },
                    valueRange = 35f..55f,
                    steps = 19
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionCard(
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
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
