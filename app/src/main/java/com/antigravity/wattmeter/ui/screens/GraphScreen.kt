package com.antigravity.wattmeter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.wattmeter.ui.components.MetricCard
import com.antigravity.wattmeter.ui.components.RealtimeChart
import com.antigravity.wattmeter.ui.theme.CyanPrimary
import com.antigravity.wattmeter.ui.theme.DangerRed
import com.antigravity.wattmeter.ui.theme.TealSecondary
import com.antigravity.wattmeter.ui.theme.WarningAmber
import com.antigravity.wattmeter.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Power (W)", "Current (mA)", "Temp (°C)")

    val wattHistory by viewModel.wattHistory.collectAsState()
    val currentHistory by viewModel.currentHistory.collectAsState()
    val tempHistory by viewModel.tempHistory.collectAsState()

    val peakWatt by viewModel.sessionPeakWatt.collectAsState()
    val avgWatt by viewModel.sessionAvgWatt.collectAsState()
    val minWatt by viewModel.sessionMinWatt.collectAsState()
    val peakCurrent by viewModel.sessionPeakCurrentMa.collectAsState()
    val maxTemp by viewModel.sessionMaxTemp.collectAsState()
    val energyWh by viewModel.sessionEnergyWh.collectAsState()

    val (activeData, activeUnit, activeColor) = when (selectedTab) {
        0 -> Triple(wattHistory, "W", CyanPrimary)
        1 -> Triple(currentHistory, "mA", TealSecondary)
        else -> Triple(tempHistory, "°C", WarningAmber)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Real-Time Telemetry",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        RealtimeChart(
            dataPoints = activeData,
            unit = activeUnit,
            lineColor = activeColor,
            height = 240.dp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Active Session Statistics",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "PEAK POWER",
                value = String.format(Locale.US, "%.1f", peakWatt),
                unit = "W",
                accentColor = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "AVERAGE POWER",
                value = String.format(Locale.US, "%.1f", avgWatt),
                unit = "W",
                accentColor = TealSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "MIN POWER",
                value = String.format(Locale.US, "%.1f", minWatt),
                unit = "W",
                accentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "PEAK CURRENT",
                value = String.format(Locale.US, "%.0f", peakCurrent),
                unit = "mA",
                accentColor = CyanPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "MAX TEMPERATURE",
                value = String.format(Locale.US, "%.1f", maxTemp),
                unit = "°C",
                accentColor = if (maxTemp >= 40f) DangerRed else WarningAmber,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "ENERGY DELIVERED",
                value = String.format(Locale.US, "%.2f", energyWh),
                unit = "Wh",
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
