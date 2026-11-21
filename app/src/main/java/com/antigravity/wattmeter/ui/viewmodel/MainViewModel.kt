package com.antigravity.wattmeter.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.antigravity.wattmeter.WattmeterApp
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.ChargingSession
import com.antigravity.wattmeter.data.model.DiagnosticInfo
import com.antigravity.wattmeter.data.model.NotificationMetric
import com.antigravity.wattmeter.data.model.ThemeMode
import com.antigravity.wattmeter.data.model.UnitMultiplier
import com.antigravity.wattmeter.data.model.UserSettings
import com.antigravity.wattmeter.data.repository.BatteryRepository
import com.antigravity.wattmeter.service.ChargingMonitorService
import com.antigravity.wattmeter.util.CsvExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class MainViewModel(
    application: Application,
    private val repository: BatteryRepository = WattmeterApp.instance.batteryRepository
) : AndroidViewModel(application) {

    val snapshot: StateFlow<BatterySnapshot> = repository.snapshotFlow
    val diagnostic: StateFlow<DiagnosticInfo> = repository.diagnosticFlow
    val isCharging: StateFlow<Boolean> = repository.isChargingFlow
    val userSettings: StateFlow<UserSettings> = repository.preferencesManager.userSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings()
    )

    val sessions: StateFlow<List<ChargingSession>> = repository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _wattHistory = MutableStateFlow<List<Double>>(emptyList())
    val wattHistory: StateFlow<List<Double>> = _wattHistory.asStateFlow()

    private val _currentHistory = MutableStateFlow<List<Double>>(emptyList())
    val currentHistory: StateFlow<List<Double>> = _currentHistory.asStateFlow()

    private val _tempHistory = MutableStateFlow<List<Double>>(emptyList())
    val tempHistory: StateFlow<List<Double>> = _tempHistory.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private val _sessionPeakWatt = MutableStateFlow(0.0)
    val sessionPeakWatt: StateFlow<Double> = _sessionPeakWatt.asStateFlow()

    private val _sessionAvgWatt = MutableStateFlow(0.0)
    val sessionAvgWatt: StateFlow<Double> = _sessionAvgWatt.asStateFlow()

    private val _sessionMinWatt = MutableStateFlow(0.0)
    val sessionMinWatt: StateFlow<Double> = _sessionMinWatt.asStateFlow()

    private val _sessionPeakCurrentMa = MutableStateFlow(0.0)
    val sessionPeakCurrentMa: StateFlow<Double> = _sessionPeakCurrentMa.asStateFlow()

    private val _sessionMaxTemp = MutableStateFlow(0.0f)
    val sessionMaxTemp: StateFlow<Float> = _sessionMaxTemp.asStateFlow()

    private val _sessionEnergyWh = MutableStateFlow(0.0)
    val sessionEnergyWh: StateFlow<Double> = _sessionEnergyWh.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    fun syncServiceState() {
        _isServiceRunning.value = ChargingMonitorService.isRunning
    }

    private val maxBufferPoints = 120
    private var sessionStartTime = 0L
    private var wattSum = 0.0
    private var wattSampleCount = 0
    private var lastSampleTime = 0L

    init {
        viewModelScope.launch {
            repository.snapshotFlow.collect { snap ->
                syncServiceState()
                val watt = snap.watt
                val current = snap.currentNowMa
                val temp = snap.temperatureCelsius.toDouble()

                _wattHistory.value = (_wattHistory.value + watt).takeLast(maxBufferPoints)
                _currentHistory.value = (_currentHistory.value + current).takeLast(maxBufferPoints)
                _tempHistory.value = (_tempHistory.value + temp).takeLast(maxBufferPoints)

                if (snap.isCharging) {
                    val now = snap.timestamp
                    if (sessionStartTime == 0L) {
                        sessionStartTime = now
                        lastSampleTime = now
                        _sessionPeakWatt.value = watt
                        _sessionMinWatt.value = watt
                        _sessionPeakCurrentMa.value = kotlin.math.abs(current)
                        _sessionMaxTemp.value = snap.temperatureCelsius
                        wattSum = watt
                        wattSampleCount = 1
                        _sessionEnergyWh.value = 0.0
                    } else {
                        val dtHours = (now - lastSampleTime).coerceAtLeast(0L) / 3600000.0
                        _sessionEnergyWh.value += watt * dtHours
                        lastSampleTime = now

                        _sessionPeakWatt.value = max(_sessionPeakWatt.value, watt)
                        if (watt > 0.1) {
                            _sessionMinWatt.value = if (_sessionMinWatt.value <= 0.1) watt else min(_sessionMinWatt.value, watt)
                        }
                        _sessionPeakCurrentMa.value = max(_sessionPeakCurrentMa.value, kotlin.math.abs(current))
                        _sessionMaxTemp.value = max(_sessionMaxTemp.value, snap.temperatureCelsius)
                        wattSum += watt
                        wattSampleCount++
                        _sessionAvgWatt.value = wattSum / wattSampleCount
                    }
                    _sessionDurationSeconds.value = (now - sessionStartTime) / 1000L
                } else {
                    sessionStartTime = 0L
                    lastSampleTime = 0L
                    wattSum = 0.0
                    wattSampleCount = 0
                    _sessionDurationSeconds.value = 0L
                }
            }
        }
    }

    fun toggleInvertSign() {
        viewModelScope.launch {
            val currentVal = userSettings.value.invertCurrentSign
            repository.preferencesManager.setInvertCurrentSign(!currentVal)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.preferencesManager.setThemeMode(mode)
        }
    }

    fun setPollingInterval(intervalMs: Long) {
        viewModelScope.launch {
            repository.preferencesManager.setPollingIntervalMs(intervalMs)
        }
    }

    fun setUnitMultiplier(multiplier: UnitMultiplier) {
        viewModelScope.launch {
            repository.preferencesManager.setUnitMultiplier(multiplier)
        }
    }

    fun setMovingAvgWindow(window: Int) {
        viewModelScope.launch {
            repository.preferencesManager.setMovingAvgWindow(window)
        }
    }

    fun setNotificationMetric(metric: NotificationMetric) {
        viewModelScope.launch {
            repository.preferencesManager.setNotificationMetric(metric)
        }
    }

    fun setAlarmFullBattery(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesManager.setAlarmFullBattery(enabled)
        }
    }

    fun setAlarmLevelEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesManager.setAlarmLevelEnabled(enabled)
        }
    }

    fun setAlarmLevelThreshold(threshold: Int) {
        viewModelScope.launch {
            repository.preferencesManager.setAlarmLevelThreshold(threshold)
        }
    }

    fun setAlarmTempEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesManager.setAlarmTempEnabled(enabled)
        }
    }

    fun setAlarmTempThreshold(threshold: Float) {
        viewModelScope.launch {
            repository.preferencesManager.setAlarmTempThreshold(threshold)
        }
    }

    fun setEstimateCurrentIfZero(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesManager.setEstimateCurrentIfZero(enabled)
        }
    }

    fun setChargerWattageRating(wattage: Int) {
        viewModelScope.launch {
            repository.preferencesManager.setChargerWattageRating(wattage)
        }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesManager.setAutoStartOnBoot(enabled)
        }
    }

    fun setAutoStartOnPlug(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesManager.setAutoStartOnPlug(enabled)
        }
    }

    fun deleteSession(session: ChargingSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            repository.clearAllSessions()
        }
    }

    fun exportCsv(context: Context): Boolean {
        return CsvExporter.exportAndShare(context, sessions.value)
    }

    fun toggleMonitoringService(context: Context) {
        val intent = Intent(context, ChargingMonitorService::class.java)
        if (ChargingMonitorService.isRunning) {
            intent.action = ChargingMonitorService.ACTION_STOP
            context.startService(intent)
            _isServiceRunning.value = false
        } else {
            intent.action = ChargingMonitorService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isServiceRunning.value = true
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
