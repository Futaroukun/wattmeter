package com.antigravity.wattmeter.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antigravity.wattmeter.MainActivity
import com.antigravity.wattmeter.R
import com.antigravity.wattmeter.data.model.BatterySnapshot
import com.antigravity.wattmeter.data.model.UserSettings
import java.util.Locale

private const val TAG = "AlarmHandler"

class AlarmHandler(private val context: Context) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var mediaPlayer: MediaPlayer? = null
    private var lastFullAlertTime = 0L
    private var lastLevelAlertTime = 0L
    private var lastTempAlertTime = 0L

    fun checkAndTriggerAlarms(snapshot: BatterySnapshot, settings: UserSettings) {
        val now = System.currentTimeMillis()

        if (settings.alarmBatteryFull && snapshot.levelPercent >= 100 && snapshot.isCharging) {
            if (now - lastFullAlertTime > COOLDOWN_MS) {
                lastFullAlertTime = now
                triggerAlarm(
                    ALARM_ID_FULL,
                    "Battery Full (100%)",
                    "Battery is fully charged. Unplug the charger to preserve battery longevity.",
                    settings.alarmSoundEnabled,
                    settings.alarmVibrationEnabled
                )
            }
        }

        if (settings.alarmBatteryLevel && snapshot.levelPercent >= settings.alarmBatteryLevelThreshold && snapshot.isCharging) {
            if (now - lastLevelAlertTime > COOLDOWN_MS) {
                lastLevelAlertTime = now
                triggerAlarm(
                    ALARM_ID_LEVEL,
                    "Battery Threshold Reached (${snapshot.levelPercent}%)",
                    "Battery has reached target limit of ${settings.alarmBatteryLevelThreshold}%.",
                    settings.alarmSoundEnabled,
                    settings.alarmVibrationEnabled
                )
            }
        }

        if (settings.alarmOverheat && snapshot.temperatureCelsius >= settings.alarmOverheatThreshold) {
            if (now - lastTempAlertTime > COOLDOWN_MS) {
                lastTempAlertTime = now
                triggerAlarm(
                    ALARM_ID_TEMP,
                    "High Battery Temperature (${String.format(Locale.US, "%.1f", snapshot.temperatureCelsius)}°C)",
                    "Battery temperature is high. Disconnect charger or reduce device workload.",
                    settings.alarmSoundEnabled,
                    settings.alarmVibrationEnabled
                )
            }
        }
    }

    private fun triggerAlarm(
        id: Int,
        title: String,
        text: String,
        sound: Boolean,
        vibrate: Boolean
    ) {
        if (sound) {
            playSound()
        }
        if (vibrate) {
            vibrate()
        }
        showNotification(id, title, text)
    }

    private fun playSound() {
        try {
            stopSound()
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Alarm operation failed", e)
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

            val pattern = longArrayOf(0, 400, 200, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Alarm operation failed", e)
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
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
        } catch (e: Exception) {
            Log.w(TAG, "Alarm operation failed", e)
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
