package com.antigravity.wattmeter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.antigravity.wattmeter.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

open class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = PreferencesManager(context).userSettingsFlow.first()
                if (settings.autoStartOnPlug) {
                    if (action == Intent.ACTION_POWER_CONNECTED) {
                        val serviceIntent = Intent(context, ChargingMonitorService::class.java).apply {
                            action = ChargingMonitorService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
                        val serviceIntent = Intent(context, ChargingMonitorService::class.java).apply {
                            action = ChargingMonitorService.ACTION_STOP
                        }
                        context.startService(serviceIntent)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
