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

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = PreferencesManager(appContext).userSettingsFlow.first()
                if (settings.autoStartOnBoot) {
                    MonitorServiceLauncher.start(appContext)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Boot auto-start failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
