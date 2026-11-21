package com.antigravity.wattmeter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import com.antigravity.wattmeter.MainActivity
import com.antigravity.wattmeter.R
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.NotificationMetric
import com.antigravity.wattmeter.data.model.UserSettings
import com.antigravity.wattmeter.data.preferences.PreferencesManager
import com.antigravity.wattmeter.data.repository.BatteryRepository
import com.antigravity.wattmeter.widget.WattmeterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class ChargingMonitorService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var batteryRepository: BatteryRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var alarmHandler: AlarmHandler
    private lateinit var notificationManager: NotificationManager

    private var currentSettings = UserSettings()
    private var hasBeenPlugged = false
    private var unplugJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        batteryRepository = BatteryRepository.getInstance(applicationContext)
        preferencesManager = PreferencesManager(applicationContext)
        alarmHandler = AlarmHandler(this)

        createNotificationChannels()
        startObservingData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        } else if (action == ACTION_DISMISS_ALARM) {
            alarmHandler.stopAlarm()
            return START_STICKY
        }

        startForegroundWithNotification()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        alarmHandler.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitoringChannel = NotificationChannel(
                CHANNEL_ID_MONITORING,
                "Wattmeter Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active charging wattage monitoring"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(monitoringChannel)
        }
    }

    private fun startForegroundWithNotification() {
        val snapshot = latestSnapshot ?: BatterySnapshot()
        val notification = buildMonitoringNotification(snapshot, currentSettings)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(
                    NOTIFICATION_ID_MONITORING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } catch (e: Exception) {
                startForeground(NOTIFICATION_ID_MONITORING, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID_MONITORING, notification)
        }
        isRunning = true
    }

    private fun startObservingData() {
        serviceScope.launch {
            preferencesManager.userSettingsFlow.collectLatest { settings ->
                currentSettings = settings
                latestSnapshot?.let { snapshot ->
                    updateNotification(snapshot, settings)
                }
            }
        }

        serviceScope.launch {
            batteryRepository.snapshotFlow.collectLatest { snapshot ->
                latestSnapshot = snapshot
                handlePlugState(snapshot)
                alarmHandler.checkThresholds(snapshot, currentSettings)
                updateNotification(snapshot, currentSettings)
                triggerWidgetUpdate()
            }
        }
    }

    private fun handlePlugState(snapshot: BatterySnapshot) {
        val plugged = snapshot.plugType.isNotEmpty() && snapshot.plugType != "Battery"
        if (plugged) {
            hasBeenPlugged = true
            unplugJob?.cancel()
            unplugJob = null
        } else if (hasBeenPlugged && unplugJob == null) {
            unplugJob = serviceScope.launch {
                delay(UNPLUG_GRACE_MS)
                stopMonitoring()
            }
        }
    }

    private fun updateNotification(snapshot: BatterySnapshot, settings: UserSettings) {
        if (!isRunning) return
        val notification = buildMonitoringNotification(snapshot, settings)
        notificationManager.notify(NOTIFICATION_ID_MONITORING, notification)
    }

    private fun triggerWidgetUpdate() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                WattmeterWidget().updateAll(applicationContext)
            } catch (e: Exception) {
            }
        }
    }

    private fun buildMonitoringNotification(snapshot: BatterySnapshot, settings: UserSettings): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ChargingMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val primaryMetric = when (settings.notificationMetric) {
            NotificationMetric.WATT -> String.format(Locale.US, "%.1f W", snapshot.smoothedWatt)
            NotificationMetric.AMPERE -> String.format(Locale.US, "%.0f mA", snapshot.currentNowMa)
            NotificationMetric.VOLT -> String.format(Locale.US, "%.2f V", snapshot.voltageVolt)
            NotificationMetric.TEMP -> String.format(Locale.US, "%.1f °C", snapshot.temperatureCelsius)
            NotificationMetric.PERCENT -> "${snapshot.levelPercent}%"
        }

        val chargingTag = if (snapshot.isCharging) {
            if (snapshot.plugType.isNotEmpty() && snapshot.plugType != "Battery") {
                "⚡ Charging (${snapshot.plugType})"
            } else {
                "⚡ Charging"
            }
        } else {
            "Discharging"
        }

        val contentTitle = "$primaryMetric • ${snapshot.levelPercent}% • $chargingTag"
        val contentText = String.format(
            Locale.US,
            "⚡ %.1f W • %.0f mA • %.2f V • %.1f°C",
            snapshot.smoothedWatt,
            snapshot.currentNowMa,
            snapshot.voltageVolt,
            snapshot.temperatureCelsius
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_MONITORING)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun stopMonitoring() {
        isRunning = false
        unplugJob?.cancel()
        unplugJob = null
        notificationManager.cancel(NOTIFICATION_ID_MONITORING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.antigravity.wattmeter.ACTION_START"
        const val ACTION_STOP = "com.antigravity.wattmeter.ACTION_STOP"
        const val ACTION_DISMISS_ALARM = "com.antigravity.wattmeter.ACTION_DISMISS_ALARM"

        const val CHANNEL_ID_MONITORING = "wattmeter_monitoring"
        const val NOTIFICATION_ID_MONITORING = 1001
        private const val UNPLUG_GRACE_MS = 3000L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var latestSnapshot: BatterySnapshot? = null
            private set
    }
}
