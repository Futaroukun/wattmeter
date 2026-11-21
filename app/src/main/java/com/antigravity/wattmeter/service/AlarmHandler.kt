package com.antigravity.wattmeter.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.antigravity.wattmeter.MainActivity
import com.antigravity.wattmeter.R
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.UserSettings
import java.util.Locale

class AlarmHandler(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var mediaPlayer: MediaPlayer? = null

    private var wasFullAlarmTriggered = false
    private var wasLevelAlarmTriggered = false
    private var wasTempAlarmTriggered = false
    private var wasCharging = false
    private var lastAlarmTimestamp = 0L

    init {
        createAlarmChannel()
    }

    private fun createAlarmChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALARMS,
                "Wattmeter Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority battery alerts"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun checkThresholds(snapshot: BatterySnapshot, settings: UserSettings) {
        val isCharging = snapshot.isCharging
        val level = snapshot.levelPercent
        val temp = snapshot.temperatureCelsius

        if (settings.alarmFullBattery) {
            val isFull = (level >= 100) || (!isCharging && wasCharging && level >= 99)
            if (isFull && !wasFullAlarmTriggered) {
                wasFullAlarmTriggered = true
                triggerAlarm(
                    ALARM_ID_FULL,
                    "Battery Full",
                    "Battery has reached 100% capacity."
                )
            } else if (level < 95) {
                wasFullAlarmTriggered = false
            }
        }

        if (settings.alarmLevelThreshold in 1..99) {
            val targetReached = isCharging && level >= settings.alarmLevelThreshold
            if (targetReached && !wasLevelAlarmTriggered) {
                wasLevelAlarmTriggered = true
                triggerAlarm(
                    ALARM_ID_LEVEL,
                    "Target Level Reached",
                    String.format(Locale.US, "Battery charged to %d%% (%.1f W).", level, snapshot.smoothedWatt)
                )
            } else if (level < settings.alarmLevelThreshold - 2) {
                wasLevelAlarmTriggered = false
            }
        }

        if (settings.alarmTempThreshold > 0.0f) {
            val isOverheat = temp >= settings.alarmTempThreshold
            if (isOverheat && !wasTempAlarmTriggered) {
                wasTempAlarmTriggered = true
                triggerAlarm(
                    ALARM_ID_TEMP,
                    "Battery Overheat Warning",
                    String.format(Locale.US, "Battery temperature reached %.1f°C! Please unplug.", temp)
                )
            } else if (temp < settings.alarmTempThreshold - 1.5f) {
                wasTempAlarmTriggered = false
            }
        }

        wasCharging = isCharging
    }

    private fun triggerAlarm(notificationId: Int, title: String, text: String) {
        val now = System.currentTimeMillis()
        if (now - lastAlarmTimestamp < COOLDOWN_MS) {
            return
        }
        lastAlarmTimestamp = now

        playSound()
        vibrate()
        showNotification(notificationId, title, text)
    }

    private fun playSound() {
        try {
            stopSound()
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    private fun showNotification(notificationId: Int, title: String, text: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, ChargingMonitorService::class.java).apply {
            action = ChargingMonitorService.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            context,
            notificationId + 10,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALARMS)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun stopAlarm() {
        stopSound()
        notificationManager.cancel(ALARM_ID_FULL)
        notificationManager.cancel(ALARM_ID_LEVEL)
        notificationManager.cancel(ALARM_ID_TEMP)
    }

    fun release() {
        stopSound()
    }

    private fun stopSound() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }
    }

    companion object {
        const val CHANNEL_ID_ALARMS = "wattmeter_alarms"
        private const val ALARM_ID_FULL = 2001
        private const val ALARM_ID_LEVEL = 2002
        private const val ALARM_ID_TEMP = 2003
        private const val COOLDOWN_MS = 30000L
    }
}
