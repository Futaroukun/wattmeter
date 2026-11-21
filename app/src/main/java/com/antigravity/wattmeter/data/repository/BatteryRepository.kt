package com.antigravity.wattmeter.data.repository

import android.content.Context
import com.antigravity.wattmeter.data.local.AppDatabase
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.ChargingSession
import com.antigravity.wattmeter.data.model.DiagnosticInfo
import com.antigravity.wattmeter.data.model.UserSettings
import com.antigravity.wattmeter.data.preferences.PreferencesManager
import com.antigravity.wattmeter.util.MovingAverage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BatteryRepository(
    private val context: Context,
    private val database: AppDatabase,
    val preferencesManager: PreferencesManager,
    private val batteryReader: BatteryReader,
    private val repositoryScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _snapshotFlow = MutableStateFlow(BatterySnapshot())
    val snapshotFlow: StateFlow<BatterySnapshot> = _snapshotFlow.asStateFlow()

    private val _diagnosticFlow = MutableStateFlow(DiagnosticInfo())
    val diagnosticFlow: StateFlow<DiagnosticInfo> = _diagnosticFlow.asStateFlow()

    private val _isChargingFlow = MutableStateFlow(false)
    val isChargingFlow: StateFlow<Boolean> = _isChargingFlow.asStateFlow()

    val allSessions: Flow<List<ChargingSession>> = database.sessionDao().getAllSessions()

    private var movingAverage = MovingAverage(5)

    @Volatile
    private var currentSettings: UserSettings = UserSettings()

    private var isTrackingSession = false
    private var sessionStartTime = 0L
    private var sessionStartLevel = 0
    private var sessionPlugType = ""
    private var sessionPeakWatt = 0.0
    private var sessionMinWatt = Double.MAX_VALUE
    private var sessionSumWatt = 0.0
    private var sessionWattCount = 0L
    private var sessionPeakCurrentMa = 0.0
    private var sessionSumCurrentMa = 0.0
    private var sessionMaxTemp = 0.0f
    private var sessionLastTimestamp = 0L
    private var sessionEnergyWh = 0.0

    private var pollingJob: Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = repositoryScope.launch {
            preferencesManager.userSettingsFlow.collectLatest { settings ->
                currentSettings = settings
                if (movingAverage.windowSize != settings.movingAvgWindow) {
                    movingAverage = MovingAverage(settings.movingAvgWindow)
                }

                while (isActive) {
                    poll(settings)
                    delay(settings.pollingIntervalMs.coerceAtLeast(250L))
                }
            }
        }
    }

    fun refreshNow() {
        repositoryScope.launch {
            poll(currentSettings)
        }
    }

    private fun poll(settings: UserSettings) {
        val (rawSnapshot, diagnostic) = batteryReader.readBattery(settings)
        val smoothedWatt = movingAverage.add(rawSnapshot.watt)
        val snapshot = rawSnapshot.copy(smoothedWatt = smoothedWatt)

        _snapshotFlow.value = snapshot
        _diagnosticFlow.value = diagnostic
        _isChargingFlow.value = snapshot.isCharging

        handleChargingSession(snapshot)
    }

    private fun handleChargingSession(snapshot: BatterySnapshot) {
        if (snapshot.isCharging) {
            if (!isTrackingSession) {
                isTrackingSession = true
                sessionStartTime = snapshot.timestamp
                sessionStartLevel = snapshot.levelPercent
                sessionPlugType = snapshot.plugType
                sessionPeakWatt = snapshot.watt
                sessionMinWatt = snapshot.watt
                sessionSumWatt = snapshot.watt
                sessionWattCount = 1L
                sessionPeakCurrentMa = abs(snapshot.currentNowMa)
                sessionSumCurrentMa = abs(snapshot.currentNowMa)
                sessionMaxTemp = snapshot.temperatureCelsius
                sessionLastTimestamp = snapshot.timestamp
                sessionEnergyWh = 0.0
            } else {
                val currentWatt = snapshot.watt
                sessionPeakWatt = max(sessionPeakWatt, currentWatt)
                if (currentWatt > 0.0) {
                    sessionMinWatt = min(sessionMinWatt, currentWatt)
                }
                sessionSumWatt += currentWatt
                val currentMaAbs = abs(snapshot.currentNowMa)
                sessionPeakCurrentMa = max(sessionPeakCurrentMa, currentMaAbs)
                sessionSumCurrentMa += currentMaAbs
                sessionWattCount++
                sessionMaxTemp = max(sessionMaxTemp, snapshot.temperatureCelsius)

                if (sessionLastTimestamp > 0L && snapshot.timestamp > sessionLastTimestamp) {
                    val deltaHours = (snapshot.timestamp - sessionLastTimestamp) / 3600000.0
                    sessionEnergyWh += currentWatt * deltaHours
                }
                sessionLastTimestamp = snapshot.timestamp
            }
        } else {
            if (isTrackingSession) {
                isTrackingSession = false
                if (sessionWattCount > 0L) {
                    val avgWatt = sessionSumWatt / sessionWattCount
                    val avgCurrentMa = sessionSumCurrentMa / sessionWattCount
                    val resolvedMinWatt = if (sessionMinWatt == Double.MAX_VALUE) 0.0 else sessionMinWatt

                    val session = ChargingSession(
                        startTime = sessionStartTime,
                        endTime = snapshot.timestamp,
                        startLevel = sessionStartLevel,
                        endLevel = snapshot.levelPercent,
                        peakWatt = sessionPeakWatt,
                        avgWatt = avgWatt,
                        minWatt = resolvedMinWatt,
                        peakCurrentMa = sessionPeakCurrentMa,
                        avgCurrentMa = avgCurrentMa,
                        maxTemperature = sessionMaxTemp,
                        plugType = sessionPlugType,
                        energyWh = sessionEnergyWh
                    )

                    repositoryScope.launch(Dispatchers.IO) {
                        database.sessionDao().insert(session)
                    }
                }
                resetSessionTracking()
            }
        }
    }

    private fun resetSessionTracking() {
        isTrackingSession = false
        sessionStartTime = 0L
        sessionStartLevel = 0
        sessionPlugType = ""
        sessionPeakWatt = 0.0
        sessionMinWatt = Double.MAX_VALUE
        sessionSumWatt = 0.0
        sessionWattCount = 0L
        sessionPeakCurrentMa = 0.0
        sessionSumCurrentMa = 0.0
        sessionMaxTemp = 0.0f
        sessionLastTimestamp = 0L
        sessionEnergyWh = 0.0
    }

    suspend fun getSessionById(id: Long): ChargingSession? {
        return database.sessionDao().getSessionById(id)
    }

    suspend fun deleteSession(session: ChargingSession) {
        database.sessionDao().delete(session)
    }

    suspend fun clearAllSessions() {
        database.sessionDao().clearAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: BatteryRepository? = null

        fun getInstance(context: Context): BatteryRepository {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val db = AppDatabase.getDatabase(appCtx)
                val prefs = PreferencesManager(appCtx)
                val reader = BatteryReader(appCtx)
                val instance = BatteryRepository(appCtx, db, prefs, reader)
                INSTANCE = instance
                instance
            }
        }
    }
}
