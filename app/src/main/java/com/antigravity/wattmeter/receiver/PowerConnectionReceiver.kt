package com.antigravity.wattmeter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.wattmeter.data.preferences.PreferencesManager
import com.antigravity.wattmeter.service.MonitorServiceLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val incomingAction = intent.action ?: return
        if (incomingAction != Intent.ACTION_POWER_CONNECTED && incomingAction != Intent.ACTION_POWER_DISCONNECTED) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = PreferencesManager(appContext).userSettingsFlow.first()
                if (settings.autoStartOnPlug) {
                    if (incomingAction == Intent.ACTION_POWER_CONNECTED) {
                        MonitorServiceLauncher.start(appContext)
                    } else {
                        MonitorServiceLauncher.stop(appContext)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Plug auto-start handling failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PowerConnectionReceiver"
    }
}
