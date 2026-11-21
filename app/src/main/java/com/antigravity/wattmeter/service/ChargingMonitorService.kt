package com.antigravity.wattmeter.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import com.antigravity.wattmeter.MainActivity
import com.antigravity.wattmeter.R
import com.antigravity.wattmeter.WattmeterApp
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.NotificationMetric
import com.antigravity.wattmeter.data.model.UserSettings
import com.antigravity.wattmeter.widget.WattmeterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class ChargingMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dataCollectionJob: Job? = null
    private var unplugJob: Job? = null

    private lateinit var notificationManager: NotificationManager
    private lateinit var alarmHandler: AlarmHandler

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        alarmHandler = AlarmHandler(this)
        isRunning = true

        val initialNotification = buildInitialNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_MONITORING,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_MONITORING, initialNotification)
        }

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

        if (!isRunning) {
            isRunning = true
            startObservingData()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        alarmHandler.release()
        serviceScope.cancel()
    }

    private fun startObservingData() {
        dataCollectionJob?.cancel()
        val app = application as WattmeterApp
        val repo = app.batteryRepository
        val pref = app.preferencesManager

        dataCollectionJob = serviceScope.launch {
            combine(repo.snapshotFlow, pref.userSettingsFlow) { snapshot, settings ->
                Pair(snapshot, settings)
            }.collect { (snapshot, settings) ->
                handleSnapshotUpdate(snapshot, settings)
            }
        }
    }

    private fun handleSnapshotUpdate(snapshot: BatterySnapshot, settings: UserSettings) {
        if (settings.autoStopOnUnplug) {
            if (!snapshot.isCharging) {
                if (unplugJob == null) {
                    unplugJob = serviceScope.launch {
                        delay(UNPLUG_GRACE_MS)
                        val latest = (application as WattmeterApp).batteryRepository.snapshotFlow.first()
                        if (!latest.isCharging) {
                            stopMonitoring()
                        }
                        unplugJob = null
                    }
                }
            } else {
                unplugJob?.cancel()
                unplugJob = null
            }
        }

        if (!isRunning) return

        updateNotification(snapshot, settings)
        alarmHandler.checkAndTriggerAlarms(snapshot, settings)
        triggerWidgetUpdate()
    }

    private fun buildInitialNotification(): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID_MONITORING)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Wattmeter Active")
            .setContentText("Monitoring charging telemetry...")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(snapshot: BatterySnapshot, settings: UserSettings) {
        val notification = buildMonitoringNotification(snapshot, settings)
        notificationManager.notify(NOTIFICATION_ID_MONITORING, notification)
    }

    private fun triggerWidgetUpdate() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                WattmeterWidget().updateAll(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Widget update failed", e)
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
        val estimateTag = if (snapshot.currentEstimated || snapshot.voltageEstimated) "~" else ""
        val contentText = String.format(
            Locale.US,
            "⚡ ${estimateTag}%.1f W • %.0f mA • %.2f V • %.1f°C",
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
        private const val TAG = "ChargingMonitorService"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
