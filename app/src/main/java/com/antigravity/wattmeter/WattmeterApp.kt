package com.antigravity.wattmeter

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.antigravity.wattmeter.data.local.AppDatabase
import com.antigravity.wattmeter.data.preferences.PreferencesManager
import com.antigravity.wattmeter.data.repository.BatteryReader
import com.antigravity.wattmeter.data.repository.BatteryRepository
import com.antigravity.wattmeter.receiver.PowerConnectionReceiver

class WattmeterApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    val batteryReader: BatteryReader by lazy { BatteryReader(this) }
    val batteryRepository: BatteryRepository by lazy {
        BatteryRepository.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        batteryRepository
        createNotificationChannels()
        registerPowerReceiver()
    }

    private fun registerPowerReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            PowerConnectionReceiver(),
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING_ID,
                "Charging Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing battery and charging metrics"
                setShowBadge(false)
            }
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARMS_ID,
                "Battery Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for battery full, level threshold, and overheat"
                enableVibration(true)
            }
            notificationManager?.createNotificationChannel(monitoringChannel)
            notificationManager?.createNotificationChannel(alarmChannel)
        }
    }

    companion object {
        const val CHANNEL_MONITORING_ID = "wattmeter_monitoring"
        const val CHANNEL_ALARMS_ID = "wattmeter_alarms"

        lateinit var instance: WattmeterApp
            private set
    }
}
